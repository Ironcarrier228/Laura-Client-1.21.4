package laura.module.movement;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.MotionEvent;
import laura.setting.ModeSetting;
import laura.setting.SliderSetting;
import laura.util.CounterUtil;

@ModuleRegister(name = "Wall Climb", description = "Позволяет взбираться по стенам", category = Category.Movement)
public class WallClimb extends Module {
    private final ModeSetting b = new ModeSetting("Выберите тип обхода", "Матрикс", "Матрикс");
    private final SliderSetting c = new SliderSetting("Скорость режима", 20.0f, 1.0f, 100.0f, 1.0f);
    private final CounterUtil d = new CounterUtil();

    public WallClimb() {
        a(this.b, this.c);
    }

    @Override
    public void b() {
        super.b();
        this.d.b();
    }

    @EventTarget
    public void a(MotionEvent event) {
        if (this.b.l("Матрикс")) {
            a(event, this.c.c().longValue());
        }
    }

    private void a(MotionEvent event, long value) {
        if (this.d.a(value * 5) && mc.player.horizontalCollision) {
            event.setOnGround(true);
            mc.player.setOnGround(true);
            mc.player.verticalCollision = true;
            mc.player.horizontalCollision = true;
            mc.player.jump();
            this.d.b();
        }
    }
}
