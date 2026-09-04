package laura.ui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import laura.config.ThemeInfo;
import laura.core.Laura;
import laura.core.Interface;
import laura.event.DrawEvent;
import laura.render.ColorUtil;
import laura.render.EasingList;
import laura.render.Fonts;
import laura.setting.BooleanSetting;
import laura.ui.element.DragInfo;
import laura.util.MathUtil;
import laura.util.ServerUtil;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import platform.inject.accessors.BossBarHudAccessor;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WatermarkWidget extends Widget implements Interface {

    private static final long ANIMATION_DURATION = 200L;
    private static final float ANIMATION_OFFSET = 8.0f;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private static final String BRAND = "Laura Client";
    private static final Identifier LOGO_ID = Identifier.of("lauraclient", "watermark_logo"); // фикс: конструктор приватный

    private static final String[] LOGO_PATHS = {"icon.png",  "laura/icon.png"};

    private final BooleanSetting sideDisplay;
    private final BooleanSetting showPing;
    private final BooleanSetting showFps;
    private final BooleanSetting showTime;
    private final BooleanSetting showServer;
    private final BooleanSetting animateDigits;

    private float smoothedFps;
    private float logoRatio = 1.0f;
    private boolean logoLoaded;

    private String lastFps = "";
    private String oldFps = "";
    private long fpsAnimationStart;

    private String lastTime = "";
    private String oldTime = "";
    private long timeAnimationStart;

    public WatermarkWidget() {
        super(new DragInfo("Инфо-панель", 0.0f, 0.0f, 0.0f, 0.0f));
        this.sideDisplay = new BooleanSetting("Боковое отображение", true);
        this.showPing = new BooleanSetting("Задержка игрока", true);
        this.showFps = new BooleanSetting("Частота кадров", true);
        this.showTime = new BooleanSetting("Текущее время", true);
        this.showServer = new BooleanSetting("Сервер", true);
        this.animateDigits = new BooleanSetting("Анимация цифр", true);
        j().setWidget(this);
        j().setDragStatus(2);
        a(this.sideDisplay, this.showPing, this.showFps, this.showTime, this.showServer, this.animateDigits);
        loadLogo();
    }

    private void loadLogo() {
        try {
            File file = null;
            for (String path : LOGO_PATHS) {
                File f = new File(path);
                if (f.isFile()) { file = f; break; }
            }
            if (file == null) {
                System.out.println("[Watermark] лого не найдено (" + String.join(", ", LOGO_PATHS) + "), рисую глиф");
                return;
            }
            NativeImage image = NativeImage.read(new FileInputStream(file));
            this.logoRatio = (float) image.getWidth() / (float) image.getHeight();
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            mc.execute(() -> mc.getTextureManager().registerTexture(LOGO_ID, texture));
            this.logoLoaded = true;
        } catch (Exception ex) {
            System.out.println("[Watermark] не удалось загрузить лого: " + ex);
        }
    }

    @Override
    public void a(DrawEvent event) {
        d().a(true);
        d().a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());

        this.smoothedFps = MathUtil.c(this.smoothedFps, mc.getCurrentFps(), 0.1f);

        long now = System.currentTimeMillis();
        String fps = String.valueOf((int) this.smoothedFps);
        String time = LocalTime.now().format(TIME_FORMAT);
        String ping = ServerUtil.d() + " ms";
        String server = getServerAddress();

        if (!fps.equals(this.lastFps)) {
            this.oldFps = this.lastFps;
            this.lastFps = fps;
            this.fpsAnimationStart = now;
        }
        if (!time.equals(this.lastTime)) {
            this.oldTime = this.lastTime;
            this.lastTime = time;
            this.timeAnimationStart = now;
        }
        float fpsProgress = Math.min(1.0f, (now - this.fpsAnimationStart) / (float) ANIMATION_DURATION);
        float timeProgress = Math.min(1.0f, (now - this.timeAnimationStart) / (float) ANIMATION_DURATION);
        boolean animate = this.animateDigits.c();

        List<Section> sections = new ArrayList<>();
        if (this.showPing.c()) sections.add(new Section("P", ping, null, "", 1.0f));
        if (this.showFps.c()) sections.add(new Section("q", fps, animate ? this.oldFps : null, " FPS", fpsProgress));
        if (this.showTime.c()) sections.add(new Section("T", time, animate ? this.oldTime : null, "", timeProgress));
        if (this.showServer.c()) sections.add(new Section("g", server, null, "", 1.0f));

        float iconSize = this.e - 0.5f;
        float startPadding = 5.0f;
        float sectionGap = 5.0f;
        float iconTextGap = 3.0f;

        float logoW, logoH;
        if (this.logoLoaded) {
            logoH = this.d - 5.0f;
            logoW = logoH * this.logoRatio;
        } else {
            float logoSize = this.e + 1.0f;
            logoW = Fonts.a.a("a", logoSize);
            logoH = logoSize;
        }

        float brandWidth = Fonts.e.a(BRAND, this.e);

        float totalWidth = startPadding;
        totalWidth += logoW + 3.0f;
        totalWidth += brandWidth + 4.0f + 1.0f + sectionGap;
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            if (i > 0) totalWidth += 1.0f + sectionGap;
            totalWidth += Fonts.a.a(s.icon, iconSize) + iconTextGap
                    + Fonts.e.a(s.text, this.e) + Fonts.e.a(s.suffix, this.e) + sectionGap;
        }

        float x = this.sideDisplay.c()
                ? 5.0f
                : (mc.getWindow().getScaledWidth() - totalWidth) / 2.0f;
        float bossOffset;
        if (this.sideDisplay.c()
                || ((BossBarHudAccessor) mc.inGameHud.getBossBarHud()).getBossBars().isEmpty()) {
            bossOffset = 0.0f;
        } else {
            int bossBars = ((BossBarHudAccessor) mc.inGameHud.getBossBarHud()).getBossBars().size() - 1;
            Objects.requireNonNull(mc.textRenderer);
            // фикс: явный float, чтобы не было целочисленного деления
            bossOffset = (12 + bossBars * (10 + 9) + 5)
                    * (float) mc.getWindow().calculateScaleFactor(mc.options.getGuiScale().getValue(), mc.forcesUnicodeFont())
                    / (float) mc.getWindow().calculateScaleFactor(2, mc.forcesUnicodeFont());
        }
        float y = 5.0f + bossOffset;

        j().setX(x);
        j().setY(y);
        j().setWidth(totalWidth);
        j().setHeight(this.d);

        int primaryColor = ColorUtil.applyAlphaToColor(
                Laura.getInstance().getModuleProcessor().o().a(ThemeInfo.PRIMARY).toIntColor(), 1.0f);
        int textColor = Laura.getInstance().getModuleProcessor().o().a(ThemeInfo.TEXT).toIntColor();

        a(event, x, y, totalWidth, this.d, true, 1.0f);

        float cursor = x + startPadding;
        float textY = y + ((this.d - Fonts.e.a(this.e)) / 2.0f) - 0.5f;

        // 1. лого: своя картинка (или глиф дельты, если файла нет)
        if (this.logoLoaded) {
            drawLogoTexture(event, cursor, y + (this.d - logoH) / 2.0f, logoW, logoH);
        } else {
            float logoSize = this.e + 1.0f;
            Fonts.a.a(event.h(), "a", cursor, y + ((this.d - logoSize) / 2.0f), logoSize, primaryColor);
        }
        cursor += logoW + 3.0f;

        // 2. бренд CollapseLoader
        Fonts.e.a(event.h(), BRAND, cursor, textY, this.e, primaryColor);
        cursor += brandWidth + 4.0f;
        a(event, cursor, y, this.d, 1.0f);
        cursor += 1.0f + sectionGap;

        // 3. секции
        for (Section s : sections) {
            Fonts.a.a(event.h(), s.icon, cursor, y + ((this.d - Fonts.a.a(iconSize)) / 2.0f), iconSize, primaryColor);
            float textX = cursor + Fonts.a.a(s.icon, iconSize) + iconTextGap;

            if (s.oldText != null) {
                drawAnimatedText(event, s.text, s.oldText, textX, textY, s.progress);
            } else {
                Fonts.e.a(event.h(), s.text, textX, textY, this.e, textColor);
            }

            float after = textX + Fonts.e.a(s.text, this.e);
            if (!s.suffix.isEmpty()) {
                Fonts.e.a(event.h(), s.suffix, after, textY, this.e, textColor);
                after += Fonts.e.a(s.suffix, this.e);
            }
            cursor = after + sectionGap;
        }

        super.a(event);
    }

    // ============ лого через текстуру (новый рендер-API 1.21.5+) ============

    private void drawLogoTexture(DrawEvent event, float x, float y, float w, float h) {
        Matrix4f matrix = event.h().peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, LOGO_ID); // bindTexture убрали, текстуру биндит это

        BufferBuilder buffer = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        buffer.vertex(matrix, x, y + h, 0.0f).texture(0.0f, 1.0f);
        buffer.vertex(matrix, x + w, y + h, 0.0f).texture(1.0f, 1.0f);
        buffer.vertex(matrix, x + w, y, 0.0f).texture(1.0f, 0.0f);
        buffer.vertex(matrix, x, y, 0.0f).texture(0.0f, 0.0f);

        BufferRenderer.drawWithGlobalProgram(buffer.end()); // .end() -> BuiltBuffer

        RenderSystem.disableBlend();
    }

    // ============ анимация цифр ============

    private void drawAnimatedText(DrawEvent event, String newText, String oldText,
                                  float x, float y, float progress) {
        int textColor = Laura.getInstance().getModuleProcessor().o().a(ThemeInfo.TEXT).toIntColor();

        if (oldText == null || oldText.isEmpty() || progress >= 1.0f) {
            Fonts.e.a(event.h(), newText, x, y, this.e, textColor);
            return;
        }

        float offsetX = x;
        int maxLen = Math.max(newText.length(), oldText.length());
        String paddedNew = padLeft(newText, maxLen);
        String paddedOld = padLeft(oldText, maxLen);

        for (int i = 0; i < paddedNew.length(); i++) {
            char newChar = paddedNew.charAt(i);
            char oldChar = paddedOld.charAt(i);
            if (newChar == ' ' && oldChar == ' ') continue;

            float charWidth = Fonts.e.a(String.valueOf(newChar != ' ' ? newChar : oldChar), this.e);
            boolean isNewDigit = Character.isDigit(newChar) || newChar == '.';
            boolean isOldDigit = Character.isDigit(oldChar) || oldChar == '.';

            if (newChar != oldChar || (!isNewDigit && !isOldDigit)) {
                if (newChar != ' ') {
                    Fonts.e.a(event.h(), String.valueOf(newChar), offsetX, y, this.e, textColor);
                }
            } else {
                float eased = easeOutCubic(progress);
                if (oldChar != ' ' && isOldDigit) {
                    float alpha = 1.0f - eased;
                    if (alpha > 0.0f) {
                        Fonts.e.a(event.h(), String.valueOf(oldChar), offsetX, y + eased * ANIMATION_OFFSET, this.e,
                                ColorUtil.applyAlphaToColor(textColor, alpha));
                    }
                }
                if (newChar != ' ' && isNewDigit) {
                    float alpha = eased;
                    if (alpha > 0.0f) {
                        Fonts.e.a(event.h(), String.valueOf(newChar), offsetX, y + (1.0f - eased) * -ANIMATION_OFFSET, this.e,
                                ColorUtil.applyAlphaToColor(textColor, alpha));
                    }
                }
            }
            if (newChar != ' ') offsetX += charWidth;
        }
    }

    private String padLeft(String text, int length) {
        return text.length() >= length ? text : " ".repeat(length - text.length()) + text;
    }

    private float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0 - t, 3);
    }

    private String getServerAddress() {
        if (mc.getCurrentServerEntry() != null) {
            return mc.getCurrentServerEntry().address;
        }
        return "localhost";
    }

    private record Section(String icon, String text, String oldText, String suffix, float progress) {}
}