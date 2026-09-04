package laura.module.misc;

import laura.core.Category;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.setting.BooleanSetting;

@ModuleRegister(name = "No Interact", description = "Блокирует случайное взаимодействие с контейнерами и блоками", category = Category.Misc)
public class NoInteract extends Module {
    private final BooleanSetting b = new BooleanSetting("Учитывать включённую Aura", true);

    public NoInteract() {
        a(this.b);
    }

    public BooleanSetting q() {
        return this.b;
    }
}
