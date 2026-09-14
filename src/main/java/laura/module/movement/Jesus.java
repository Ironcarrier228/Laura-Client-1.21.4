package laura.module.movement;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.TickEvent;
import laura.setting.ModeSetting;
import laura.setting.SliderSetting;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Портировано из Wexside (модуль Jesus).
 * Позволяет ходить по воде и лаве: удерживает игрока на поверхности
 * и задаёт скорость в зависимости от эффектов.
 */
@ModuleRegister(name = "Jesus", description = "Позволяет ходить по воде", category = Category.Movement)
public class Jesus extends Module {
    private final ModeSetting mode = new ModeSetting("Режим", "Авто", "Авто", "Простой");
    private final SliderSetting speed = new SliderSetting("Скорость", 0.2967f, 0.2f, 1.05f, 0.01f)
            .a(() -> this.mode.l("Простой"));

    public Jesus() {
        a(this.mode, this.speed);
    }

    @EventTarget
    public void a(TickEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (!mc.player.isTouchingWater() && !mc.player.isInLava()) {
            return;
        }
        if (mc.player.isGliding() || mc.player.hasVehicle()) {
            return;
        }
        float speedValue = this.mode.l("Авто") ? autoSpeed() : this.speed.c().floatValue();
        setSpeed(speedValue);
        boolean moving = mc.options.forwardKey.isPressed()
                || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed()
                || mc.options.rightKey.isPressed();
        if (!moving) {
            mc.player.setVelocity(0.0d, mc.player.getVelocity().y, 0.0d);
        }
        double motionY = mc.options.jumpKey.isPressed() ? 0.019d : 0.003d;
        mc.player.setVelocity(mc.player.getVelocity().x, motionY, mc.player.getVelocity().z);
    }

    private float autoSpeed() {
        float result;
        StatusEffectInstance speedEffect = mc.player.getStatusEffect(StatusEffects.SPEED);
        if (speedEffect != null) {
            if (speedEffect.getAmplifier() == 2) {
                result = 0.53535f;
            } else if (speedEffect.getAmplifier() == 1) {
                result = 0.43f;
            } else {
                result = 0.3243f;
            }
        } else {
            result = 0.2967f;
        }
        if (mc.player.hasStatusEffect(StatusEffects.SLOWNESS)) {
            result *= 0.85f;
        }
        return result;
    }

    private void setSpeed(double speed) {
        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;
        if (forward == 0.0f && strafe == 0.0f) {
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
    }
}
