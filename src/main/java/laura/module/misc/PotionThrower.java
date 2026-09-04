package laura.module.misc;

import laura.autobuy.AutoBuyEntry;
import laura.core.Category;
import laura.core.Laura;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.setting.BindSetting;
import laura.setting.ModeSetting;
import laura.ui.screen.AssistantScreen;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

@ModuleRegister(name = "Potion Thrower", description = "Быстрое метание бафов через колесо или по клавише", category = Category.Misc)
public class PotionThrower extends Module {
    private final ModeSetting b = new ModeSetting("Способ использования зелий", "Колесо выбора", "Колесо выбора", "Клавиша");
    private final AssistantScreen c = new AssistantScreen(Text.literal("Potion Thrower"));

    public PotionThrower() {
        BindSetting d = new BindSetting("Открыть меню зелий", 86, 0).a(() -> {
            mc.setScreen(this.c);
        }).b(() -> {
            if (mc.currentScreen == this.c) {
                this.c.b(this.c.b());
                if (mc.currentScreen == this.c) {
                    mc.setScreen(null);
                }
            }
        }).a(() -> {
            return Boolean.valueOf(this.b.l("Колесо выбора"));
        });
        a(this.b, d);
        for (AutoBuyEntry potion : AutoBuyEntry.values()) {
            if (potion.getItem() == Items.SPLASH_POTION) {
                a(new BindSetting(potion.getDisplayName(), -1).a(() -> {
                    Laura.getInstance().getModuleProcessor().v().getUseableHandler().a(potion.a());
                }).a(() -> {
                    return Boolean.valueOf(this.b.l("Клавиша"));
                }));
            }
        }
    }

    public AssistantScreen q() {
        return this.c;
    }
}
