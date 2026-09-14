package laura.module.movement;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.TickEvent;
import laura.util.MathUtil;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * Портировано из Wexside (модуль NoWeb).
 * Убирает замедление в паутине: задаёт собственную скорость движения,
 * позволяет выпрыгивать вверх (прыжок) и быстро опускаться (шифт).
 */
@ModuleRegister(name = "NoWeb", description = "Убирает замедление в паутине", category = Category.Movement)
public class NoWeb extends Module {

    @EventTarget
    public void a(TickEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (!isInWeb()) {
            return;
        }
        double speed = MathUtil.a(0.62f, 0.64f);
        double[] direction = direction(speed);
        double motionY = mc.options.jumpKey.isPressed() ? 1.2d : (mc.options.sneakKey.isPressed() ? -2.0d : 0.0d);
        mc.player.setVelocity(direction[0], motionY, direction[1]);
        mc.player.velocityModified = true;
    }

    private double[] direction(double speed) {
        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;
        float yaw = mc.player.getYaw();
        if (forward != 0.0f) {
            if (strafe > 0.0f) {
                yaw += forward > 0.0f ? -45.0f : 45.0f;
            } else if (strafe < 0.0f) {
                yaw += forward > 0.0f ? 45.0f : -45.0f;
            }
            strafe = 0.0f;
            forward = forward > 0.0f ? 1.0f : -1.0f;
        }
        double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        return new double[]{(forward * speed * cos) + (strafe * speed * sin), (forward * speed * sin) - (strafe * speed * cos)};
    }

    private boolean isInWeb() {
        Box box = mc.player.getBoundingBox();
        for (BlockPos pos : blocksAround()) {
            if (mc.world.getBlockState(pos).isOf(Blocks.COBWEB) && box.intersects(new Box(pos))) {
                return true;
            }
        }
        return false;
    }

    private List<BlockPos> blocksAround() {
        BlockPos center = mc.player.getBlockPos();
        ArrayList<BlockPos> positions = new ArrayList<>();
        for (int x = center.getX() - 2; x <= center.getX() + 2; x++) {
            for (int y = center.getY() - 1; y <= center.getY() + 4; y++) {
                for (int z = center.getZ() - 2; z <= center.getZ() + 2; z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }
}
