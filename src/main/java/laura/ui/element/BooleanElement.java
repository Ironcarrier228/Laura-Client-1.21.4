package laura.ui.element;


import laura.config.ThemeInfo;
import laura.config.ThemeProcessor;
import laura.core.Laura;
import laura.event.DrawEvent;
import laura.render.ColorUtil;
import laura.render.Draw2DProcessor;
import laura.render.EasingList;
import laura.render.Fonts;
import laura.render.Spring;
import laura.setting.BooleanSetting;
import laura.util.MathUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;

/**
 * Boolean setting: label + animated toggle switch.
 * The knob is driven by a spring, so toggling has a natural overshoot.
 */
public class BooleanElement extends Element<BooleanSetting> {
    private final Spring knobSpring = Spring.bouncy();

    public BooleanElement(BooleanSetting setting) {
        super(setting);
        this.a.w = 12.0f;
    }

    @Override

    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        Vector4f vector4f = this.a;
        var setting = this.b;
        if (!MathUtil.a(mouseX, mouseY, vector4f.x, vector4f.y, vector4f.z, vector4f.w)) {
            return false;
        }
        if (button != 0) {
            if (button != 2) {
                return false;
            }
            if (!(setting instanceof BooleanSetting)) {
                throw new ClassCastException();
            }
            setting.b();
            return true;
        }
        if (!(setting instanceof BooleanSetting)) {
            throw new ClassCastException();
        }
        BooleanSetting booleanSetting = setting;
        Boolean boolC = booleanSetting.c();
        if (!(boolC instanceof Boolean)) {
            throw new ClassCastException();
        }
        booleanSetting.a(Boolean.valueOf(!boolC.booleanValue()));
        return true;
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, float delta, float extend) {
        MatrixStack matrices = context.getMatrices();
        Draw2DProcessor draw = Laura.getInstance().getModuleProcessor().i();
        ThemeProcessor theme = Laura.getInstance().getModuleProcessor().o();
        int primary = theme.a(ThemeInfo.PRIMARY).toIntColor();

        boolean enabledState = this.b.c().booleanValue();
        getActivationAnimation().a(enabledState);
        getActivationAnimation().a(0.0f, 1.0f, 0.5f, EasingList.i, delta);
        float enabled = getActivationAnimation().c();

        this.knobSpring.to(enabledState ? 1.0f : 0.0f);
        float knob = MathUtil.b(this.knobSpring.get(), -0.2f, 1.2f);

        float centerY = this.a.y + (this.a.w / 2.0f) + 0.5f;
        boolean hovered = MathUtil.a(mouseX, mouseY, this.a.x, this.a.y, this.a.z, this.a.w) && extend >= 1.0f;

        float labelW = (this.a.z - 16.0f) - 4.0f;
        float hoverF = hovered ? 1.0f : 0.0f;
        drawLabel(matrices, Fonts.c, this.b.i(), this.a.x, this.a.y, this.a.w, 6.5f,
                ColorUtil.lerpColor(theme.a(ThemeInfo.TEXT).toIntColor(), ColorUtil.convertToARGB(255, 255, 255, 255), 0.5f * hoverF),
                labelW, hovered, extend, delta);

        // Track
        float trackX = (this.a.x + this.a.z) - 16.0f;
        float trackY = centerY - 5.25f;
        int trackBg = ColorUtil.lerpColor(ColorUtil.convertToARGB(40, 42, 52, 255), primary, 1.0f);
        draw.a(matrices, trackX, trackY, 16.0f, 10.5f, 5.25f, ColorUtil.applyAlphaToColor(trackBg, (0.35f + (0.65f * enabled)) * extend));
        draw.a(matrices, trackX, trackY, 16.0f, 10.5f, 5.25f, 0.5f, ColorUtil.applyAlphaToColor(
                ColorUtil.lerpColor(theme.a(ThemeInfo.OUTLINE_SMALL).toIntColor(), primary, 0.5f * enabled),
                theme.a(ThemeInfo.OUTLINE_SMALL).getAlphaFloat() * extend + 0.15f * enabled * extend));
        // Glow when on
        if (enabled > 0.05f) {
            draw.a(matrices, trackX - 1.5f, trackY - 1.5f, 19.0f, 13.5f, 6.75f, ColorUtil.applyAlphaToColor(primary, 0.16f * enabled * extend));
        }
        // Knob (spring driven, can overshoot the track edges slightly)
        float knobX = trackX + 1.5f + ((16.0f - 3.0f - 7.5f) * knob);
        draw.a(matrices, knobX + 0.5f, trackY + 1.5f + 1.5f, 7.5f, 7.5f, 3.75f, ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(0, 0, 0, 255), 0.25f * extend));
        draw.a(matrices, knobX, trackY + 1.5f, 7.5f, 7.5f, 3.75f, ColorUtil.applyAlphaToColor(
                ColorUtil.lerpColor(ColorUtil.convertToARGB(160, 162, 175, 255), ColorUtil.convertToARGB(255, 255, 255, 255), enabled), extend));
    }

    @Override
    public void onDrawEvent(DrawEvent event, float x, float y, float width, float animation) {
        getActivationAnimation().a(this.b.c().booleanValue());
        getActivationAnimation().a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());
        ThemeProcessor theme = Laura.getInstance().getModuleProcessor().o();
        float textX = x + 19.5f;
        float toggleX = ((x + width) - 11.0f) - 5.0f;
        float toggleY = y + 2.25f;
        int primary = theme.a(ThemeInfo.PRIMARY).toIntColor();
        Fonts.a.a(event.h(), "g", x + 5.0f, y + ((12.0f - Fonts.a.a(6.5f)) / 2.0f), 6.5f, ColorUtil.applyAlphaToColor(primary, animation));
        event.getDraw2DProcessor().a(event.i().getMatrices(), x + 15.5f, y + 3.0f, 0.75f, 6.0f, 0.0f, ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(200, 200, 200, 255), 0.5f * animation));
        Fonts.e.a(event.h(), this.b.i(), textX, (y + ((12.0f - Fonts.e.a(6.5f)) / 2.0f)) - 0.5f, 6.5f, ColorUtil.applyAlphaToColor(-1, animation));
        float value = getActivationAnimation().c();
        event.getDraw2DProcessor().a(event.h(), toggleX, toggleY, 11.0f, 7.5f, 2.5f, ColorUtil.applyAlphaToColor(primary, value * animation));
        event.getDraw2DProcessor().a(event.h(), toggleX, toggleY, 11.0f, 7.5f, 2.5f, 0.3f, ColorUtil.applyAlphaToColor(theme.a(ThemeInfo.OUTLINE_SMALL).toIntColor(), theme.a(ThemeInfo.OUTLINE_SMALL).getAlphaFloat() * animation));
        event.getDraw2DProcessor().a(event.h(), toggleX + 1.5f + (3.5f * value), toggleY + 1.5f, 4.5f, 4.5f, 1.25f, ColorUtil.applyAlphaToColor(ColorUtil.lerpColor(ColorUtil.convertToARGB(150, 150, 155, 255), ColorUtil.convertToARGB(255, 255, 255, 255), value), animation));
    }
}
