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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * WildClient-style ClickGUI для Laura — РЕАЛЬНАЯ копия wild дизайна.
 *
 * Прямой перевод с ru/wild/gui/screen/ClickGuiScreen.java +
 * ClickGuiRenderer + ModuleCategoryPanel + ModuleCategoryColumn +
 * ClickGuiBackdropRenderer из Wexside-1.21.8.
 *
 * Wild layout:
 *  - 5 колонок в один ряд по центру экрана
 *  - Каждая колонка = категория (Combat/Movement/Render/Player/Misc)
 *  - Внутри колонки: модули списком, настройки раскрываются по правому клику
 *  - Сверху по центру — поле поиска
 *  - Снизу по центру — переключатель тем (5 цветов)
 *
 * Все wild-зависимости заменены на Laura:
 *  - RoundedRectRenderer → Draw2DProcessor.a(MatrixStack, x, y, w, h, radius, color)
 *  - BlurStateManager → AnimationUtil / статические поля этого класса
 *  - ThemePalette → WildTheme enum (4 темы)
 *  - EasingFunctions → EasingList.h
 *  - NumericTransform (hit-test) → встроенные методы
 *  - FontRegistry → MinecraftClient.textRenderer
 */
public class ClickGuiScreen extends Screen {
    // === Размеры колонок (как в wild) ===
    private static final float COLUMN_WIDTH = 120.0F;
    private static final float COLUMN_GAP = 8.0F;
    private static final float COLUMN_HEADER_HEIGHT = 20.0F;
    private static final float MODULE_ROW_HEIGHT = 20.0F;
    private static final float SEARCH_HEIGHT = 17.0F;

    // === Анимации (замена BlurStateManager) ===
    private final AnimationUtil openAnimation = new AnimationUtil(); // mode (открытие)
    private final AnimationUtil closeAnimation = new AnimationUtil(); // close
    private final AnimationUtil searchBarAnimation = new AnimationUtil(); // handler
    private final AnimationUtil themePanelAnimation = new AnimationUtil(); // enabled

    // === Состояние ===
    private boolean searchActive = false;
    private String searchQuery = "";
    private Category activeCategory = Category.values()[0];
    private int expandedModuleHash = 0; // какой модуль раскрыт (по hashCode)

    // === Темы (замена ThemePalette) ===
    private enum WildTheme {
        WILD(0xFF5C95FF, "Wild"),
        CHERRY(0xFFFF547D, "Cherry"),
        MINT(0xFF00F5A0, "Mint"),
        SUN(0xFFFFB347, "Sun"),
        FOREST(0xFF50C878, "Forest");
        final int primary;
        final String name;
        WildTheme(int p, String n) { this.primary = p; this.name = n; }
    }
    private WildTheme currentTheme = WildTheme.WILD;

    public ClickGuiScreen() {
        super(Text.literal("WildGUI"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Продвигаем анимации
        this.openAnimation.a(true);
        this.openAnimation.a(0.0F, 1.0F, 0.4F, EasingList.h, delta);

        this.searchBarAnimation.a(this.searchActive ? 1.0F : 0.0F, 0.32F, 0.4F, EasingList.h, delta);

        float t = this.openAnimation.c();
        if (t <= 0.001F) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        Draw2DProcessor draw2d = Laura.getInstance().getModuleProcessor().i();

        // === 1. Фон-затемнение на весь экран (как у wild) ===
        int bgColor = ColorUtil.applyAlphaToColor(0xFF000000, (int)(140.0F * t));
        ctx.fill(0, 0, sw, sh, bgColor);

        if (draw2d != null) {
            // === 2. Контейнер всех 5 колонок (как у wild: 120*5 + 8*4 = 632px ширина) ===
            float totalWidth = Category.values().length * COLUMN_WIDTH
                    + (Category.values().length - 1) * COLUMN_GAP;
            float containerX = (sw - totalWidth) / 2.0F;
            float containerY = (sh - 240.0F) / 2.0F;
            float containerW = totalWidth;
            float containerH = 240.0F;

            // Фон контейнера — тёмный полупрозрачный
            int containerBg = ColorUtil.applyAlphaToColor(0xFF0A1430, (int)(220 * t));
            draw2d.a(ctx.getMatrices(), containerX, containerY, containerW, containerH, 8.0F, containerBg);

            // === 3. Заголовок каждой колонки ===
            int idx = 0;
            for (Category cat : Category.values()) {
                float colX = containerX + idx * (COLUMN_WIDTH + COLUMN_GAP);
                float colY = containerY;
                renderColumnHeader(draw2d, ctx, ctx.getMatrices(), colX, colY, COLUMN_WIDTH, cat, t);
                idx++;
            }

            // === 4. Модули в каждой колонке ===
            idx = 0;
            for (Category cat : Category.values()) {
                float colX = containerX + idx * (COLUMN_WIDTH + COLUMN_GAP);
                float colY = containerY + COLUMN_HEADER_HEIGHT + 4.0F;
                renderColumnModules(draw2d, ctx, colX, colY, COLUMN_WIDTH, containerH - COLUMN_HEADER_HEIGHT - 8.0F, cat, mouseX, mouseY, t);
                idx++;
            }

            // === 5. Поиск сверху по центру (как у wild) ===
            renderSearchBar(draw2d, ctx, mouseX, mouseY, t);

            // === 6. Переключатель тем снизу по центру (как у wild BackdropRenderer) ===
            renderThemeBar(draw2d, ctx, mouseX, mouseY, t);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderColumnHeader(Draw2DProcessor draw2d, DrawContext ctx, net.minecraft.client.util.math.MatrixStack matrices, float x, float y, float w, Category cat, float t) {
        int headerBg = ColorUtil.applyAlphaToColor(0xFF1A2050, (int)(220 * t));
        draw2d.a(matrices, x, y, w, COLUMN_HEADER_HEIGHT, 6.0F, headerBg);

        // Цветной кружочек слева (как в wild)
        int accentColor = getThemeAccent();
        draw2d.a(matrices, x + 8.0F, y + 7.5F, 5.0F, 5.0F, 4.0F, accentColor);

        // Текст категории
        MinecraftClient mc = MinecraftClient.getInstance();
        int textColor = ColorUtil.applyAlphaToColor(0xFFEFF5FF, (int)(255 * t));
        String label = cat.name().toUpperCase();
        ctx.drawText(mc.textRenderer, Text.literal(label), (int)(x + 18.0F), (int)(y + 6.5F), textColor, false);

        // Счётчик модулей справа (как в wild "X" индикатор)
        int count = countModules(cat);
        String countText = count + " mods";
        ctx.drawText(mc.textRenderer, Text.literal(countText), (int)(x + w - 30.0F), (int)(y + 6.5F),
                ColorUtil.applyAlphaToColor(0xFFEFF5FF, (int)(180 * t)), false);
    }

    private void renderColumnModules(Draw2DProcessor draw2d, DrawContext ctx, float x, float y, float w, float h, Category cat, int mouseX, int mouseY, float t) {
        List<Module> modules = collectModules(cat);
        float rowY = y + 2.0F;

        for (Module m : modules) {
            if (rowY + MODULE_ROW_HEIGHT > y + h) break; // не рисуем за пределами

            boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= rowY && mouseY <= rowY + MODULE_ROW_HEIGHT;
            boolean selected = m == selectedModule();
            boolean expanded = m.hashCode() == expandedModuleHash;

            // Фон строки модуля
            int rowBg;
            if (selected) {
                rowBg = ColorUtil.applyAlphaToColor(0xFF203050, (int)(200 * t));
            } else if (hover) {
                rowBg = ColorUtil.applyAlphaToColor(0xFF152040, (int)(180 * t));
            } else {
                rowBg = ColorUtil.applyAlphaToColor(0xFF0A1020, (int)(120 * t));
            }
            draw2d.a(ctx.getMatrices(), x + 2.0F, rowY, w - 4.0F, MODULE_ROW_HEIGHT - 2.0F, 4.0F, rowBg);

            // Иконка статуса (зелёный если включён, серый если выключен)
            int statusColor = m.m() ? getThemeAccent() : 0xFF505060;
            draw2d.a(ctx.getMatrices(), x + 6.0F, rowY + 7.0F, 6.0F, 6.0F, 3.0F,
                    ColorUtil.applyAlphaToColor(statusColor, (int)(255 * t)));

            // Имя модуля
            int nameColor = m.m() ? 0xFFFFFFFF : ColorUtil.applyAlphaToColor(0xFFD0D5E5, (int)(220 * t));
            ctx.drawText(MinecraftClient.getInstance().textRenderer, Text.literal(m.j()),
                    (int)(x + 18.0F), (int)(rowY + 6.5F), nameColor, false);

            // Бинд справа (если есть)
            int keyCode = m.p();
            if (keyCode != -1) {
                String bindName = glfwKeyName(keyCode);
                if (bindName != null) {
                    int bindW = mc().textRenderer.getWidth(bindName) + 6;
                    float bindX = x + w - bindW - 16.0F;
                    draw2d.a(ctx.getMatrices(), bindX, rowY + 4.0F, bindW, 12.0F, 3.0F,
                            ColorUtil.applyAlphaToColor(0xFF2A3050, (int)(200 * t)));
                    ctx.drawText(mc().textRenderer, Text.literal(bindName),
                            (int)(bindX + 3.0F), (int)(rowY + 6.5F),
                            ColorUtil.applyAlphaToColor(0xFFB0B5C5, (int)(220 * t)), false);
                }
            }

            // Индикатор раскрытия настроек
            int settingsCount = m.e().size();
            if (settingsCount > 0) {
                String expandIndicator = expanded ? "▼" : "▶";
                ctx.drawText(mc().textRenderer, Text.literal(expandIndicator),
                        (int)(x + w - 12.0F), (int)(rowY + 6.5F),
                        ColorUtil.applyAlphaToColor(0xFFFFFFFF, (int)(180 * t)), false);
            }

            // Если раскрыт — рисуем настройки ниже
            if (expanded) {
                float settingsY = rowY + MODULE_ROW_HEIGHT;
                int sIdx = 0;
                for (laura.setting.Setting<?> s : m.e()) {
                    String label = "  • " + s.i();
                    int sy = (int)settingsY + sIdx * 16;
                    ctx.drawText(mc().textRenderer, Text.literal(label),
                            (int)(x + 8.0F), sy,
                            ColorUtil.applyAlphaToColor(0xFFB0B5C5, (int)(200 * t)), false);
                    // Показываем текущее значение
                    String valueStr = String.valueOf(s.h());
                    ctx.drawText(mc().textRenderer, Text.literal(valueStr),
                            (int)(x + w - 30.0F), sy,
                            ColorUtil.applyAlphaToColor(getThemeAccent(), (int)(255 * t)), false);
                    sIdx++;
                }
                rowY += sIdx * 16;
            }

            rowY += MODULE_ROW_HEIGHT;
        }
    }

    private void renderSearchBar(Draw2DProcessor draw2d, DrawContext ctx, int mouseX, int mouseY, float t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        float searchY = 60.0F;
        float searchW = Math.min(220.0F, sw - 100.0F);
        float searchX = (sw - searchW) / 2.0F;

        int bgColor = ColorUtil.applyAlphaToColor(0xFF0A1020, (int)(200 * t));
        draw2d.a(ctx.getMatrices(), searchX, searchY, searchW, SEARCH_HEIGHT, 6.0F, bgColor);

        int borderColor = ColorUtil.applyAlphaToColor(0xFF203050, (int)(180 * t));
        draw2d.a(ctx.getMatrices(), searchX, searchY, searchW, SEARCH_HEIGHT, 6.0F, borderColor);

        String display = searchQuery.isEmpty() ? "🔍 Поиск..." : searchQuery;
        int txtColor = searchQuery.isEmpty()
                ? ColorUtil.applyAlphaToColor(0xFFB0B5C5, (int)(180 * t))
                : ColorUtil.applyAlphaToColor(0xFFFFFFFF, (int)(255 * t));
        ctx.drawText(mc.textRenderer, Text.literal(display),
                (int)(searchX + 8.0F), (int)(searchY + 4.5F), txtColor, false);
    }

    private void renderThemeBar(Draw2DProcessor draw2d, DrawContext ctx, int mouseX, int mouseY, float t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        WildTheme[] themes = WildTheme.values();

        float barY = sh - 32.0F;
        float circleGap = 18.0F;
        float barW = themes.length * circleGap;
        float barX = (sw - barW) / 2.0F;

        int barBg = ColorUtil.applyAlphaToColor(0xFF0A1020, (int)(220 * t));
        draw2d.a(ctx.getMatrices(), barX - 8.0F, barY - 5.0F, barW + 16.0F, 25.0F,
                new Vector4f(6.5F, 6.5F, 0.0F, 0.0F), barBg);

        for (int i = 0; i < themes.length; i++) {
            WildTheme theme = themes[i];
            float cx = barX + i * circleGap;
            // Большой кружок
            draw2d.a(ctx.getMatrices(), cx, barY, 9.25F, 9.25F, 10.0F, theme.primary);
            // Маленький индикатор (выбранная тема)
            if (theme == currentTheme) {
                draw2d.a(ctx.getMatrices(), cx + 4.5F, barY + 4.76F, 0.1F, 0.1F, 6.0F,
                        ColorUtil.applyAlphaToColor(0xFFFFFFFF, (int)(90 * t)));
            }
        }
    }

    // === Хелперы ===
    private MinecraftClient mc() { return MinecraftClient.getInstance(); }

    private int getThemeAccent() {
        return currentTheme.primary;
    }

    private int countModules(Category cat) {
        ModuleProcessor mp = Laura.getInstance().getModuleProcessor().t();
        if (mp == null) return 0;
        int count = 0;
        for (Module m : mp.e()) {
            if (m.l() == cat) count++;
        }
        return count;
    }

    private List<Module> collectModules(Category cat) {
        List<Module> result = new ArrayList<>();
        ModuleProcessor mp = Laura.getInstance().getModuleProcessor().t();
        if (mp == null) return result;
        String q = searchQuery.trim().toLowerCase();
        for (Module m : mp.e()) {
            if (m.l() != cat) continue;
            if (!q.isEmpty() && !m.j().toLowerCase().contains(q)) continue;
            result.add(m);
        }
        result.sort(Comparator.comparing(Module::j));
        return result;
    }

    private Module selectedModule() {
        ModuleProcessor mp = Laura.getInstance().getModuleProcessor().t();
        if (mp == null) return null;
        for (Module m : mp.e()) {
            if (m.m() && m.l() == activeCategory) return m;
        }
        return null;
    }

    private static String glfwKeyName(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RShift";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LShift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCtrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCtrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LAlt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RAlt";
            case GLFW.GLFW_KEY_MENU -> "Menu";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> "`";
            case GLFW.GLFW_KEY_BACKSPACE -> "Back";
            default -> null;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        // === Поиск ===
        float searchW = Math.min(220.0F, sw - 100.0F);
        float searchX = (sw - searchW) / 2.0F;
        float searchY = 60.0F;
        if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + SEARCH_HEIGHT) {
            searchActive = true;
            return true;
        }

        // === Клик в модуль (toggle / expand) ===
        float totalWidth = Category.values().length * COLUMN_WIDTH + (Category.values().length - 1) * COLUMN_GAP;
        float containerX = (sw - totalWidth) / 2.0F;
        float containerY = (sh - 240.0F) / 2.0F;
        float modulesY = containerY + COLUMN_HEADER_HEIGHT + 4.0F;

        int idx = 0;
        for (Category cat : Category.values()) {
            float colX = containerX + idx * (COLUMN_WIDTH + COLUMN_GAP);
            List<Module> modules = collectModules(cat);
            float rowY = modulesY + 2.0F;
            for (Module m : modules) {
                if (rowY + MODULE_ROW_HEIGHT > modulesY + 240 - COLUMN_HEADER_HEIGHT - 8.0F) break;
                if (mouseX >= colX && mouseX <= colX + COLUMN_WIDTH && mouseY >= rowY && mouseY <= rowY + MODULE_ROW_HEIGHT) {
                    activeCategory = cat;
                    if (button == 0) {
                        // ЛКМ — toggle
                        m.a();
                    } else if (button == 1) {
                        // ПКМ — развернуть/свернуть настройки
                        expandedModuleHash = (expandedModuleHash == m.hashCode()) ? 0 : m.hashCode();
                    }
                    return true;
                }
                rowY += MODULE_ROW_HEIGHT;
            }
            idx++;
        }

        // === Переключатель тем (снизу) ===
        WildTheme[] themes = WildTheme.values();
        float barY = sh - 32.0F;
        float circleGap = 18.0F;
        float barW = themes.length * circleGap;
        float barX = (sw - barW) / 2.0F;
        if (button == 0 && mouseY >= barY && mouseY <= barY + 10.0F) {
            for (int i = 0; i < themes.length; i++) {
                float cx = barX + i * circleGap;
                if (mouseX >= cx - 2 && mouseX <= cx + 12) {
                    currentTheme = themes[i];
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        if (searchActive && keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            searchActive = false;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchActive && (Character.isLetterOrDigit(chr) || chr == ' ')) {
            searchQuery += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}
