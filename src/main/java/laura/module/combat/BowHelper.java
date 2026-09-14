package laura.module.combat;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Laura;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.TickEvent;
import laura.setting.BooleanSetting;
import laura.setting.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Портировано из Wexside (модуль BowHelper).
 * Плавно наводит лук/арбалет на ближайшего к прицелу противника,
 * рассчитывая баллистическую траекторию полёта снаряда.
 */
@ModuleRegister(name = "BowHelper", description = "Плавная наводка лука без тряски", category = Category.Combat)
public class BowHelper extends Module {
    private final SliderSetting range = new SliderSetting("Дистанция", 30.0f, 1.0f, 50.0f, 1.0f);
    private final BooleanSetting ignoreFriends = new BooleanSetting("Игнор друзей", true);

    private LivingEntity target;
    private boolean aiming;
    private float savedYaw;
    private float savedPitch;

    public BowHelper() {
        a(this.range, this.ignoreFriends);
    }

    @Override
    public void c() {
        super.c();
        restoreRotation();
        this.target = null;
    }

    @EventTarget
    public void a(TickEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        boolean holdingBow = mc.player.getMainHandStack().getItem() instanceof BowItem
                || mc.player.getOffHandStack().getItem() instanceof BowItem
                || mc.player.getMainHandStack().getItem() instanceof CrossbowItem
                || mc.player.getOffHandStack().getItem() instanceof CrossbowItem;
        if (!holdingBow) {
            restoreRotation();
            this.target = null;
            return;
        }
        boolean drawingBow = mc.player.isUsingItem() && mc.player.getActiveItem().getItem() instanceof BowItem;
        boolean chargedCrossbow = (mc.player.getMainHandStack().isOf(Items.CROSSBOW) && CrossbowItem.isCharged(mc.player.getMainHandStack()))
                || (mc.player.getOffHandStack().isOf(Items.CROSSBOW) && CrossbowItem.isCharged(mc.player.getOffHandStack()));
        if (this.target != null && !isValid(this.target)) {
            this.target = null;
        }
        if (this.target == null) {
            this.target = findTarget();
        }
        if (this.target == null) {
            return;
        }
        if (!this.aiming) {
            this.savedYaw = mc.player.getYaw();
            this.savedPitch = mc.player.getPitch();
            this.aiming = true;
        }
        if (!drawingBow && !chargedCrossbow) {
            return;
        }
        float power = bowPower();
        Vec3d eye = mc.player.getEyePos();
        Vec3d aim = this.target.getPos().add(0.0d, (this.target.getHeight() * 0.5d) + 0.1d, 0.0d);
        double targetSpeedX = this.target.getX() - this.target.lastX;
        double targetSpeedZ = this.target.getZ() - this.target.lastZ;
        double targetSpeed = Math.sqrt((targetSpeedX * targetSpeedX) + (targetSpeedZ * targetSpeedZ));
        Vec3d predicted = aim;
        float pitch = 0.0f;
        for (int pass = 0; pass < 3; pass++) {
            double horizontal = Math.cos(Math.toRadians(pitch));
            float effectivePower = (float) (power * Math.max(horizontal, 0.1d));
            if (targetSpeed > 0.01d) {
                Vec3d step = aim;
                for (int iteration = 0; iteration < 25; iteration++) {
                    double dx = step.x - eye.x;
                    double dz = step.z - eye.z;
                    double distance = Math.sqrt((dx * dx) + (dz * dz));
                    double flightTicks = flightTime(distance, effectivePower);
                    step = new Vec3d(aim.x + (targetSpeedX * flightTicks), aim.y, aim.z + (targetSpeedZ * flightTicks));
                }
                predicted = step;
            }
            double dx = predicted.x - eye.x;
            double dz = predicted.z - eye.z;
            double horizontalDistance = Math.sqrt((dx * dx) + (dz * dz));
            double heightDifference = predicted.y - eye.y;
            pitch = solvePitch(horizontalDistance, heightDifference, effectivePower);
        }
        double finalDx = predicted.x - eye.x;
        double finalDz = predicted.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-finalDx, finalDz));
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        mc.player.headYaw = yaw;
    }

    private void restoreRotation() {
        if (this.aiming && mc.player != null) {
            mc.player.setYaw(this.savedYaw);
            mc.player.setPitch(this.savedPitch);
            mc.player.headYaw = this.savedYaw;
        }
        this.aiming = false;
    }

    private float bowPower() {
        if (mc.player.getMainHandStack().getItem() instanceof CrossbowItem
                || mc.player.getOffHandStack().getItem() instanceof CrossbowItem) {
            return 3.15f;
        }
        float power = 1.0f;
        if (mc.player.isUsingItem() && mc.player.getActiveItem().getItem() instanceof BowItem) {
            float ticks = mc.player.getItemUseTime() / 20.0f;
            power = MathHelper.clamp(((ticks * ticks) + (ticks * 2.0f)) / 3.0f, 0.0f, 1.0f);
        }
        return power * 3.0f;
    }

    private boolean isValid(LivingEntity entity) {
        if (!(entity instanceof PlayerEntity player)) {
            return false;
        }
        if (!player.isAlive() || player.isInvulnerable()) {
            return false;
        }
        return !(mc.player.distanceTo(player) > this.range.c().floatValue());
    }

    private LivingEntity findTarget() {
        float rangeValue = this.range.c().floatValue();
        PlayerEntity best = null;
        double bestAngle = Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        float cameraYaw = mc.gameRenderer.getCamera().getYaw();
        float cameraPitch = mc.gameRenderer.getCamera().getPitch();
        Vec3d look = Vec3d.fromPolar(cameraPitch, cameraYaw).normalize();
        boolean friends = this.ignoreFriends.c().booleanValue();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)
                    || player == mc.player
                    || !player.isAlive()
                    || player.isInvulnerable()
                    || player.isCreative()
                    || mc.player.distanceTo(player) > rangeValue) {
                continue;
            }
            if (friends && Laura.getInstance().getModuleProcessor().e().d(player.getName().getString())) {
                continue;
            }
            Vec3d toTarget = player.getPos().add(0.0d, player.getHeight() * 0.5d, 0.0d).subtract(eye).normalize();
            double angle = Math.acos(MathHelper.clamp(look.dotProduct(toTarget), -1.0d, 1.0d));
            if (angle < bestAngle) {
                bestAngle = angle;
                best = player;
            }
        }
        return best;
    }

    private float solvePitch(double horizontalDistance, double heightDifference, float power) {
        if (horizontalDistance < 0.5d) {
            return 0.0f;
        }
        float low = -89.0f;
        float high = 89.0f;
        for (int iteration = 0; iteration < 60; iteration++) {
            float middle = (low + high) / 2.0f;
            double simulated = simulateHeight(horizontalDistance, middle, power);
            if (simulated > heightDifference) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return (low + high) / 2.0f;
    }

    private double simulateHeight(double horizontalDistance, float pitch, float power) {
        double radians = Math.toRadians(pitch);
        double velocityX = power * Math.cos(radians);
        double velocityY = -power * Math.sin(radians);
        double travelled = 0.0d;
        double height = 0.0d;
        for (int tick = 0; tick < 500; tick++) {
            double previous = travelled;
            travelled += velocityX;
            height += velocityY;
            velocityX *= 0.99d;
            velocityY *= 0.99d;
            velocityY -= 0.05d;
            if (travelled >= horizontalDistance) {
                double step = travelled - previous > 0.001d ? (horizontalDistance - previous) / (travelled - previous) : 1.0d;
                return (height - velocityY) + ((height - (height - velocityY)) * step);
            }
        }
        return height;
    }

    private double flightTime(double horizontalDistance, float power) {
        double velocity = power;
        double travelled = 0.0d;
        for (int tick = 0; tick < 500; tick++) {
            travelled += velocity;
            velocity *= 0.99d;
            if (travelled >= horizontalDistance) {
                return tick + 1;
            }
        }
        return 500.0d;
    }
}
