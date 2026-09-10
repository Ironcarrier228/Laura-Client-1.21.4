package laura.ui.element;


import laura.config.ThemeInfo;
import laura.config.ThemeProcessor;
import laura.core.Laura;
import laura.render.*;
import laura.setting.ModeSetting;
import laura.util.MathUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;

/**
 * Mode setting: row of pill chips. The selected chip glows with the theme
 * accent; chips animate in/out and react to hover.
 */
public class ModeElement extends Element<ModeSetting> {
    private final AnimationUtil[] modeAnimations;
    private final AnimationUtil[] hoverAnimations;

    public ModeElement(ModeSetting setting) {
        super(setting);
        this.modeAnimations = new AnimationUtil[setting.k().size()];
        this.hoverAnimations = new AnimationUtil[setting.k().size()];
        for (int i = 0; i < this.modeAnimations.length; i++) {
            this.modeAnimations[i] = new AnimationUtil();
            this.hoverAnimations[i] = new AnimationUtil();
        }
    }

    @Override

    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        Vector4f vector4f = this.a;
        var setting = this.b;
        if (button != 0) {
            if (button != 2 || !MathUtil.a(mouseX, mouseY, vector4f.x, vector4f.y, vector4f.z, vector4f.w)) {
                return false;
            }
            if (!(setting instanceof ModeSetting)) {
                throw new ClassCastException();
            }
            setting.b();
            return true;
        }
        float f = vector4f.x;
        float fA = vector4f.y + Fonts.c.a(6.5f) + 5.0f;
        if (!(setting instanceof ModeSetting)) {
            throw new ClassCastException();
        }
        ModeSetting modeSetting = setting;
        int i = 0;
        for (String str : modeSetting.k()) {
            if (!(str instanceof String)) {
                throw new ClassCastException();
            }
            String str2 = str;
            float fA2 = Fonts.c.a(str2, 6.25f) + 7.0f;
            if (f + fA2 > vector4f.x + vector4f.z) {
                f = vector4f.x;
                fA += 12.0f;
            }
            boolean over = MathUtil.a(mouseX, mouseY, f, fA, fA2, 9.0f);
            this.hoverAnimations[i].a(over);
            this.hoverAnimations[i].a(0.0f, 1.0f, 0.3f, EasingList.i, 0.5f);
            if (over) {
                modeSetting.a(str2);
                return true;
            }
            f += fA2 + 3.0f;
            i++;
        }
        return false;
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, float delta, float extend) {
        MatrixStack matrices = context.getMatrices();
        Draw2DProcessor draw = Laura.getInstance().getModuleProcessor().i();
        ThemeProcessor theme = Laura.getInstance().getModuleProcessor().o();
        int primary = theme.a(ThemeInfo.PRIMARY).toIntColor();
        Fonts.c.a(matrices, this.b.i(), this.a.x, this.a.y, 6.5f, ColorUtil.applyAlphaToColor(theme.a(ThemeInfo.TEXT).toIntColor(), extend));
        float x = this.a.x;
        float y = this.a.y + Fonts.c.a(6.5f) + 5.0f;
        int i = 0;
        for (String mode : this.b.k()) {
            float width = Fonts.c.a(mode, 6.25f) + 7.0f;
            if (x + width > this.a.x + this.a.z) {
                x = this.a.x;
                y += 12.0f;
            }
            this.modeAnimations[i].a(this.b.l(mode));
            this.modeAnimations[i].a(0.0f, 1.0f, 0.3f, EasingList.i, delta);
            float value = this.modeAnimations[i].c();
            this.hoverAnimations[i].a(MathUtil.a(mouseX, mouseY, x, y, width, 9.0f) && extend >= 1.0f);
            this.hoverAnimations[i].a(0.0f, 1.0f, 0.3f, EasingList.i, delta);
            float hover = MathUtil.b(this.hoverAnimations[i].c(), 0.0f, 1.0f);
            float lift = (-0.5f * hover) * (1.0f - value);
            draw.a(matrices, x, y + lift, width, 9.0f, 4.5f, ColorUtil.applyAlphaToColor(ColorUtil.lerpColor(ColorUtil.convertToARGB(38, 40, 50, 255), primary, 1.0f), ((0.25f + (0.85f * value)) + (0.18f * hover * (1.0f - value))) * extend));
            draw.a(matrices, x, y + lift, width, 9.0f, 4.5f, 0.5f, ColorUtil.applyAlphaToColor(ColorUtil.lerpColor(theme.a(ThemeInfo.OUTLINE_SMALL).toIntColor(), primary, value * 0.7f + hover * 0.3f), theme.a(ThemeInfo.OUTLINE_SMALL).getAlphaFloat() * extend + 0.35f * value * extend));
            int color = ColorUtil.lerpColor(theme.a(ThemeInfo.TEXT_DISABLED).toIntColor(), ColorUtil.convertToARGB(255, 255, 255, 255), Math.max(value, hover * 0.5f));
            Fonts.c.b(matrices, mode, x + (width / 2.0f), (y + lift + ((9.0f - Fonts.c.a(6.25f)) / 2.0f)) - 0.75f, 6.25f, ColorUtil.applyAlphaToColor(color, extend));
            x += width + 3.0f;
            i++;
        }
        this.a.w = (y + 9.0f) - this.a.y;
    }
}
