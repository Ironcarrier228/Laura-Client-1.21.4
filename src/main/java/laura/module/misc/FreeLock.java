package laura.module.misc;

import laura.core.Category;
import laura.core.Module;
import laura.core.ModuleRegister;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

/**
 * FreeLock (из WildClient)
 * <p>
 * Фиксирует вид от 3-го лица, не давая F5 переключать на 1-е лицо.
 * Позволяет смотреть назад без возможности сменить перспективу.
 */
@ModuleRegister(
        name = "FreeLock",
        description = "Фиксирует вид от 3-го лица (назад)",
        category = Category.Misc
)
public class FreeLock extends Module {
    private Perspective savedPerspective;

    @Override
    public void b() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            // Запоминаем текущую перспективу и переключаем на 3-е лицо (назад)
            savedPerspective = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
    }

    @Override
    public void c() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null && savedPerspective != null) {
            mc.options.setPerspective(savedPerspective);
            savedPerspective = null;
        }
    }
}
