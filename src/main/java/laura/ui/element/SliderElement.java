package laura.ui.element;


import laura.config.ThemeInfo;
import laura.config.ThemeProcessor;
import laura.core.Laura;
import laura.render.ColorUtil;
import laura.render.Draw2DProcessor;
import laura.render.EasingList;
import laura.render.Fonts;
import laura.render.Spring;
import laura.setting.SliderSetting;
import laura.util.MathUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;

/**
 * Slider setting: label + value pill on top, track with animated fill and a
 * spring-scaled knob below.
 */
public class SliderElement extends Element<SliderSetting> {
    private boolean isDragging;
    private final Spring knobSpring = Spring.snappy();

    public SliderElement(SliderSetting setting) {
        super(setting);
        this.a.w = 22.0f;
    }

    @Override

    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        Vector4f vector4f = this.a;
        var setting = this.b;
        if (!MathUtil.a(mouseX, mouseY, vector4f.x, vector4f.y + Fonts.c.a(6.5f), vector4f.z, 11.0f)) {
            return false;
        }
        if (button == 0) {
            this.isDragging = true;
            updateSliderFromMouse(mouseX);
            return true;
        }
        if (button != 2) {
            return false;
        }
        if (!(setting instanceof SliderSetting)) {
            throw new ClassCastException();
        }
        setting.b();
        return true;
    }

    @Override

    public boolean onMouseRelease(double mouseX, double mouseY, int button) {
        this.isDragging = false;
        return false;
    }

    @Override

    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        var setting = this.b;
        Vector4f vector4f = this.a;
        if (!(setting instanceof SliderSetting)) {
            throw new ClassCastException();
        }
        SliderSetting sliderSetting = setting;
        if (!sliderSetting.e || !MathUtil.a(mouseX, mouseY, vector4f.x, vector4f.y, vector4f.z, ((vector4f.y + Fonts.c.a(6.5f)) + 9.5f) - vector4f.y)) {
            return false;
        }
        Float fC = sliderSetting.c();
        if (!(fC instanceof Float)) {
            throw new ClassCastException();
        }
        sliderSetting.a(Float.valueOf(MathUtil.b(Math.round((fC.floatValue() + (((float) Math.signum(amount)) * sliderSetting.c)) / sliderSetting.c) * sliderSetting.c, sliderSetting.a, sliderSetting.b)));
        return true;
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, float delta, float extend) {
        MatrixStack matrices = context.getMatrices();
        Draw2DProcessor draw = Laura.getInstance().getModuleProcessor().i();
        ThemeProcessor theme = Laura.getInstance().getModuleProcessor().o();
        int primary = theme.a(ThemeInfo.PRIMARY).toIntColor();
        this.a.w = 18.0f;
        if (this.isDragging) {
            updateSliderFromMouse(mouseX);
        }
        getActivationAnimation().c(MathUtil.c(getActivationAnimation().a(), (this.b.c().floatValue() - this.b.a) / (this.b.b - this.b.a), 1.0f));
        float progress = MathUtil.b(getActivationAnimation().a(), 0.0f, 1.0f);
        boolean hovered = MathUtil.a(mouseX, mouseY, this.a.x, this.a.y, this.a.z, this.a.w) && extend >= 1.0f;

        this.knobSpring.to((hovered || this.isDragging) ? 1.0f : 0.0f);
        float knobScale = 1.0f + (0.35f * MathUtil.b(this.knobSpring.get(), 0.0f, 1.2f));

        // Label + value pill
        float current = this.b.a + ((this.b.b - this.b.a) * progress);
        String value = this.b.c % 1.0f == 0.0f ? String.valueOf(Math.round(current)) : String.valueOf(Math.round(current * 100.0f) / 100.0f);
        float boxWidth = Fonts.c.a(value, 6.25f) + 6.0f;
        float boxHeight = Fonts.c.a(6.25f) + 2.0f;
        float boxX = (this.a.x + this.a.z) - boxWidth;
        drawLabel(matrices, Fonts.c, this.b.i(), this.a.x, this.a.y + 0.5f, Fonts.c.a(6.5f), 6.5f, theme.a(ThemeInfo.TEXT).toIntColor(), (boxX - this.a.x) - 4.0f, hovered, extend, delta);
        draw.a(matrices, boxX, this.a.y, boxWidth, boxHeight, 2.5f, ColorUtil.applyAlphaToColor(primary, (0.10f + (0.15f * (this.isDragging ? 1.0f : 0.0f))) * extend));
        draw.a(matrices, boxX, this.a.y, boxWidth, boxHeight, 2.5f, 0.5f, ColorUtil.applyAlphaToColor(theme.a(ThemeInfo.OUTLINE_SMALL).toIntColor(), theme.a(ThemeInfo.OUTLINE_SMALL).getAlphaFloat() * extend));
        Fonts.c.b(matrices, value, boxX + (boxWidth / 2.0f), (this.a.y + ((boxHeight - Fonts.c.a(6.25f)) / 2.0f)) - 0.5f, 6.25f, ColorUtil.applyAlphaToColor(theme.a(ThemeInfo.TEXT).toIntColor(), extend));

        // Track
        float trackY = this.a.y + Fonts.c.a(6.5f) + 7.0f;
        draw.a(matrices, this.a.x, trackY, this.a.z, 3.5f, 1.75f, ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(40, 42, 52, 255), extend * 0.55f));
        float fillW = Math.max(3.5f, this.a.z * progress);
        int fillEnd = ColorUtil.b(primary, 1.35f);
        draw.a(matrices, this.a.x, trackY, fillW, 3.5f, 1.75f, ColorUtil.lerpColor(primary, fillEnd, progress));
        // Glow under the fill
        if (progress > 0.02f) {
            draw.a(matrices, this.a.x, trackY - 1.0f, fillW, 5.5f, 2.75f, ColorUtil.applyAlphaToColor(primary, 0.18f * extend));
        }
        // Knob
        float knobX = this.a.x + ((this.a.z - 6.0f) * progress) + 3.0f;
        float knobSize = 6.5f * knobScale;
        float knobY = (trackY + 1.75f) - (knobSize / 2.0f);
        draw.a(matrices, knobX - (knobSize / 2.0f) + 0.5f, knobY + 1.0f, knobSize, knobSize, knobSize / 2.0f, ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(0, 0, 0, 255), 0.30f * extend));
        draw.a(matrices, knobX - (knobSize / 2.0f), knobY, knobSize, knobSize, knobSize / 2.0f, ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(255, 255, 255, 255), extend));
        draw.a(matrices, knobX - (knobSize / 2.0f), knobY, knobSize, knobSize, knobSize / 2.0f, 0.5f, ColorUtil.applyAlphaToColor(primary, 0.6f * extend));
    }

    private void updateSliderFromMouse(double mouseX) {
        float progress = MathUtil.b(((float) (mouseX - ((double) this.a.x))) / this.a.z, 0.0f, 1.0f);
        float value = this.b.a + ((this.b.b - this.b.a) * progress);
        this.b.a(Float.valueOf(MathUtil.b(Math.round(value / this.b.c) * this.b.c, this.b.a, this.b.b)));
    }
}
