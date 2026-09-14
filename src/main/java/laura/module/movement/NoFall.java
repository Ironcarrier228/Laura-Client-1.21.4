package laura.module.movement;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.InputEvent;
import laura.event.PacketEvent;
import laura.event.TickEvent;
import laura.setting.ModeSetting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;

/**
 * Портировано из Wexside (модуль NoFall).
 * Режим «Элитры» кратковременно открывает элитры при падении,
 * режим «Пакет» подменяет флаг земли в пакете движения.
 */
@ModuleRegister(name = "NoFall", description = "Предотвращает урон от падения", category = Category.Movement)
public class NoFall extends Module {
    private final ModeSetting mode = new ModeSetting("Режим", "Пакет", "Пакет", "Элитры");
    private boolean falling;
    private boolean spoofActive;
    private boolean sentThisTick;
    private int settleTicks;
    private int boostTicks;
    private long lastElytraToggle;
    private long lastPacketSpoof;
    private long lastSettleReset;

    public NoFall() {
        a(this.mode);
    }

    @Override
    public void b() {
        super.b();
        reset();
    }

    @Override
    public void c() {
        super.c();
        reset();
        Timer.speed = 1.0f;
    }

    @EventTarget
    public void a(TickEvent event) {
        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }
        this.falling = mc.player.fallDistance > 3.0f;
        if (this.mode.l("Элитры")) {
            if (this.settleTicks > 0) {
                this.settleTicks--;
            }
            if (this.falling
                    && !mc.player.isOnGround()
                    && willLand()
                    && mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)
                    && System.currentTimeMillis() - this.lastElytraToggle >= 200L) {
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                this.lastElytraToggle = System.currentTimeMillis();
                this.spoofActive = true;
                this.lastSettleReset = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - this.lastSettleReset >= 300L) {
                this.spoofActive = false;
                this.settleTicks = 0;
            }
        } else {
            if (this.boostTicks == 2) {
                Timer.speed = 0.5f;
            } else if (this.boostTicks <= 1) {
                Timer.speed = 1.0f;
            }
            if (this.boostTicks > 0) {
                this.boostTicks--;
            }
            if (System.currentTimeMillis() - this.lastSettleReset >= 300L) {
                this.spoofActive = false;
                this.boostTicks = 0;
            }
        }
    }

    @EventTarget
    public void a(PacketEvent event) {
        if (mc.player == null) {
            return;
        }
        if (event.isReceive()) {
            if (event.getPacket() instanceof GameJoinS2CPacket
                    || event.getPacket() instanceof DisconnectS2CPacket
                    || event.getPacket() instanceof PlayerRespawnS2CPacket) {
                reset();
                return;
            }
            if (event.getPacket() instanceof PlayerPositionLookS2CPacket && this.spoofActive) {
                if (this.mode.l("Элитры")) {
                    this.settleTicks = 2;
                } else {
                    this.boostTicks = 2;
                }
                this.spoofActive = false;
            }
            return;
        }
        if (this.mode.l("Элитры") || this.spoofActive || this.sentThisTick) {
            return;
        }
        if (!(event.getPacket() instanceof PlayerMoveC2SPacket)) {
            return;
        }
        if (this.falling
                && willLand()
                && this.boostTicks == 0
                && System.currentTimeMillis() - this.lastPacketSpoof >= 100L) {
            this.sentThisTick = true;
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, false));
            this.sentThisTick = false;
            event.a(true);
            this.lastPacketSpoof = System.currentTimeMillis();
            this.lastSettleReset = System.currentTimeMillis();
            this.spoofActive = true;
        }
    }

    @EventTarget
    public void a(InputEvent event) {
        if (this.spoofActive || this.settleTicks == 2 || this.boostTicks == 2) {
            event.setForward(0.0f);
            event.setStrafe(0.0f);
            event.setJump(false);
        }
    }

    private boolean willLand() {
        double step = Math.min(-0.05d, mc.player.getVelocity().y);
        return mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(0.0d, step, 0.0d)).iterator().hasNext();
    }

    private void reset() {
        this.falling = false;
        this.spoofActive = false;
        this.sentThisTick = false;
        this.settleTicks = 0;
        this.boostTicks = 0;
        Timer.speed = 1.0f;
    }
}
