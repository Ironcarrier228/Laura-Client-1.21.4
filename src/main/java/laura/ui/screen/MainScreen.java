package laura.ui.screen;


import laura.config.ThemeInfo;
import laura.core.Laura;
import laura.core.Interface;
import laura.render.*;
import laura.ui.element.Button;
import laura.ui.shader.GradientUtil;
import laura.ui.widget.EffectMarker;
import laura.util.MathUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom main menu (replaces the vanilla title screen via TitleScreenMixin).
 *
 * <p>Layout: parallax background + vignette, animated gradient title,
 * a 2x2 grid of spring-animated buttons and a drag-to-exit pill at the
 * bottom. All elements enter with a staggered slide-up animation.</p>
 */
public class MainScreen extends Screen {
    private static final float[] parallax = new float[2];

    private static final float BTN_W = 128.0f;
    private static final float BTN_H = 27.0f;
    private static final float BTN_GAP = 8.0f;
    private static final float EXIT_W = 96.0f;
    private static final float EXIT_H = 20.0f;

    private final AnimationUtil open = new AnimationUtil();
    private final Spring exitSpring = Spring.bouncy();
    private final List<Button> buttons = new ArrayList<>();
    private final List<EffectMarker.a> particles = new ArrayList<>();
    private Button pressedButton;
    private float exitX;
    private float exitY;
    private float exitProgress;   // 0..1 actual knob position
    private float exitTarget;     // where the knob wants to be
    private boolean draggingExit;

    public MainScreen() {
        super(Text.empty());
        this.exitTarget = 0.0f;
        if (Interface.mc.currentScreen instanceof MainScreen) {
            this.open.c(1.0f);
            this.open.d(1.0f);
            this.open.e(1.0f);
        }
        float w = BTN_W;
        float h = BTN_H;
        this.buttons.add(new Button(w, h, "Одиночная игра", () -> {
            Interface.mc.setScreen(new SelectWorldScreen(null));
        }, 0));
        this.buttons.add(new Button(w, h, "Сетевая игра", () -> {
            Interface.mc.setScreen(new MultiplayerScreen(null));
        }, 1));
        this.buttons.add(new Button(w, h, "Аккаунты", () -> {
            Interface.mc.setScreen(new AltScreen());
        }, 2));
        this.buttons.add(new Button(w, h, "Параметры", () -> {
            Interface.mc.setScreen(new OptionsScreen(null, Interface.mc.options));
        }, 3));
    }

    /**
     * Parallax background. Public static so other screens (AltScreen) can reuse it.
     */
    public static void a(DrawContext context, int width, int height, int mouseX, int mouseY, float scale) {
        float marginX = width * 0.025f;
        float marginY = height * 0.025f;
        parallax[0] += ((MathHelper.clamp((((mouseX / width) - 0.5f) * 2.0f) * marginX, (-marginX) * 0.9f, marginX * 0.9f) - parallax[0]) * 0.03f);
        parallax[1] += ((MathHelper.clamp((((mouseY / height) - 0.5f) * 2.0f) * marginY, (-marginY) * 0.9f, marginY * 0.9f) - parallax[1]) * 0.03f);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(width / 2.0f, height / 2.0f, 0.0f);
        matrices.scale(scale, scale, 1.0f);
        matrices.translate((-width) / 2.0f, (-height) / 2.0f, 0.0f);
        Laura.getInstance().getModuleProcessor().i().a(matrices, Identifier.of("laura", "pictures/main.png"), (-marginX) + parallax[0], (-marginY) + parallax[1], width + (marginX * 2.0f), height + (marginY * 2.0f), 0.0f, -1);
        matrices.pop();
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.open.a(Interface.mc.currentScreen instanceof MainScreen);
        this.open.a(0.0f, 1.0f, 0.15f, EasingList.g, delta);
        float openRaw = Math.min(1.0f, this.open.c() / 0.9f);
        double sx = MathUtil.scale(mouseX, 2);
        double sy = MathUtil.scale(mouseY, 2);
        ScaleUtil.a(context, 2);
        int width = Interface.mc.getWindow().getScaledWidth();
        int height = Interface.mc.getWindow().getScaledHeight();

        a(context, width, height, (int) sx, (int) sy, 1.25f - (EasingList.s.ease(openRaw) * 0.2f));
        Draw2DProcessor draw = Laura.getInstance().getModuleProcessor().i();
        draw.e().a(context.getMatrices());
        a(context, width, height, openRaw);

        // Title
        float gridTop = layout(width, height);
        a(context, width / 2.0f, gridTop - 34.0f, openRaw, draw);

        // Buttons
        for (Button button : this.buttons) {
            button.setPressed(button == this.pressedButton);
            button.render(context, (int) sx, (int) sy, delta, openRaw);
        }

        // Exit pill
        a(context, (int) sx, (int) sy, openRaw, draw);

        EffectMarker.a(context.getMatrices(), delta, this.particles);
        ScaleUtil.a(context);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double sx = MathUtil.scale(mouseX, 2);
        double sy = MathUtil.scale(mouseY, 2);
        EffectMarker.a(this.particles, (float) sx, (float) sy);
        if (button == 0 && MathUtil.a(sx, sy, this.exitX + 2.0f, this.exitY + 2.0f, 16.0f, 16.0f)) {
            this.draggingExit = true;
            this.exitTarget = exitProgressFromMouse((float) sx);
            return true;
        }
        if (button == 0) {
            for (Button b : this.buttons) {
                if (b.getAction() != null && b.contains(sx, sy)) {
                    this.pressedButton = b;
                    b.setPressed(true);
                    return true;
                }
            }
        }
        return super.mouseClicked(sx, sy, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.draggingExit) {
            this.exitTarget = exitProgressFromMouse((float) MathUtil.scale(mouseX, 2));
            return true;
        }
        if (this.pressedButton != null && button == 0) {
            double sx = MathUtil.scale(mouseX, 2);
            double sy = MathUtil.scale(mouseY, 2);
            if (!this.pressedButton.contains(sx, sy)) {
                this.pressedButton.setPressed(false);
                this.pressedButton = null;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingExit && button == 0) {
            this.draggingExit = false;
            if (this.exitTarget >= 0.95f) {
                Interface.mc.scheduleStop();
                return true;
            }
            this.exitTarget = 0.0f;
            return true;
        }
        if (this.pressedButton != null && button == 0) {
            Button b = this.pressedButton;
            this.pressedButton = null;
            b.setPressed(false);
            double sx = MathUtil.scale(mouseX, 2);
            double sy = MathUtil.scale(mouseY, 2);
            if (b.contains(sx, sy) && b.getAction() != null) {
                b.getAction().run();
                return true;
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    public void close() {
    }

    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    /** Positions all elements; returns the top Y of the button grid. */
    private float layout(int width, int height) {
        float gridW = (BTN_W * 2.0f) + BTN_GAP;
        float gridH = (BTN_H * 2.0f) + BTN_GAP;
        float gridX = (width - gridW) / 2.0f;
        float gridTop = ((height - gridH) / 2.0f) + 22.0f;
        for (int i = 0; i < this.buttons.size(); i++) {
            int col = i % 2;
            int row = i / 2;
            this.buttons.get(i).setPosition(gridX + (col * (BTN_W + BTN_GAP)), gridTop + (row * (BTN_H + BTN_GAP)));
        }
        this.exitX = (width - EXIT_W) / 2.0f;
        this.exitY = height - 42.0f;
        return gridTop;
    }

    private void a(DrawContext context, int width, int height, float open) {
        Draw2DProcessor draw = Laura.getInstance().getModuleProcessor().i();
        MatrixStack matrices = context.getMatrices();
        if (open <= 0.002f) {
            return;
        }
        int edge = ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(0, 0, 0, 255), 0.45f * open);
        int center = ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(0, 0, 0, 255), 0.0f);
        draw.a(matrices, 0.0f, 0.0f, width, height, 0.0f, edge, edge, edge, edge);
        float inset = Math.min(width, height) * 0.32f;
        draw.a(matrices, inset, inset, width - (inset * 2.0f), height - (inset * 2.0f), 0.0f, center, center, center, center);
    }

    private void a(DrawContext context, float centerX, float top, float open, Draw2DProcessor draw) {
        if (open <= 0.002f) {
            return;
        }
        MatrixStack matrices = context.getMatrices();
        float in = EasingList.s.ease(MathUtil.b(open / 0.45f, 0.0f, 1.0f));
        float rise = (1.0f - in) * 12.0f;
        int primary = Laura.getInstance().getModuleProcessor().o().a(ThemeInfo.PRIMARY).toIntColor();
        float scale = 0.9f + (0.1f * in);
        matrices.push();
        matrices.translate(centerX, top + 8.0f, 0.0f);
        matrices.scale(scale, scale, 1.0f);
        matrices.translate(-centerX, -(top + 8.0f), 0.0f);
        float titleW = Fonts.e.a("Laura Client", 15.0f);
        Fonts.e.a(matrices, GradientUtil.a("Laura Client", primary, 5.0f, 0.5f), centerX - (titleW / 2.0f), top + rise, 15.0f, 0.0f, open);
        String version = "1.21.4";
        Fonts.b.a(matrices, version, centerX - (Fonts.b.a(version, 7.5f) / 2.0f), top + 21.0f + rise, 7.5f, ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(190, 194, 208, 255), 0.85f * in * open));
        matrices.pop();
    }

    private void a(DrawContext context, int mouseX, int mouseY, float open, Draw2DProcessor draw) {
        if (open <= 0.002f) {
            return;
        }
        float in = EasingList.s.ease(MathUtil.b((open - 0.3f) / 0.4f, 0.0f, 1.0f));
        float alpha = in * MathUtil.b(open * 4.0f, 0.0f, 1.0f);
        if (alpha <= 0.002f) {
            return;
        }

        // Spring the knob back after a cancelled drag
        if (!this.draggingExit) {
            this.exitSpring.to(this.exitTarget);
            this.exitProgress = this.exitSpring.get();
        } else {
            this.exitProgress = MathUtil.c(this.exitProgress, this.exitTarget, 30.0f);
        }
        float p = MathUtil.b(this.exitProgress, 0.0f, 1.0f);
        float fillW = (EXIT_W - 4.0f) * p;
        int red = ColorUtil.convertToARGB(220, 80, 80, 255);

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        float rise = (1.0f - in) * 12.0f;
        float y = this.exitY + rise;

        // Track
        draw.a(matrices, this.exitX, y, EXIT_W, EXIT_H, 9.0f, ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(14, 14, 19, 235), alpha));
        // Danger fill
        if (fillW > 0.5f) {
            draw.a(matrices, this.exitX + 2.0f, y + 2.0f, fillW, EXIT_H - 4.0f, 7.0f, ColorUtil.applyAlphaToColor(red, (0.25f + (0.6f * p)) * alpha));
        }
        draw.a(matrices, this.exitX, y, EXIT_W, EXIT_H, 9.0f, 0.5f, ColorUtil.applyAlphaToColor(ColorUtil.lerpColor(ColorUtil.convertToARGB(255, 255, 255, 30), red, p), alpha));
        // Label
        float labelW = Fonts.e.a("Выйти из игры", 8.0f);
        float labelX = this.exitX + Math.max(4.0f, Math.min(10.0f, EXIT_W - 4.0f - labelW));
        Fonts.e.a(matrices, "Выйти из игры", labelX, Fonts.e.a("Выйти из игры", 8.0f, y + (EXIT_H / 2.0f)), 8.0f, ColorUtil.applyAlphaToColor(ColorUtil.lerpColor(ColorUtil.convertToARGB(200, 203, 214, 255), ColorUtil.convertToARGB(255, 255, 255, 255), p), alpha));
        // Knob
        float knobX = this.exitX + 2.0f + ((EXIT_W - 4.0f - 16.0f) * p);
        float knobY = y + 2.0f;
        draw.a(matrices, knobX + 1.0f, knobY + 2.0f, 16.0f, 16.0f, 8.0f, ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(0, 0, 0, 255), 0.35f * alpha));
        draw.a(matrices, knobX, knobY, 16.0f, 16.0f, 8.0f, ColorUtil.applyAlphaToColor(ColorUtil.lerpColor(ColorUtil.convertToARGB(235, 237, 245, 255), ColorUtil.convertToARGB(255, 255, 255, 255), p), alpha));
        // Power icon on the knob
        matrices.push();
        matrices.translate(knobX + 8.0f, knobY + 8.0f, 0.0f);
        matrices.multiply(new Quaternionf().rotateZ((float) Math.toRadians((-90.0f) + (180.0f * p))));
        matrices.translate(-(knobX + 8.0f), -(knobY + 8.0f), 0.0f);
        float iconY = Fonts.a.a("c", 8.5f, knobY + 8.0f);
        Fonts.a.a(matrices, "c", knobX + 8.0f - (Fonts.a.a("c", 8.5f) / 2.0f) + 0.5f, iconY, 8.5f, ColorUtil.applyAlphaToColor(ColorUtil.lerpColor(ColorUtil.convertToARGB(70, 72, 82, 255), red, p), alpha));
        matrices.pop();
        matrices.pop();
    }

    private float exitProgressFromMouse(float mouseX) {
        return MathHelper.clamp((((mouseX - this.exitX) - 10.0f) / (EXIT_W - 20.0f)), 0.0f, 1.0f);
    }
}
