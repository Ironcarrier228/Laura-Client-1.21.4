package laura.module.movement;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.SlowEvent;
import laura.setting.ModeSetting;

@ModuleRegister(name = "No Slow Down", description = "Убирает замедление при использовании предметов", category = Category.Movement)
public class NoSlowDown extends Module {
    private final ModeSetting b = new ModeSetting("Режим использования", "Vanilla", "Vanilla");

    public NoSlowDown() {
        a(this.b);
    }

    @EventTarget
    public void a(SlowEvent slow) {
        if (this.b.l("Vanilla")) {
            slow.a(true);
        }
    }
}
