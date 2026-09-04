package laura.module.render;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.DrawEvent;
import laura.render.ColorUtil;
import laura.render.Draw2DProcessor;
import laura.setting.BooleanSetting;
import laura.setting.ColorSetting;
import laura.setting.MultiModeSetting;
import laura.setting.SliderSetting;
import laura.util.MathUtil;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.util.math.MatrixStack;

@ModuleRegister(name = "Chromatic Vignette", description = "Затемнение и цветовая аберрация по краям экрана, усиливающиеся при низком HP и под водой", category = Category.Render)
public class ChromaticVignette extends Module {
    private final SliderSetting b = new SliderSetting("Ширина рамки", 0.34f, 0.05f, 0.8f, 0.01f);
    private final SliderSetting c = new SliderSetting("Базовая интенсивность", 0.16f, 0.0f, 0.6f, 0.01f);
    private final SliderSetting d = new SliderSetting("Сила аберрации", 0.6f, 0.0f, 2.0f, 0.05f);
    private final SliderSetting e = new SliderSetting("Порог HP", 60.0f, 5.0f, 100.0f, 1.0f);
    private final SliderSetting f = new SliderSetting("Сглаживание", 6.0f, 1.0f, 20.0f, 0.5f);
    private final ColorSetting g = new ColorSetting("Цвет затемнения", Integer.valueOf(ColorUtil.convertToARGB(0, 0, 0, 255)));
    private final ColorSetting h = new ColorSetting("Цвет под водой", Integer.valueOf(ColorUtil.convertToARGB(22, 88, 152, 255)));
    private final MultiModeSetting i = new MultiModeSetting("Усиление",
            new BooleanSetting("При низком HP", true),
            new BooleanSetting("Под водой", true),
            new BooleanSetting("Пульсация", true),
            new BooleanSetting("Аберрация", true));
    private float j;
    private float k;

    public ChromaticVignette() {
        a(this.b, this.c, this.d, this.e, this.f, this.g, this.h, this.i);
    }

    public SliderSetting q() {
        return this.b;
    }

    public SliderSetting r() {
        return this.c;
    }

    public SliderSetting s() {
        return this.d;
    }

    public SliderSetting t() {
        return this.e;
    }

    public SliderSetting u() {
        return this.f;
    }

    public ColorSetting v() {
        return this.g;
    }

    public ColorSetting w() {
        return this.h;
    }

    public MultiModeSetting x() {
        return this.i;
    }

    @Override
    public void b() {
        super.b();
        this.j = 0.0f;
        this.k = 0.0f;
    }

    @Override
    public void c() {
        super.c();
        this.j = 0.0f;
        this.k = 0.0f;
    }

    @EventTarget
    public void a(DrawEvent event) {
        if (!event.b() || mc.player == null || mc.world == null || mc.options.hudHidden) {
            return;
        }
        a(System.currentTimeMillis());
        float intensity = MathUtil.b(this.j, 0.0f, 1.0f);
        if (intensity <= 0.005f) {
            return;
        }
        float width = (float) mc.getWindow().getScaledWidth();
        float height = (float) mc.getWindow().getScaledHeight();
        if (width < 4.0f || height < 4.0f) {
            return;
        }
        float band = MathUtil.b(Math.min(width, height) * this.b.c().floatValue(), 2.0f, Math.min(width, height) * 0.5f);
        Draw2DProcessor draw = event.getDraw2DProcessor();
        MatrixStack matrices = event.i().getMatrices();
        int base = ColorUtil.lerpColor(this.g.c().intValue(), this.h.c().intValue(), MathUtil.b(this.k, 0.0f, 1.0f));
        a(matrices, draw, width, height, band, base, intensity, 0.0f);
        if (this.i.a("Аберрация").c().booleanValue() && this.d.c().floatValue() > 0.0f) {
            b(matrices, draw, width, height, band, intensity);
        }
    }

    private void a(long now) {
        float target = this.c.c().floatValue();
        float maxHealth = Math.max(1.0f, mc.player.getMaxHealth());
        float percent = MathUtil.b(mc.player.getHealth() / maxHealth, 0.0f, 1.0f);
        float threshold = MathUtil.b(this.e.c().floatValue() / 100.0f, 0.05f, 1.0f);
        if (this.i.a("При низком HP").c().booleanValue() && percent < threshold) {
            target += (1.0f - (percent / threshold)) * 0.55f;
        }
        float water = 0.0f;
        if (this.i.a("Под водой").c().booleanValue()
                && mc.getEntityRenderDispatcher().camera.getSubmersionType() == CameraSubmersionType.WATER) {
            target += 0.3f;
            water = 1.0f;
        }
        if (this.i.a("Пульсация").c().booleanValue() && percent < threshold * 0.55f) {
            target += 0.09f * (float) (0.5d + (0.5d * Math.sin(((double) now) / 240.0d)));
        }
        float speed = this.f.c().floatValue();
        this.j = MathUtil.c(this.j, MathUtil.b(target, 0.0f, 0.95f), speed);
        this.k = MathUtil.c(this.k, water, speed);
    }

    private void b(MatrixStack matrices, Draw2DProcessor draw, float width, float height, float band, float intensity) {
        float strength = this.d.c().floatValue();
        float fringe = MathUtil.b(band * 0.3f * strength, 1.0f, Math.min(width, height) * 0.25f);
        float alpha = MathUtil.b(intensity * 0.4f * strength, 0.0f, 0.45f);
        if (alpha <= 0.01f) {
            return;
        }
        a(matrices, draw, width, height, fringe, ColorUtil.convertToARGB(255, 44, 44, 255), alpha, 0.0f);
        a(matrices, draw, width, height, fringe, ColorUtil.convertToARGB(44, 92, 255, 255), alpha, 1.2f * strength);
    }

    private void a(MatrixStack matrices, Draw2DProcessor draw, float width, float height, float band, int color,
                   float alpha, float inset) {
        if (alpha <= 0.004f) {
            return;
        }
        float innerWidth = width - (inset * 2.0f);
        float innerHeight = height - (inset * 2.0f);
        if (innerWidth <= 4.0f || innerHeight <= 4.0f) {
            return;
        }
        float size = Math.min(band, Math.min(innerWidth, innerHeight) * 0.5f);
        if (size < 0.5f) {
            return;
        }
        int outer = ColorUtil.applyAlphaToColor(color, MathUtil.b(alpha, 0.0f, 1.0f));
        int fade = ColorUtil.applyAlphaToColor(color, 0.0f);
        draw.a(matrices, inset, inset, innerWidth, size, 0.0f, outer, outer, fade, fade);
        draw.a(matrices, inset, (inset + innerHeight) - size, innerWidth, size, 0.0f, fade, fade, outer, outer);
        draw.a(matrices, inset, inset, size, innerHeight, 0.0f, outer, fade, outer, fade);
        draw.a(matrices, (inset + innerWidth) - size, inset, size, innerHeight, 0.0f, fade, outer, fade, outer);
    }
}