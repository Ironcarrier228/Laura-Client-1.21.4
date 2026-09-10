package laura.ui.element;

import laura.config.ThemeInfo;
import laura.config.ThemeProcessor;
import laura.core.Laura;
import laura.render.ColorUtil;
import laura.render.Draw2DProcessor;
import laura.render.EasingList;
import laura.render.Fonts;
import laura.render.Spring;
import laura.util.MathUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Main-menu button.
 *
 * <p>Modern flat button: rounded card, theme-aware accent border, spring
 * based hover (lift + scale + glow), press state (vanilla-style: the action
 * fires on mouse release while the cursor is still over the button) and a
 * staggered slide-up entrance when the screen opens.</p>
 */
public class Button {
    public static final float LABEL_SIZE = 10.5f;

    private final float width;
    private final float height;
    private final String label;
    private final Runnable action;
    private final int index;
    private final Spring hoverSpring = Spring.snappy();
    private final Spring pressSpring = Spring.snappy();
    private float x;
    private float y;
    private boolean pressed;

    public Button(float width, float height, String label, Runnable action) {
        this(width, height, label, action, 0);
    }

    public Button(float width, float height, String label, Runnable action, int index) {
        this.width = width;
        this.height = height;
        this.label = label;
        this.action = action;
        this.index = index;
    }

    public float getWidth() {
        return this.width;
    }

    public float getHeight() {
        return this.height;
    }

    public String getLabel() {
        return this.label;
    }

    public Runnable getAction() {
        return this.action;
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /** The screen is pressing this button (mouse button 0 held down on it). */
    public void setPressed(boolean pressed) {
        this.pressed = pressed;
    }

    public boolean isPressed() {
        return this.pressed;
    }

    public boolean contains(double mouseX, double mouseY) {
        return MathUtil.a(mouseX, mouseY, this.x, this.y, this.width, this.height);
    }

    /**
     * @param open 0..1 overall screen openness (raw, not eased)
     */
    public void render(DrawContext context, int mouseX, int mouseY, float delta, float open) {
        float raw = MathUtil.b((open - (this.index * 0.16f)) / 0.34f, 0.0f, 1.0f);
        float in = EasingList.s.ease(raw);
        float alpha = in * MathUtil.b(open * 4.0f, 0.0f, 1.0f);
        if (alpha <= 0.002f) {
            return;
        }

        Draw2DProcessor draw = Laura.getInstance().getModuleProcessor().i();
        ThemeProcessor theme = Laura.getInstance().getModuleProcessor().o();
        int primary = theme.a(ThemeInfo.PRIMARY).toIntColor();

        boolean hovered = this.action != null && MathUtil.a(mouseX, mouseY, this.x, this.y, this.width, this.height);
        this.hoverSpring.to(hovered ? 1.0f : 0.0f);
        this.pressSpring.to(this.pressed && hovered ? 1.0f : 0.0f);
        float hover = MathUtil.b(this.hoverSpring.get(), 0.0f, 1.5f);
        float press = MathUtil.b(this.pressSpring.get(), 0.0f, 1.0f);

        // Entrance: slide up + pop in. Hover: lift. Press: sink.
        float rise = (1.0f - in) * 18.0f;
        float lift = (-1.4f * hover) + (1.2f * press);
        float y = this.y + rise + lift;
        float scale = (0.92f + (0.08f * in)) * (1.0f + (0.045f * hover)) * (1.0f - (0.06f * press));
        float cx = this.x + (this.width / 2.0f);
        float cy = this.y + rise + (this.height / 2.0f);

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(cx, cy, 0.0f);
        matrices.scale(scale, scale, 1.0f);
        matrices.translate(-cx, -cy, 0.0f);

        // Soft shadow (deeper on hover)
        draw.a(matrices, this.x + 1.0f, y + 3.0f, this.width, this.height, 9.0f, ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(0, 0, 0, 255), (0.30f + (0.30f * hover)) * alpha));

        // Card
        int bg = ColorUtil.lerpColor(ColorUtil.convertToARGB(16, 16, 22, 240), ColorUtil.convertToARGB(32, 34, 46, 245), hover);
        draw.a(matrices, this.x, y, this.width, this.height, 8.0f, ColorUtil.applyAlphaToColor(bg, alpha));

        // Accent border: primary from the theme, stronger on hover
        draw.a(matrices, this.x, y, this.width, this.height, 8.0f, 0.5f, ColorUtil.applyAlphaToColor(primary, (0.25f + (0.75f * hover) + (0.35f * press)) * alpha));

        // Top sheen
        int sheen = ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(255, 255, 255, 255), (0.07f + (0.07f * hover)) * alpha);
        int sheenBottom = ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(255, 255, 255, 255), 0.0f);
        draw.a(matrices, this.x + 0.75f, y + 0.75f, this.width - 1.5f, (this.height - 1.5f) * 0.55f, 7.0f, sheen, sheen, sheenBottom, sheenBottom);

        // Label
        if (this.label != null) {
            float labelW = Fonts.e.a(this.label, LABEL_SIZE);
            int textColor = ColorUtil.lerpColor(ColorUtil.convertToARGB(214, 217, 228, 255), ColorUtil.convertToARGB(255, 255, 255, 255), hover);
            Fonts.e.a(matrices, this.label, cx - (labelW / 2.0f), Fonts.e.a(this.label, LABEL_SIZE, cy), LABEL_SIZE, ColorUtil.applyAlphaToColor(textColor, alpha));
        }
        matrices.pop();
    }
}
