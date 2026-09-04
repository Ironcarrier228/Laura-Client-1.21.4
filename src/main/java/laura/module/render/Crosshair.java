package laura.module.render;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.CrosshairEvent;
import laura.event.DrawEvent;
import laura.render.ColorUtil;
import laura.render.Draw2DProcessor;
import laura.setting.BooleanSetting;
import laura.setting.MultiModeSetting;
import laura.setting.SliderSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.hit.EntityHitResult;

@ModuleRegister(name = "Crosshair", description = "Отображает настраиваемый прицел на экране", category = Category.Render)
public class Crosshair extends Module {
    private final SliderSetting b = new SliderSetting("Расстояние от центра", 0.0f, 0.0f, 6.0f, 0.5f);
    private final SliderSetting c = new SliderSetting("Длина сегментов", 2.5f, 2.0f, 5.0f, 0.5f);
    private final MultiModeSetting d = new MultiModeSetting("Параметры прицела", new BooleanSetting("Адаптивность", false), new BooleanSetting("Контур", true), new BooleanSetting("Центральная метка", false));

    public Crosshair() {
        a(this.b, this.c, this.d);
    }

    @EventTarget
    public void a(CrosshairEvent e) {
        if (mc.options.getPerspective().isFirstPerson() && !mc.options.hudHidden) {
            e.a(true);
        }
    }

    @EventTarget
    public void a(DrawEvent event) {
        if (event.b() && mc.options.getPerspective().isFirstPerson() && !mc.options.hudHidden) {
            a(event, mc.getWindow().getScaledWidth() / 2.0f, mc.getWindow().getScaledHeight() / 2.0f, 1.0f - mc.player.getAttackCooldownProgress(event.g()));
        }
    }

    private void a(DrawEvent drawEvent, float centerX, float centerY, float cooldown) {
        Draw2DProcessor draw2D = drawEvent.getDraw2DProcessor();
        DrawContext context = drawEvent.i();
        float actualGap = this.d.a("Адаптивность").c().booleanValue() ? this.b.c().floatValue() + (8.0f * cooldown) : this.b.c().floatValue();
        int color = mc.crosshairTarget instanceof EntityHitResult ? ColorUtil.a(255, 64, 64) : -1;
        if (this.d.a("Контур").c().booleanValue()) {
            draw2D.a(context, (centerX + actualGap) - 0.5f, (centerY - 0.5f) - 0.5f, this.c.c().floatValue() + 1.0f, 2.0f, ColorUtil.a(0, 0, 0));
            draw2D.a(context, ((centerX - actualGap) - this.c.c().floatValue()) - 0.5f, (centerY - 0.5f) - 0.5f, this.c.c().floatValue() + 1.0f, 2.0f, ColorUtil.a(0, 0, 0));
            draw2D.a(context, (centerX - 0.5f) - 0.5f, ((centerY - actualGap) - this.c.c().floatValue()) - 0.5f, 2.0f, this.c.c().floatValue() + 1.0f, ColorUtil.a(0, 0, 0));
            draw2D.a(context, (centerX - 0.5f) - 0.5f, (centerY + actualGap) - 0.5f, 2.0f, this.c.c().floatValue() + 1.0f, ColorUtil.a(0, 0, 0));
            draw2D.a(context, centerX + actualGap, centerY - 0.5f, this.c.c().floatValue(), 1.0f, color);
            draw2D.a(context, (centerX - actualGap) - this.c.c().floatValue(), centerY - 0.5f, this.c.c().floatValue(), 1.0f, color);
            draw2D.a(context, centerX - 0.5f, (centerY - actualGap) - this.c.c().floatValue(), 1.0f, this.c.c().floatValue(), color);
            draw2D.a(context, centerX - 0.5f, centerY + actualGap, 1.0f, this.c.c().floatValue(), color);
        } else {
            draw2D.a(context, centerX + actualGap, centerY - 0.5f, this.c.c().floatValue(), 1.0f, color);
            draw2D.a(context, (centerX - actualGap) - this.c.c().floatValue(), centerY - 0.5f, this.c.c().floatValue(), 1.0f, color);
            draw2D.a(context, centerX - 0.5f, (centerY - actualGap) - this.c.c().floatValue(), 1.0f, this.c.c().floatValue(), color);
            draw2D.a(context, centerX - 0.5f, centerY + actualGap, 1.0f, this.c.c().floatValue(), color);
        }
        if (this.d.a("Центральная метка").c().booleanValue() && actualGap > 0.0f) {
            float x = centerX - 0.5f;
            float y = centerY - 0.5f;
            if (this.d.a("Контур").c().booleanValue()) {
                draw2D.a(context, x - 0.5f, y - 0.5f, 2.0f, 2.0f, ColorUtil.a(0, 0, 0));
                draw2D.a(context, x, y, 1.0f, 1.0f, color);
            } else {
                draw2D.a(context, x, y, 1.0f, 1.0f, color);
            }
        }
    }
}
