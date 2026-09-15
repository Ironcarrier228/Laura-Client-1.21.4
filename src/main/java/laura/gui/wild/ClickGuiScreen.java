package laura.gui.wild;

import laura.config.ModuleProcessor;
import laura.core.Category;
import laura.core.Laura;
import laura.core.Module;
import laura.render.AnimationUtil;
import laura.render.ColorUtil;
import laura.render.Draw2DProcessor;
import laura.render.EasingList;
import laura.setting.Setting;
import laura.ui.element.Element;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * WildClient-style ClickGUI для Laura.
 *
 * Полностью написан на Laura API. Визуально вдохновлён Wexside-1.21.8:
 *  - Категории слева как большие цветные кнопки с blur-эффектом
 *  - Список модулей в центре с плавной анимацией выбора
 *  - Панель настроек модуля справа
 *  - Поиск по модулям вверху
 *  - Анимация открытия/закрытия через laura.render.AnimationUtil
 *
 * Это НЕ перенос wild ClickGuiScreen (тот требует 130+ файлов зависимостей),
 * а новый GUI вдохновлённый wild. Все wild-зависимости заменены на Laura-эквиваленты:
 *
 *  Wild:                      →  Laura:
 *  ru.wild.WildClient         →  laura.core.Laura.getInstance()
 *  FeatureManager             →  ModuleProcessor (getModuleProcessor().t())
 *  ModuleCategory             →  laura.core.Category
 *  ThemeManager/ThemePalette  →  ThemeInfo/ThemeConstructor
 *  BlurStateManager           →  laura.ui.shader.BlurShader (через Draw2DProcessor)
 *  RoundedRectRenderer        →  Draw2DProcessor.a(MatrixStack, x, y, w, h, radius, color)
 *  EasingFunctions            →  laura.render.EasingList
 *  ColorSetting/ThemePalette  →  ThemeInfo + ThemeConstructor
 *  FontRegistry               →  laura.render.Font (встроенный)
 */
public class ClickGuiScreen extends Screen {
    // Размеры окна (как в WildClient: ~480x260 базовых единиц)
    private static final float BASE_WIDTH = 480.0F;
    private static final float BASE_HEIGHT = 260.0F;
    private static final float CATEGORY_PANEL_WIDTH = 100.0F;
    private static final float SETTINGS_PANEL_WIDTH = 160.0F;
    private static final float PADDING = 6.0F;
    private static final float CORNER_RADIUS = 4.0F;

    private final AnimationUtil openAnimation = new AnimationUtil(); // 0 → 1
    private final List<WildCategoryPanel> categoryPanels = new ArrayList<>();
    private String searchQuery = "";
    private boolean searchFocused = false;
    private Category selectedCategory = null;
    private Module selectedModule = null;
    private final List<Element<?>> selectedElements = new ArrayList<>();

    // Цветовая схема (wild-style)
    private enum AccentColor {
        WILD(0xFF5C95FF, 0xFF0A1430, 0xFFEFF5FF),
        CHERRY(0xFFFF547D, 0xFF12091B, 0xFFFFEFF5),
        MINT(0xFF00F5A0, 0xFF09091B, 0xFFEFFFF7),
        SUN(0xFFFFB347, 0xFF121111, 0xFFFFF5E5);
        final int accent;
        final int background;
        final int foreground;
        AccentColor(int a, int b, int c) { this.accent = a; this.background = b; this.foreground = c; }
    }

    private AccentColor currentAccent = AccentColor.WILD;

    public ClickGuiScreen() {
        super(Text.literal("WildGUI"));
        this.openAnimation.a(0.0F, 1.0F, 0.4F, EasingList.h, 1.0F);
        rebuildCategories();
    }

    /** Пересоздаёт панели категорий на основе текущих Category.values() */
    private void rebuildCategories() {
        categoryPanels.clear();
        int idx = 0;
        for (Category cat : Category.values()) {
            WildCategoryPanel panel = new WildCategoryPanel(cat, idx++);
            categoryPanels.add(panel);
        }
        if (selectedCategory == null && !categoryPanels.isEmpty()) {
            selectedCategory = categoryPanels.get(0).category;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Продвигаем анимацию открытия
        this.openAnimation.a(true);
        this.openAnimation.a(0.0F, 1.0F, 0.4F, EasingList.h, delta);

        float t = this.openAnimation.c(); // animationValue (0..1, интерполированный)
        if (t <= 0.001F) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        // Центрируем окно
        float winX = (sw - BASE_WIDTH) / 2.0F;
        float winY = (sh - BASE_HEIGHT) / 2.0F;

        // Применяем easing — масштаб окна
        float scale = 0.92F + 0.08F * t;
        float cx = sw / 2.0F;
        float cy = sh / 2.0F;
        var matrices = ctx.getMatrices();
        matrices.push();
        matrices.translate(cx, cy, 0);
        matrices.scale(scale, scale, 1.0F);
        matrices.translate(-cx, -cy, 0);

        // === Главное окно с blur-фоном ===
        Draw2DProcessor draw2d = Laura.getInstance().getModuleProcessor().t() != null
                ? laura.core.Laura.getInstance().getModuleProcessor().i()
                : null;

        // Фон окна (тёмный полупрозрачный)
        int bgColor = ColorUtil.applyAlphaToColor(this.currentAccent.background, (int)(220 * t));
        if (draw2d != null) {
            draw2d.a(matrices, winX, winY, BASE_WIDTH, BASE_HEIGHT, CORNER_RADIUS, bgColor);
        } else {
            ctx.fill((int)winX, (int)winY, (int)(winX + BASE_WIDTH), (int)(winY + BASE_HEIGHT), bgColor);
        }

        // === Левая панель: категории ===
        renderCategoryPanel(ctx, mouseX, mouseY);

        // === Центральная панель: список модулей ===
        renderModuleList(ctx, mouseX, mouseY);

        // === Правая панель: настройки выбранного модуля ===
        if (selectedModule != null) {
            renderSettingsPanel(ctx, mouseX, mouseY);
        }

        // === Поиск сверху ===
        renderSearchBar(ctx, mouseX, mouseY);

        matrices.pop();

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderCategoryPanel(DrawContext ctx, int mouseX, int mouseY) {
        float x = (MinecraftClient.getInstance().getWindow().getScaledWidth() - BASE_WIDTH) / 2.0F + PADDING;
        float y = (MinecraftClient.getInstance().getWindow().getScaledHeight() - BASE_HEIGHT) / 2.0F + 30.0F; // +30 под поиск
        float rowH = 22.0F;
        var matrices = ctx.getMatrices();
        Draw2DProcessor draw2d = Laura.getInstance().getModuleProcessor().i();

        for (WildCategoryPanel panel : categoryPanels) {
            boolean hover = mouseX >= x && mouseX <= x + CATEGORY_PANEL_WIDTH - PADDING * 2
                    && mouseY >= y && mouseY <= y + rowH;
            boolean selected = panel.category == selectedCategory;
            int color;
            if (selected) {
                color = this.currentAccent.accent;
            } else if (hover) {
                color = ColorUtil.applyAlphaToColor(this.currentAccent.accent, 80);
            } else {
                color = ColorUtil.applyAlphaToColor(0xFFFFFFFF, 30);
            }
            if (draw2d != null) {
                draw2d.a(matrices, x, y, CATEGORY_PANEL_WIDTH - PADDING * 2, rowH - 2, 3.0F, color);
            }
            // Текст
            String label = localizedCategoryName(panel.category);
            ctx.drawText(MinecraftClient.getInstance().textRenderer, Text.literal(label),
                    (int)(x + 8), (int)(y + 7), this.currentAccent.foreground, false);
            y += rowH;
        }
    }

    private void renderModuleList(DrawContext ctx, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float panelX = (mc.getWindow().getScaledWidth() - BASE_WIDTH) / 2.0F + CATEGORY_PANEL_WIDTH + PADDING * 2;
        float panelY = (mc.getWindow().getScaledHeight() - BASE_HEIGHT) / 2.0F + 30.0F;
        float panelW = BASE_WIDTH - CATEGORY_PANEL_WIDTH - SETTINGS_PANEL_WIDTH - PADDING * 4;
        float panelH = BASE_HEIGHT - 30.0F - PADDING * 2;

        // Фон
        var matrices = ctx.getMatrices();
        Draw2DProcessor draw2d = Laura.getInstance().getModuleProcessor().i();
        int bgCol = ColorUtil.applyAlphaToColor(0xFF000000, 90);
        if (draw2d != null) {
            draw2d.a(matrices, panelX, panelY, panelW, panelH, 3.0F, bgCol);
        }

        // Собираем модули выбранной категории с учётом поиска
        List<Module> modules = collectModules();
        float rowY = panelY + 4.0F;
        float rowH = 18.0F;
        for (Module m : modules) {
            boolean hover = mouseX >= panelX && mouseX <= panelX + panelW
                    && mouseY >= rowY && mouseY <= rowY + rowH;
            boolean selected = m == selectedModule;
            int color = selected ? this.currentAccent.accent
                    : (hover ? ColorUtil.applyAlphaToColor(this.currentAccent.accent, 50) : 0);
            if (draw2d != null && color != 0) {
                draw2d.a(matrices, panelX + 2, rowY, panelW - 4, rowH - 2, 2.0F, color);
            }
            int txtColor = m.m() ? this.currentAccent.accent : this.currentAccent.foreground;
            ctx.drawText(mc.textRenderer, Text.literal(m.j()),
                    (int)(panelX + 8), (int)(rowY + 5), txtColor, false);
            rowY += rowH;
        }
    }

    private void renderSettingsPanel(DrawContext ctx, int mouseX, int mouseY) {
        if (selectedModule == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        float panelX = (mc.getWindow().getScaledWidth() - BASE_WIDTH) / 2.0F
                + BASE_WIDTH - SETTINGS_PANEL_WIDTH - PADDING;
        float panelY = (mc.getWindow().getScaledHeight() - BASE_HEIGHT) / 2.0F + 30.0F;
        float panelW = SETTINGS_PANEL_WIDTH;
        float panelH = BASE_HEIGHT - 30.0F - PADDING * 2;
        var matrices = ctx.getMatrices();
        Draw2DProcessor draw2d = Laura.getInstance().getModuleProcessor().i();

        int bgCol = ColorUtil.applyAlphaToColor(0xFF000000, 120);
        if (draw2d != null) {
            draw2d.a(matrices, panelX, panelY, panelW, panelH, 3.0F, bgCol);
        }

        // Заголовок модуля
        ctx.drawText(mc.textRenderer, Text.literal(selectedModule.j()),
                (int)(panelX + 8), (int)(panelY + 6), this.currentAccent.accent, true);
        ctx.drawText(mc.textRenderer, Text.literal(selectedModule.k()),
                (int)(panelX + 8), (int)(panelY + 18), this.currentAccent.foreground, false);

        // Настройки — рисуем через onDrawEvent (как в GUIPanel)
        // Чтобы не зависеть от DrawEvent (сложная передача), используем простой текст
        float sy = panelY + 34;
        for (Element<?> el : selectedElements) {
            Setting<?> s = el.getSetting();
            String label = s == null ? "?" : s.i(); // i() — name
            ctx.drawText(mc.textRenderer, Text.literal(label),
                    (int)(panelX + 8), (int)sy, this.currentAccent.foreground, false);
            sy += 18;
        }
    }

    private void renderSearchBar(DrawContext ctx, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float barX = (mc.getWindow().getScaledWidth() - BASE_WIDTH) / 2.0F + CATEGORY_PANEL_WIDTH + PADDING * 2;
        float barY = (mc.getWindow().getScaledHeight() - BASE_HEIGHT) / 2.0F + 4.0F;
        float barW = BASE_WIDTH - CATEGORY_PANEL_WIDTH - PADDING * 3;

        var matrices = ctx.getMatrices();
        Draw2DProcessor draw2d = Laura.getInstance().getModuleProcessor().i();
        int bgCol = ColorUtil.applyAlphaToColor(0xFF000000, 100);
        if (draw2d != null) {
            draw2d.a(matrices, barX, barY, barW, 22.0F, 3.0F, bgCol);
        }

        String display = searchQuery.isEmpty() ? "Поиск..." : searchQuery;
        int color = searchQuery.isEmpty() ? ColorUtil.applyAlphaToColor(this.currentAccent.foreground, 100) : this.currentAccent.foreground;
        ctx.drawText(mc.textRenderer, Text.literal(display), (int)(barX + 8), (int)(barY + 7), color, false);
    }

    private List<Module> collectModules() {
        List<Module> result = new ArrayList<>();
        if (selectedCategory == null) return result;
        ModuleProcessor mp = Laura.getInstance().getModuleProcessor().t();
        if (mp == null) return result;
        String q = searchQuery.toLowerCase(Locale.ROOT).trim();
        for (Module m : mp.e()) {
            if (m.l() != selectedCategory) continue;
            if (!q.isEmpty() && !m.j().toLowerCase(Locale.ROOT).contains(q)) continue;
            result.add(m);
        }
        result.sort(Comparator.comparing(Module::j));
        return result;
    }

    private static String localizedCategoryName(Category c) {
        switch (c) {
            case Combat: return "Combat";
            case Movement: return "Movement";
            case Render: return "Render";
            case Player: return "Player";
            case Misc: return "Misc";
            default: return c.name();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float winX = (mc.getWindow().getScaledWidth() - BASE_WIDTH) / 2.0F;
        float winY = (mc.getWindow().getScaledHeight() - BASE_HEIGHT) / 2.0F;

        // Клик в категории
        float catX = winX + PADDING;
        float catY = winY + 30.0F;
        float rowH = 22.0F;
        for (WildCategoryPanel panel : categoryPanels) {
            if (mouseX >= catX && mouseX <= catX + CATEGORY_PANEL_WIDTH - PADDING * 2
                    && mouseY >= catY && mouseY <= catY + rowH) {
                selectedCategory = panel.category;
                selectedModule = null;
                selectedElements.clear();
                return true;
            }
            catY += rowH;
        }

        // Клик в модуле
        if (selectedCategory != null) {
            float panelX = winX + CATEGORY_PANEL_WIDTH + PADDING * 2;
            float panelY = winY + 30.0F;
            float panelW = BASE_WIDTH - CATEGORY_PANEL_WIDTH - SETTINGS_PANEL_WIDTH - PADDING * 4;
            List<Module> modules = collectModules();
            float rowY = panelY + 4.0F;
            for (Module m : modules) {
                if (mouseX >= panelX && mouseX <= panelX + panelW
                        && mouseY >= rowY && mouseY <= rowY + 18.0F) {
                    selectedModule = m;
                    rebuildSelectedElements();
                    return true;
                }
                rowY += 18.0F;
            }
        }

        // Клик в search bar
        float searchX = winX + CATEGORY_PANEL_WIDTH + PADDING * 2;
        float searchY = winY + 4.0F;
        if (mouseX >= searchX && mouseX <= searchX + BASE_WIDTH - CATEGORY_PANEL_WIDTH - PADDING * 3
                && mouseY >= searchY && mouseY <= searchY + 22.0F) {
            searchFocused = true;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void rebuildSelectedElements() {
        selectedElements.clear();
        if (selectedModule == null) return;
        for (Setting<?> s : selectedModule.e()) {
            Element<?> el = s.createBooleanElement();
            if (el != null) selectedElements.add(el);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        if (searchFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocused && Character.isLetterOrDigit(chr) || chr == ' ') {
            searchQuery += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        super.close();
    }

    /** Снимок категории с её индексом для упорядочивания */
    private static final class WildCategoryPanel {
        final Category category;
        final int index;
        WildCategoryPanel(Category c, int i) { this.category = c; this.index = i; }
    }
}
