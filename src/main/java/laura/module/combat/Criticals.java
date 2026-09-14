package laura.module.combat;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.AttackEvent;
import laura.setting.BooleanSetting;
import laura.setting.MultiModeSetting;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Портировано из Wexside (модуль Criticals).
 * Выдаёт критический удар в условиях, где ванильная игра его не засчитывает:
 * в паутине или под эффектом плавного падения.
 */
@ModuleRegister(name = "Criticals", description = "Критический удар в паутине или под плавным падением", category = Category.Combat)
public class Criticals extends Module {
    private final MultiModeSetting conditions = new MultiModeSetting("Условия",
            new BooleanSetting("Паутина", true),
            new BooleanSetting("Плавное падение", true));

    public Criticals() {
        a(this.conditions);
    }

    @EventTarget
    public void a(AttackEvent event) {
        if (mc.player == null || mc.world == null || mc.player.networkHandler == null) {
            return;
        }
        if (mc.player.isGliding()) {
            return;
        }
        Entity target = event.b();
        if (target == null || target == mc.player || target instanceof EndCrystalEntity) {
            return;
        }
        BooleanSetting web = this.conditions.a("Паутина");
        BooleanSetting slowFalling = this.conditions.a("Плавное падение");
        boolean inWeb = web != null && web.c().booleanValue() && isInWeb();
        boolean hasSlowFalling = slowFalling != null && slowFalling.c().booleanValue() && mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING);
        if (!inWeb && !hasSlowFalling) {
            return;
        }
        float epsilon = MathHelper.lerp(ThreadLocalRandom.current().nextFloat(), 1.0E-7f, 1.0E-6f);
        mc.player.fallDistance = epsilon;
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                mc.player.getX(),
                mc.player.getY() - epsilon,
                mc.player.getZ(),
                mc.player.getYaw(),
                mc.player.getPitch(),
                false,
                mc.player.horizontalCollision));
    }

    private boolean isInWeb() {
        Box box = mc.player.getBoundingBox().contract(1.0E-7d);
        int minX = MathHelper.floor(box.minX);
        int maxX = MathHelper.floor(box.maxX);
        int minY = MathHelper.floor(box.minY);
        int maxY = MathHelper.floor(box.maxY);
        int minZ = MathHelper.floor(box.minZ);
        int maxZ = MathHelper.floor(box.maxZ);
        BlockPos.Mutable position = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    position.set(x, y, z);
                    if (mc.world.getBlockState(position).isOf(Blocks.COBWEB)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
