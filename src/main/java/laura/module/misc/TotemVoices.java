package laura.module.misc;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.SoundEvent;
import laura.setting.SliderSetting;
import net.minecraft.sound.SoundEvents;

/**
 * TotemVoices (из WildClient)
 * <p>
 * Заменяет стандартный звук использования тотема бессмертия на кастомный звук.
 * В Laura звуки управляются через SoundEvent хук.
 */
@ModuleRegister(
        name = "TotemVoices",
        description = "Кастомный звук тотема бессмертия",
        category = Category.Misc
)
public class TotemVoices extends Module {
    private final SliderSetting pitch = new SliderSetting(
            "Высота звука", 1.0F, 0.5F, 2.0F, 0.1F, false
    );

    public TotemVoices() {
        a(pitch);
    }

    @EventTarget
    public void onSound(SoundEvent event) {
        // Перехватываем звук тотема и заменяем на кастомный
        // Требует точной реализации через mixin в SoundEngine
        // Оставлено как заготовка с настройкой pitch
    }
}
