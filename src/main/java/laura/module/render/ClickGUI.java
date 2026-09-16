package laura.module.render;

import laura.core.Category;
import laura.core.Laura;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.ui.screen.ModernClickGuiScreen;
import net.minecraft.client.MinecraftClient;

@ModuleRegister(
    name = "ClickGUI",
    description = "Современное меню управления модулями",
    category = Category.Render
)
public class ClickGUI extends Module {
    
    public ClickGUI() {
        // По умолчанию привязываем к правому Shift
        this.keyCode = 344; // GLFW_KEY_RIGHT_SHIFT
    }

    @Override
    public void b() {
        // При включении модуля открываем экран
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            mc.execute(() -> mc.setScreen(new ModernClickGuiScreen()));
        }
        // Сразу выключаем модуль (это просто триггер для открытия GUI)
        a(false);
    }

    @Override
    public void c() {
        // При выключении ничего не делаем
    }
}
