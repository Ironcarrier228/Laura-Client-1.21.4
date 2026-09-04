package laura.module.movement;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.PushEvent;
import laura.setting.BooleanSetting;
import laura.setting.MultiModeSetting;

@ModuleRegister(name = "No Push", description = "Отключает отталкивание от выбранных объектов", category = Category.Movement)
public class NoPush extends Module {
    private final MultiModeSetting b = new MultiModeSetting("Отключить коллизию для", new BooleanSetting("Воды и лавы", false), new BooleanSetting("Блоков", false), new BooleanSetting("Энтити", false), new BooleanSetting("Граница", false), new BooleanSetting("Удочки", false));

    public NoPush() {
        a(this.b);
    }

    public MultiModeSetting q() {
        return this.b;
    }

    @EventTarget
    public void a(PushEvent event) {
        switch (event.b()) {
            case FLUIDS:
                event.a(this.b.a("Воды и лавы").c().booleanValue());
                break;
            case BLOCKS:
                event.a(this.b.a("Блоков").c().booleanValue());
                break;
            case ENTITIES:
                event.a(this.b.a("Энтити").c().booleanValue());
                break;
            case WORLD_BORDER:
                event.a(this.b.a("Граница").c().booleanValue());
                break;
            case FISHING_HOOK:
                event.a(this.b.a("Удочки").c().booleanValue());
                break;
        }
    }
}
