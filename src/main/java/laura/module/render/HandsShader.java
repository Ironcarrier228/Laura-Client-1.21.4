package laura.module.render;

import laura.config.ThemeInfo;
import laura.core.*;
import laura.core.Module;
import laura.event.HandEvent;
import laura.render.ColorUtil;
import laura.setting.SliderSetting;
import laura.ui.shader.NoiseShader;
import net.minecraft.client.option.Perspective;

@ModuleRegister(name = "Hands Shader", description = "Накладывает шейдер на руку от первого лица", category = Category.Render)
public class HandsShader extends Module {
    private final SliderSetting b = new SliderSetting("Непрозрачность", 0.6f, 0.0f, 1.0f, 0.05f);

    public HandsShader() {
        a(this.b);
    }

    @EventTarget
    public void a(HandEvent event) {
        NoiseShader shader = Laura.getInstance().getModuleProcessor().i().f();
        if (mc.options.getPerspective() == Perspective.FIRST_PERSON) {
            if (event.isPreEvent()) {
                shader.e();
            }
            if (event.isPostEvent()) {
                float[] color = ColorUtil.a(Laura.getInstance().getModuleProcessor().o().a(ThemeInfo.PRIMARY).toIntColor());
                color[3] = this.b.c().floatValue();
                shader.a(color);
            }
        }
    }
}
