package laura.module.render;

import laura.core.Category;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.setting.BooleanSetting;

@ModuleRegister(name = "Item Physic", description = "Добавляет физику предметам, лежащим на земле", category = Category.Render)
public class ItemPhysic extends Module {
    private final BooleanSetting b = new BooleanSetting("Уменьшить размер предметов", false);

    public ItemPhysic() {
        a(this.b);
    }

    public BooleanSetting q() {
        return this.b;
    }
}
