package laura.module.misc;

import laura.core.Category;
import laura.core.ModuleRegister;
import laura.setting.ModeSetting;

/**
 * Модуль-переключатель ClickGUI.
 *
 * Позволяет выбрать какой интерфейс открывается по RShift:
 *  - "Laura"        — нативный ClickGUI Laura (laura/ui/screen/GUIScreen.java)
 *  - "Vanilla"      — стандартный Minecraft pause menu (GameMenuScreen)
 *  - "Wild Classic" — WildClient-style ClickGUI на Laura API (laura/gui/wild/ClickGuiScreen.java)
 *
 * Подробнее о Wild Classic см. laura/gui/wild/README.md
 *
 * Логика выбора реализована в Laura.a(KeyEvent).
 */
@ModuleRegister(
        name = "GUI Selector",
        description = "Выбор ClickGUI: Laura / Vanilla / Wild Classic",
        category = Category.Misc
)
public class GuiSelector extends Module {
    public final ModeSetting style = new ModeSetting(
            "Стиль GUI",
            "Laura",
            "Laura",
            "Vanilla",
            "Wild Classic"
            // "Wild Modern (скоро)"   — добавится в будущем
    );

    public GuiSelector() {
        a(this.style);
    }

    /**
     * Возвращает текущий выбранный стиль GUI.
     * Используется в Laura.java (см. a(KeyEvent)).
     */
    public String getSelectedStyle() {
        return this.style.h();
    }
}
