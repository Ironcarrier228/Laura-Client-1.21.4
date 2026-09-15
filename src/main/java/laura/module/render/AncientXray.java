package laura.module.render;

import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.core.Category;
import laura.event.DrawEvent;
import laura.event.PacketEvent;
import laura.event.TickEvent;
import laura.render.ColorUtil;
import laura.util.ChatUtil;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ModuleRegister(name = "AncientXray", description = "Поиск обломков после взрыва ТНТ", category = Category.Render)
public class AncientXray extends Module {
    private static final int SCAN_RADIUS = 28;
    private static final int[] SCAN_DELAYS = new int[]{4, 10, 20, 40};

    private final Set<BlockPos> found = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> announced = ConcurrentHashMap.newKeySet();
    private final List<ScanTask> scheduled = new ArrayList<>();
    private long lastAnnounce = 0L;

    @Override
    public void b() {
        super.b();
        this.found.clear();
        this.announced.clear();
        this.scheduled.clear();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player != null && mc.world != null) {
            this.refresh();
        }
    }

    public void refresh() {
        if (mc.player == null || mc.world == null) {
            return;
        }

        Iterator<ScanTask> iterator = this.scheduled.iterator();
        while (iterator.hasNext()) {
            ScanTask task = iterator.next();
            task.ticks--;
            if (task.ticks <= 0) {
                this.scanAround(task.pos, SCAN_RADIUS);
                iterator.remove();
            }
        }

        if (System.currentTimeMillis() - this.lastAnnounce > 50L) {
            for (BlockPos pos : this.found) {
                if (!this.announced.contains(pos)) {
                    this.announced.add(pos);
                    this.forceBlockUpdate(pos);
                    this.lastAnnounce = System.currentTimeMillis();
                    break;
                }
            }
        }
    }

    public void clearState() {
        this.found.clear();
        this.announced.clear();
        this.scheduled.clear();
    }

    public void scheduleScan(BlockPos pos, int delayTicks) {
        if (pos != null) {
            this.scheduled.add(new ScanTask(pos.toImmutable(), delayTicks));
        }
    }

    public void onBlockUpdate(BlockPos pos, Block block) {
        this.trackBlock(pos, block);
    }

    public List<BlockPos> getFoundPositions() {
        return new ArrayList<>(this.found);
    }

    public void removePosition(BlockPos pos) {
        this.found.remove(pos);
        this.announced.remove(pos);
    }

    public boolean isFound(BlockPos pos) {
        return this.found.contains(pos);
    }

    private void forceBlockUpdate(BlockPos pos) {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, Direction.UP));
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.world == null || event.isSend()) {
            return;
        }

        if (event.getPacket() instanceof ExplosionS2CPacket explosion) {
            BlockPos center = BlockPos.ofFloored(explosion.center());
            for (int delay : SCAN_DELAYS) {
                this.scheduleScan(center, delay);
            }
        } else if (event.getPacket() instanceof BlockUpdateS2CPacket update) {
            this.trackBlock(update.getPos(), update.getState().getBlock());
        } else if (event.getPacket() instanceof ChunkDeltaUpdateS2CPacket delta) {
            delta.visitUpdates((pos, state) -> this.trackBlock(pos, state.getBlock()));
        }
    }

    private void trackBlock(BlockPos pos, Block block) {
        BlockPos immutable = pos.toImmutable();
        if (block == Blocks.ANCIENT_DEBRIS) {
            if (this.isDebrisCandidate(immutable) && this.found.add(immutable)) {
                ChatUtil.sendMessage("§6[AncientXray] §fОбломок найден §e" + immutable.toShortString());
            }
        } else {
            this.found.remove(immutable);
            this.announced.remove(immutable);
        }
    }

    private void scanAround(BlockPos center, int radius) {
        if (mc.world == null) {
            return;
        }

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    mutable.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (this.isDebrisCandidate(mutable)) {
                        BlockPos immutable = mutable.toImmutable();
                        if (this.found.add(immutable)) {
                            ChatUtil.sendMessage("§fОбнаружен обломок: §e" + immutable.toShortString());
                        }
                    }
                }
            }
        }
    }

    private boolean isDebrisCandidate(BlockPos pos) {
        if (mc.world == null) {
            return false;
        }

        Block block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.ANCIENT_DEBRIS
                && this.hasExposedSide(pos)
                && !this.surroundedByOres(pos)
                && this.hasNearbyAir(pos)
                && !this.isDebrisCluster(pos);
    }

    private boolean hasExposedSide(BlockPos pos) {
        int exposed = 0;
        for (Direction direction : Direction.values()) {
            Block neighbor = mc.world.getBlockState(pos.offset(direction)).getBlock();
            if (neighbor == Blocks.AIR || neighbor == Blocks.LAVA || neighbor == Blocks.CAVE_AIR) {
                if (++exposed >= 2) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean surroundedByOres(BlockPos pos) {
        int ores = 0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = mc.world.getBlockState(pos.add(x, y, z)).getBlock();
                    if (block == Blocks.NETHER_QUARTZ_ORE || block == Blocks.NETHER_GOLD_ORE) {
                        if (++ores >= 4) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean hasNearbyAir(BlockPos pos) {
        int open = 0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = mc.world.getBlockState(pos.add(x, y, z)).getBlock();
                    if (block == Blocks.AIR || block == Blocks.LAVA || block == Blocks.CAVE_AIR) {
                        if (++open >= 4) {
                            return true;
                        }
                    }
                }
            }
        }

        return open >= 4;
    }

    private boolean isDebrisCluster(BlockPos pos) {
        int debris = 0;
        for (int x = -3; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 3; z++) {
                    if (mc.world.getBlockState(pos.add(x, y, z)).getBlock() == Blocks.ANCIENT_DEBRIS) {
                        if (++debris > 6) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    @EventTarget
    public void onDraw(DrawEvent event) {
        if (!event.c() || mc.world == null || mc.player == null || this.found.isEmpty()) {
            return;
        }

        for (BlockPos pos : this.found) {
            if (!mc.world.getBlockState(pos).isOf(Blocks.ANCIENT_DEBRIS)) {
                this.found.remove(pos);
            } else {
                event.getDraw3DProcessor().a(event.h(), new Box(pos), ColorUtil.convertToARGB(255, 255, 170, 0), 1.5f);
            }
        }
    }

    private static final class ScanTask {
        final BlockPos pos;
        int ticks;

        ScanTask(BlockPos pos, int ticks) {
            this.pos = pos;
            this.ticks = ticks;
        }
    }
}
