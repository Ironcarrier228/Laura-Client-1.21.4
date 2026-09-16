package laura.ui.screen;

import laura.core.Category;
import laura.core.Laura;
import laura.core.Module;
import laura.render.AnimationUtil;
import laura.render.ColorUtil;
import laura.render.Draw2DProcessor;
import laura.render.Fonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ModernClickGuiScreen extends Screen {
    private Category selectedCategory = Category.Combat;
    private final List<ModuleCard> moduleCards = new ArrayList<>();
    private float scrollOffset = 0.0f;
    private final AnimationUtil fadeAnimation = new AnimationUtil(300);
    
    public ModernClickGuiScreen() {
        super(Text.literal("Laura ClickGUI"));
    }

    @Override
    protected void init() {
        super.init();
        fadeAnimation.a(true);
        updateModuleCards();
    }

    private void updateModuleCards() {
        moduleCards.clear();
        List<Module> modules = Laura.getInstance().getModuleProcessor().a();
        for (Module module : modules) {
            if (module.i() == selectedCategory) {
                moduleCards.add(new ModuleCard(module));
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Background blur
        renderBackground(context, mouseX, mouseY, delta);
        
        float alpha = fadeAnimation.c();
        Draw2DProcessor draw = Laura.getInstance().getModuleProcessor().getDraw2DProcessor();
        
        if (draw == null) return;
        
        int width = this.width;
        int height = this.height;
        
        // Panel background
        float panelX = width / 2.0f - 400;
        float panelY = height / 2.0f - 300;
        float panelW = 800;
        float panelH = 600;
        
        int bg = ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(18, 20, 26, 255), alpha);
        draw.a(context.getMatrices(), panelX, panelY, panelW, panelH, 12.0f, bg);
        
        // Header
        renderHeader(draw, context, panelX, panelY, panelW, alpha);
        
        // Category tabs
        renderCategories(draw, context, panelX, panelY + 60, panelW, alpha, mouseX, mouseY);
        
        // Module cards
        renderModules(draw, context, panelX + 20, panelY + 120, panelW - 40, panelH - 140, alpha, mouseX, mouseY);
        
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderHeader(Draw2DProcessor draw, DrawContext context, float x, float y, float w, float alpha) {
        int textColor = ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(240, 240, 245, 255), alpha);
        Fonts.d.a(context.getMatrices(), "Laura Client", x + 20, y + 20, 12.0f, textColor, 0.0f);
        
        String version = "v1.21.4";
        Fonts.b.a(context.getMatrices(), version, x + w - 80, y + 22, 7.0f, 
            ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(150, 154, 164, 255), alpha), 0.0f);
    }

    private void renderCategories(Draw2DProcessor draw, DrawContext context, float x, float y, float w, float alpha, int mouseX, int mouseY) {
        Category[] categories = Category.values();
        float tabW = 120;
        float tabH = 35;
        float spacing = 10;
        float startX = x + (w - (tabW * categories.length + spacing * (categories.length - 1))) / 2.0f;
        
        for (int i = 0; i < categories.length; i++) {
            Category cat = categories[i];
            float tabX = startX + i * (tabW + spacing);
            boolean selected = cat == selectedCategory;
            boolean hovered = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= y && mouseY <= y + tabH;
            
            int bgColor = selected 
                ? ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(66, 135, 245, 255), alpha * 0.3f)
                : (hovered ? ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(255, 255, 255, 255), alpha * 0.05f) : 0);
            
            if (bgColor != 0) {
                draw.a(context.getMatrices(), tabX, y, tabW, tabH, 8.0f, bgColor);
            }
            
            int textColor = selected
                ? ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(66, 135, 245, 255), alpha)
                : ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(150, 154, 164, 255), alpha);
            
            String name = cat.a();
            float textW = Fonts.b.a(name, 8.0f);
            Fonts.b.a(context.getMatrices(), name, tabX + (tabW - textW) / 2.0f, y + 12, 8.0f, textColor, 0.0f);
        }
    }

    private void renderModules(Draw2DProcessor draw, DrawContext context, float x, float y, float w, float h, float alpha, int mouseX, int mouseY) {
        float cardW = 240;
        float cardH = 80;
        float spacing = 15;
        int columns = (int) ((w + spacing) / (cardW + spacing));
        
        int index = 0;
        for (ModuleCard card : moduleCards) {
            int col = index % columns;
            int row = index / columns;
            
            float cardX = x + col * (cardW + spacing);
            float cardY = y + row * (cardH + spacing) - scrollOffset;
            
            if (cardY + cardH < y || cardY > y + h) {
                index++;
                continue;
            }
            
            boolean hovered = mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH;
            card.render(draw, context, cardX, cardY, cardW, cardH, alpha, hovered);
            
            index++;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check category clicks
        float panelX = width / 2.0f - 400;
        float panelY = height / 2.0f - 300;
        float panelW = 800;
        float y = panelY + 60;
        
        Category[] categories = Category.values();
        float tabW = 120;
        float tabH = 35;
        float spacing = 10;
        float startX = panelX + (panelW - (tabW * categories.length + spacing * (categories.length - 1))) / 2.0f;
        
        for (int i = 0; i < categories.length; i++) {
            Category cat = categories[i];
            float tabX = startX + i * (tabW + spacing);
            
            if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= y && mouseY <= y + tabH) {
                selectedCategory = cat;
                updateModuleCards();
                return true;
            }
        }
        
        // Check module clicks
        float cardW = 240;
        float cardH = 80;
        float spacing2 = 15;
        float modulesX = panelX + 20;
        float modulesY = panelY + 120;
        float modulesW = panelW - 40;
        int columns = (int) ((modulesW + spacing2) / (cardW + spacing2));
        
        int index = 0;
        for (ModuleCard card : moduleCards) {
            int col = index % columns;
            int row = index / columns;
            
            float cardX = modulesX + col * (cardW + spacing2);
            float cardY = modulesY + row * (cardH + spacing2) - scrollOffset;
            
            if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH) {
                card.module.a();
                return true;
            }
            
            index++;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (float) verticalAmount * 20.0f;
        scrollOffset = Math.max(0, scrollOffset);
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        fadeAnimation.a(false);
        super.close();
    }

    private static class ModuleCard {
        private final Module module;
        private final AnimationUtil toggleAnimation = new AnimationUtil(200);
        
        public ModuleCard(Module module) {
            this.module = module;
            toggleAnimation.a(module.m());
        }
        
        public void render(Draw2DProcessor draw, DrawContext context, float x, float y, float w, float h, float alpha, boolean hovered) {
            toggleAnimation.a(module.m());
            float toggleAlpha = toggleAnimation.c();
            
            int bgColor = ColorUtil.applyAlphaToColor(
                ColorUtil.lerpColor(
                    ColorUtil.convertToARGB(25, 27, 33, 255),
                    ColorUtil.convertToARGB(66, 135, 245, 255),
                    toggleAlpha * 0.2f
                ),
                alpha
            );
            
            if (hovered) {
                bgColor = ColorUtil.applyAlphaToColor(
                    ColorUtil.lerpColor(bgColor, ColorUtil.convertToARGB(255, 255, 255, 255), 0.05f),
                    alpha
                );
            }
            
            draw.a(context.getMatrices(), x, y, w, h, 8.0f, bgColor);
            
            // Module name
            int textColor = ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(240, 240, 245, 255), alpha);
            Fonts.d.a(context.getMatrices(), module.j(), x + 12, y + 12, 9.0f, textColor, 0.0f);
            
            // Module description
            int descColor = ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(150, 154, 164, 255), alpha);
            Fonts.b.a(context.getMatrices(), module.k(), x + 12, y + 28, 6.5f, descColor, 0.0f);
            
            // Toggle indicator
            float indicatorSize = 8.0f;
            float indicatorX = x + w - indicatorSize - 12;
            float indicatorY = y + 12;
            int indicatorColor = ColorUtil.applyAlphaToColor(
                ColorUtil.lerpColor(
                    ColorUtil.convertToARGB(150, 154, 164, 255),
                    ColorUtil.convertToARGB(66, 135, 245, 255),
                    toggleAlpha
                ),
                alpha
            );
            draw.a(context.getMatrices(), indicatorX, indicatorY, indicatorSize, indicatorSize, indicatorSize / 2.0f, indicatorColor);
            
            // Keybind
            if (module.p() != -1) {
                String keybind = "[" + org.lwjgl.glfw.GLFW.glfwGetKeyName(module.p(), 0) + "]";
                Fonts.b.a(context.getMatrices(), keybind, x + 12, y + h - 20, 6.0f, descColor, 0.0f);
            }
        }
    }
}
