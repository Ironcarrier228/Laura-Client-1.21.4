package laura.ui.screen;


import laura.config.ThemeInfo;
import laura.config.ThemeProcessor;
import laura.core.Category;
import laura.core.Laura;
import laura.core.Module;
import laura.render.*;
import laura.ui.element.Element;
import laura.util.KeyUtil;
import laura.util.MathUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;

import java.util.Iterator;
import java.util.List;

/**
 * One category column of the module GUI.
 *
 * <p>Each panel slides in with a staggered spring-ish entrance (the per-panel
 * {@code open} value is computed by {@link GUIScreen} from the index), shows a
 * header with an accent underline, and renders module rows with hover
 * indicators, a sliding toggle switch and a bind chip.</p>
 */
public class GUIPanel {
    private final Vector4f a = new Vector4f(0.0f, 0.0f, 125.0f, 270.0f);
    private final AnimationUtil b = new AnimationUtil();
    private final AnimationUtil c = new AnimationUtil();
    private final Category d;
    private int index = 0;
    private List<Module> e;
    private Module f;

    public GUIPanel(Category category) {
        this.d = category;
    }

    public void setIndex(int index) {
        this.index = index;
    }


    public boolean a(final double mouseX, final double mouseY, final int button) {
        for (Module module : this.e) {
            if (module.n()) {
                module.a(-100 + button);
                module.b(false);
                return true;
            }
        }
        if (this.f != null) {
            if (button == 0) {
                this.f.a();
                return true;
            }
            if (button == 1) {
                this.f.c(!this.f.o());
                return true;
            }
            if (button == 2) {
                this.e.forEach(obj -> this.i(obj));
                return true;
            }
        }
        return this.e.stream().filter(Module::o).flatMap(module -> module.d().stream()).filter(Element::isEnabled).anyMatch(element -> element.onMouseClick(mouseX, mouseY, button));
    }


    public boolean b(final double mouseX, final double mouseY, final int button) {
        return this.e.stream().filter(Module::o).flatMap(module -> module.d().stream()).filter(Element::isEnabled).anyMatch(element -> element.onMouseRelease(mouseX, mouseY, button));
    }


    public boolean a(final double mouseX, final double mouseY, final int button, final double deltaX, final double deltaY) {
        return this.e.stream().filter(Module::o).flatMap(module -> module.d().stream()).filter(Element::isEnabled).anyMatch(element -> element.onMouseDrag(mouseX, mouseY, button, deltaX, deltaY));
    }


    public boolean a(final int keyCode, final int scanCode, final int modifiers) {
        for (Module module : this.e) {
            if (module.n()) {
                module.a(keyCode);
                module.b(false);
                return true;
            }
        }
        return this.e.stream().filter(Module::o).flatMap(module -> module.d().stream()).filter(Element::isEnabled).anyMatch(element -> element.onKeyPress(keyCode, scanCode, modifiers));
    }


    public boolean a(final char chr, final int modifiers) {
        return this.e.stream().filter(Module::o).flatMap(module -> module.d().stream()).filter(Element::isEnabled).anyMatch(element -> element.onCharTyped(chr, modifiers));
    }


    public boolean a(final double mouseX, final double mouseY, final double amount) {
        if (!MathUtil.a(mouseX, mouseY, this.a.x, this.a.y, this.a.z, this.a.w)) {
            return false;
        }
        if (this.e.stream().filter(Module::o).flatMap(module -> module.d().stream()).filter(Element::isEnabled).anyMatch(element -> element.onMouseScroll(mouseX, mouseY, amount))) {
            return true;
        }
        this.b.a((float) amount * 15.0f);
        return true;
    }

    public void a(List<Module> modules) {
        this.e = modules;
    }

    public void a(Module hovered) {
        this.f = hovered;
    }

    public Vector4f f() {
        return this.a;
    }

    public AnimationUtil a() {
        return this.b;
    }

    public AnimationUtil b() {
        return this.c;
    }

    public Category c() {
        return this.d;
    }

    public List<Module> d() {
        return this.e;
    }

    public Module e() {
        return this.f;
    }

    public void a(DrawContext context, int mouseX, int mouseY, float delta) {
        a(context, mouseX, mouseY, delta, this.c.c());
    }

    /**
     * @param open 0..1 staggered entrance progress for this panel
     */
    public void a(DrawContext context, int mouseX, int mouseY, float delta, float open) {
        MatrixStack matrices = context.getMatrices();
        Draw2DProcessor draw = Laura.getInstance().getModuleProcessor().i();
        ThemeProcessor theme = Laura.getInstance().getModuleProcessor().o();
        float scale = 0.86f + (0.14f * EasingList.s.ease(open));
        matrices.push();
        matrices.translate(this.a.x + (this.a.z / 2.0f), this.a.y + (this.a.w / 2.0f) + ((1.0f - EasingList.p.ease(open)) * 16.0f), 0.0f);
        matrices.scale(scale, scale, 1.0f);
        matrices.translate(-(this.a.x + (this.a.z / 2.0f)), -(this.a.y + (this.a.w / 2.0f)), 0.0f);
        int background = ColorUtil.combineColorWithAlpha(ColorUtil.lerpColor(theme.a(ThemeInfo.BACKGROUND_GUI).toIntColor(), theme.a(ThemeInfo.PRIMARY).toIntColor(), theme.a(ThemeInfo.PRIMARY).getAlphaFloat() / 4.0f), 230);
        draw.a(matrices, this.a.x, this.a.y, this.a.z, this.a.w, 8.0f, background, 1.0f, background, 16.0f);
        draw.a(matrices, this.a.x, this.a.y, this.a.z, this.a.w, 8.0f, 0.5f, ColorUtil.applyAlphaToColor(theme.a(ThemeInfo.OUTLINE_MEDIUM).toIntColor(), MathUtil.b(open * 3.0f, 0.0f, 1.0f)));
        // subtle vertical sheen
        int sheenTop = ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(255, 255, 255, 255), 0.03f * open);
        int sheenBottom = ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(255, 255, 255, 255), 0.0f);
        draw.a(matrices, this.a.x + 1.0f, this.a.y + 1.0f, this.a.z - 2.0f, (this.a.w - 2.0f) * 0.4f, 7.0f, sheenTop, sheenTop, sheenBottom, sheenBottom);
        a(matrices, theme, open);
        a(context, mouseX, mouseY, this.a.y + 26.0f + 4.0f, delta, open);
        matrices.pop();
    }

    private void a(MatrixStack matrices, ThemeProcessor theme, float open) {
        Draw2DProcessor draw = Laura.getInstance().getModuleProcessor().i();
        float headerCenter = this.a.y + 13.0f;
        float titleX = this.a.x + 10.0f;
        float iconWidth = Fonts.a.b(this.d.a(), 9.0f);
        float iconX = (((this.a.x + this.a.z) - 10.0f) - 4.0f) - iconWidth;
        int color = ColorUtil.lerpColor(ColorUtil.convertToARGB(255, 255, 255, 255), ColorUtil.applyAlphaToColor(theme.a(ThemeInfo.PRIMARY).toIntColor(), 1.0f), 0.25f);
        Fonts.c.a(matrices, this.d.name(), titleX, Fonts.c.a(this.d.name(), 9.0f, headerCenter), 9.0f, ColorUtil.applyAlphaToColor(color, MathUtil.b(open * 2.0f, 0.0f, 1.0f)));
        Fonts.a.a(matrices, this.d.a(), iconX, Fonts.a.a(this.d.a(), 9.0f, headerCenter), 9.0f, ColorUtil.applyAlphaToColor(color, MathUtil.b(open * 2.0f, 0.0f, 1.0f)));
        // accent underline that grows in with the panel
        int primary = theme.a(ThemeInfo.PRIMARY).toIntColor();
        float lineW = (this.a.z - 20.0f) * MathUtil.b(open * 1.5f, 0.0f, 1.0f);
        if (lineW > 0.5f) {
            draw.a(matrices, this.a.x + 10.0f, this.a.y + 24.0f, lineW, 1.0f, 0.5f, ColorUtil.applyAlphaToColor(primary, 0.55f * open));
        }
    }

    private void a(DrawContext context, int mouseX, int mouseY, float y, float delta, float open) {
        MatrixStack matrices = context.getMatrices();
        Draw2DProcessor draw = Laura.getInstance().getModuleProcessor().i();
        ThemeProcessor theme = Laura.getInstance().getModuleProcessor().o();
        int primary = theme.a(ThemeInfo.PRIMARY).toIntColor();
        float view = (((this.a.y + this.a.w) - 8.0f) - y) + 4.0f;
        float content = 0.0f;
        Iterator<Module> it = this.e.iterator();
        while (it.hasNext()) {
            content += b(it.next()) + 4.0f;
        }
        float y2 = y + this.b.a(Math.min(0.0f, view - content), 0.0f, 1.0f);
        this.f = null;
        ScissorUtil.a(matrices, this.a.x, y, this.a.z, view);
        float bottom = y + view;
        int rowIndex = 0;
        Iterator<Module> it2 = this.e.iterator();
        while (it2.hasNext()) {
            Module module = it2.next();
            float center = y2 + 8.0f;
            float activation = module.f().c();
            float fade = (float) Math.pow(MathUtil.a(MathUtil.b((bottom - y2) / 16.0f, 0.0f, 1.0f)), 1.0d);
            // staggered row reveal
            float rowIn = MathUtil.b((open - (rowIndex * 0.03f)) / 0.45f, 0.0f, 1.0f);
            fade *= EasingList.s.ease(rowIn);
            boolean hover = ((float) mouseY) >= y && ((float) mouseY) <= bottom && MathUtil.a(mouseX, mouseY, this.a.x + 6.0f, y2, this.a.z - 12.0f, 16.0f);
            if (hover) {
                this.f = module;
            }
            module.h().a(0.0f, 1.0f, 0.5f, EasingList.i, delta);
            module.i().a(0.0f, 1.0f, 0.25f, EasingList.i, delta);
            module.h().a(module.o());
            module.i().a(module == this.f);
            float hoverV = module.i().c();
            float total = b(module);
            if (y2 + total > y && y2 < bottom) {
                // row background
                draw.a(matrices, this.a.x + 6.0f, y2, this.a.z - 12.0f, total, 5.0f, ColorUtil.applyAlphaToColor(primary, 0.05f * activation * fade));
                draw.a(matrices, this.a.x + 6.0f, y2, this.a.z - 12.0f, total, 5.0f, ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(255, 255, 255, 255), 0.04f * hoverV * fade));
                draw.a(matrices, this.a.x + 6.0f, y2, this.a.z - 12.0f, total, 5.0f, 0.5f, ColorUtil.applyAlphaToColor(ColorUtil.lerpColor(theme.a(ThemeInfo.OUTLINE_SMALL).toIntColor(), primary, 0.4f * hoverV), theme.a(ThemeInfo.OUTLINE_SMALL).getAlphaFloat() * activation * fade + 0.10f * hoverV * fade));
                // hover accent bar
                if (hoverV > 0.01f) {
                    draw.a(matrices, this.a.x + 6.0f, y2 + 4.0f, 2.5f, Math.max(1.0f, total - 8.0f), 1.25f, ColorUtil.applyAlphaToColor(primary, 0.85f * hoverV * fade));
                }
                float textX = this.a.x + 6.0f + 4.0f + 2.0f;
                Fonts.c.a(matrices, module.j(), textX, (center - (Fonts.c.a(7.5f) / 2.0f)) - 0.5f, 7.5f, ColorUtil.applyAlphaToColor(ColorUtil.lerpColor(theme.a(ThemeInfo.TEXT).toIntColor(), ColorUtil.convertToARGB(255, 255, 255, 255), 0.5f * hoverV), fade));
                if (module.g().c() > 0.0f) {
                    float bind = module.g().c();
                    String bindText = module.n() ? "?" : KeyUtil.b(module.p());
                    float iconWidth = Fonts.a.b("C", 6.0f);
                    float boxWidth = 4.0f + iconWidth + 2.5f + Fonts.c.a(bindText, 6.0f) + 4.0f;
                    float boxX = this.a.x + 6.0f + 4.0f + 2.0f + Fonts.c.a(module.j(), 7.5f) + 4.0f;
                    float boxY = center - 4.5f;
                    draw.a(matrices, boxX, boxY, boxWidth, 9.0f, 2.5f, ColorUtil.applyAlphaToColor(primary, 0.18f * bind * fade));
                    draw.a(matrices, boxX, boxY, boxWidth, 9.0f, 2.5f, 0.5f, ColorUtil.applyAlphaToColor(theme.a(ThemeInfo.OUTLINE_MEDIUM).toIntColor(), theme.a(ThemeInfo.OUTLINE_MEDIUM).getAlphaFloat() * bind * fade));
                    Fonts.a.a(matrices, "C", boxX + 4.0f, Fonts.a.a("C", 6.0f, center), 6.0f, ColorUtil.applyAlphaToColor(theme.a(ThemeInfo.TEXT).toIntColor(), bind * fade));
                    Fonts.c.a(matrices, bindText, boxX + 4.0f + iconWidth + 2.5f, Fonts.c.a(bindText, 6.0f, center), 6.0f, ColorUtil.applyAlphaToColor(theme.a(ThemeInfo.TEXT).toIntColor(), bind * fade));
                }
                if (module.d().stream().anyMatch((v0) -> {
                    return v0.isEnabled();
                })) {
                    float dotsX = (((this.a.x + this.a.z) - 10.0f) - 4.0f) - Fonts.c.a("...", 9.0f) - (activation > 0.0f ? 19.0f : 0.0f);
                    Fonts.c.a(matrices, "...", dotsX, Fonts.c.a("...", 9.0f, center), 9.0f, ColorUtil.applyAlphaToColor(theme.a(ThemeInfo.TEXT_DISABLED).toIntColor(), fade));
                }
                if (activation > 0.0f) {
                    // modern toggle switch with spring-ish overshoot
                    float toggleX = (((this.a.x + this.a.z) - 10.0f) - 4.0f) - 15.0f;
                    float toggleY = center - 4.75f;
                    float knobT = EasingList.s.ease(activation);
                    draw.a(matrices, toggleX - 1.5f, toggleY - 1.5f, 18.0f, 12.5f, 6.25f, ColorUtil.applyAlphaToColor(primary, 0.18f * activation * fade));
                    draw.a(matrices, toggleX, toggleY, 15.0f, 9.5f, 4.75f, ColorUtil.applyAlphaToColor(ColorUtil.lerpColor(ColorUtil.convertToARGB(45, 47, 58, 255), primary, 1.0f), (0.30f + (0.70f * activation)) * fade));
                    draw.a(matrices, toggleX, toggleY, 15.0f, 9.5f, 4.75f, 0.3f, ColorUtil.applyAlphaToColor(theme.a(ThemeInfo.OUTLINE_SMALL).toIntColor(), theme.a(ThemeInfo.OUTLINE_SMALL).getAlphaFloat() * fade));
                    draw.a(matrices, toggleX + 1.25f + ((15.0f - 2.5f - 7.0f) * knobT), toggleY + 1.25f, 7.0f, 7.0f, 3.5f, ColorUtil.applyAlphaToColor(ColorUtil.lerpColor(ColorUtil.convertToARGB(150, 150, 160, 255), ColorUtil.convertToARGB(255, 255, 255, 255), activation), fade));
                }
                float extend = module.h().c();
                if (extend > 0.0f) {
                    ScissorUtil.a(matrices, this.a.x + 6.0f, y2, this.a.z - 12.0f, total);
                    float baseY = (y2 + 16.0f) - (4.0f * (1.0f - extend));
                    float offset = 0.0f;
                    for (Element<?> element : module.d()) {
                        element.getVisibilityAnimation().a(element.isEnabled());
                        element.getVisibilityAnimation().a(0.0f, 1.0f, 0.4f, EasingList.i, delta);
                        float visible = element.getVisibilityAnimation().c();
                        if (visible > 0.0f) {
                            float targetY = (baseY + offset) - (4.0f * (1.0f - visible));
                            float currentY = baseY + ((targetY - baseY) * extend);
                            element.getBounds().set(this.a.x + 6.0f + 8.5f, currentY, (this.a.z - 12.0f) - 13.0f, element.getBounds().w());
                            element.render(context, mouseX, mouseY, delta, extend * visible * MathUtil.b(open * 2.0f, 0.0f, 1.0f));
                            offset += (element.getBounds().w() + 4.0f) * visible;
                        }
                    }
                    ScissorUtil.a(matrices);
                }
            }
            y2 += total + 4.0f;
            rowIndex++;
        }
        ScissorUtil.a(matrices);
    }

    public void a(DrawContext context, double mouseX, double mouseY, float delta) {
        for (Module module : this.e) {
            for (Element<?> element : module.d()) {
                element.renderColorPicker(context, mouseX, mouseY, delta);
            }
        }
    }

    private float b(Module module) {
        return 16.0f + (module.d().isEmpty() ? 0.0f : ((float) module.d().stream().mapToDouble(e -> {
            return (e.getBounds().w() + 4.0f) * e.getVisibilityAnimation().c();
        }).sum()) * module.h().c());
    }

    public void i(Module module) {
        module.b(module == this.f && !module.n());
    }
}
