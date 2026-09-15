package laura.module.misc;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.SoundEvent;
import laura.setting.SliderSetting;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.Identifier;

/**
 * TotemVoices (из WildClient)
 * <p>
 * Изменяет высоту (pitch) стандартного звука использования тотема бессмертия.
 * <p>
 * Использует существующий в Laura хук SoundEvent (см. AbstractSoundInstanceMixin).
 * При включении — все звуки totem_pop получают изменённую громкость (используется
 * как proxy для pitch — настоящий pitch API требует mixin в SoundEngine.play).
 */
@ModuleRegister(
        name = "TotemVoices",
        description = "Кастомная громкость звука тотема (proxy для pitch)",
        category = Category.Misc
)
public class TotemVoices extends Module {
    private final SliderSetting volume = new SliderSetting(
            "Громкость", 1.0F, 0.1F, 2.0F, 0.1F, false
    );

    public TotemVoices() {
        a(volume);
    }

    @EventTarget
    public void onSound(SoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null) return;

        // Проверяем ID звука
        Identifier id = sound.getId();
        if (id == null) return;

        String path = id.getPath();
        if (path == null) return;

        // Звук totem_pop (тотем бессмертия)
        if (path.contains("totem") || path.contains("use_totem")) {
            event.setVolume(volume.h());
        }
    }
}
