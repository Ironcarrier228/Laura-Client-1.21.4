package laura.module.movement;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.TickEvent;
import laura.setting.ModeSetting;
import laura.setting.SliderSetting;
import net.minecraft.util.math.Vec3d;

/**
 * Портировано из Wexside (модуль Speed).
 * «Ванильный» режим задаёт фиксированную скорость движения по направлению ввода,
 * «Буст» — умножает текущую горизонтальную скорость до пикового значения.
 */
@ModuleRegister(name = "Speed", description = "Ускоряет вашего персонажа", category = Category.Movement)
public class Speed extends Module {
    private final ModeSetting mode = new ModeSetting("Режим", "Ванильный", "Ванильный", "Буст");
    private final SliderSetting speed = new SliderSetting("Скорость", 0.42f, 0.2f, 1.5f, 0.01f);
    private final SliderSetting boost = new SliderSetting("Сила ускорения", 1.3f, 1.0f, 2.0f, 0.05f)
            .a(() -> this.mode.l("Буст"));
    private final SliderSetting peak = new SliderSetting("Пиковая скорость (BPS)", 9.0f, 3.0f, 15.0f, 0.5f)
            .a(() -> this.mode.l("Буст"));

    public Speed() {
        a(this.mode, this.speed, this.boost, this.peak);
    }

    @EventTarget
    public void a(TickEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (mc.player.isGliding() || mc.player.isTouchingWater() || mc.player.hasVehicle()) {
            return;
        }
        if (this.mode.l("Ванильный")) {
            setSpeed(this.speed.c().floatValue());
        } else if (this.mode.l("Буст")) {
            accelerate();
        }
    }

    private void accelerate() {
        Vec3d velocity = mc.player.getVelocity();
        double multiplier = this.boost.c().floatValue();
        double maxSpeed = this.peak.c().floatValue() / 20.0d;
        double targetX = velocity.x * multiplier;
        double targetZ = velocity.z * multiplier;
        double resultSpeed = Math.sqrt((targetX * targetX) + (targetZ * targetZ));
        if (resultSpeed > maxSpeed) {
            double scale = maxSpeed / resultSpeed;
            targetX *= scale;
            targetZ *= scale;
        }
        mc.player.addVelocity(targetX - velocity.x, 0.0d, targetZ - velocity.z);
        mc.player.velocityModified = true;
    }

    private void setSpeed(double speed) {
        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;
        if (forward == 0.0f && strafe == 0.0f) {
            mc.player.setVelocity(0.0d, mc.player.getVelocity().y, 0.0d);
            return;
        }
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
        double motionX = (forward * speed * cos) + (strafe * speed * sin);
        double motionZ = (forward * speed * sin) - (strafe * speed * cos);
        mc.player.setVelocity(motionX, mc.player.getVelocity().y, motionZ);
        mc.player.velocityModified = true;
    }
}
