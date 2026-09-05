package laura.module.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.ClickEvent;
import laura.event.DrawEvent;
import laura.render.ColorUtil;
import laura.render.Draw2DProcessor;
import laura.render.Font;
import laura.render.Fonts;
import laura.render.ScissorUtil;
import laura.setting.BindSetting;
import laura.setting.BooleanSetting;
import laura.setting.ButtonSetting;
import laura.setting.ColorSetting;
import laura.setting.ModeSetting;
import laura.setting.SliderSetting;
import laura.setting.StringSetting;
import laura.util.MathUtil;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@ModuleRegister(name = "Spotify HUD", description = "Виджет «Сейчас играет» для Spotify: обложка, трек, исполнитель, прогресс-бар и управление горячими клавишами", category = Category.Render)
public class SpotifyHUD extends Module {
    private static final String API_BASE = "https://api.spotify.com/v1/me/player";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final long TOKEN_REFRESH_MARGIN_MS = 30_000L;

    private static final String MODE_CORNER = "По углам";
    private static final String MODE_FREE = "Своя позиция";

    // Режим размещения: по углам (автоматически) или свободная позиция (перетаскивание мышью)
    private final ModeSetting positionMode = new ModeSetting("Режим позиции", MODE_CORNER, MODE_CORNER, MODE_FREE);
    private final ModeSetting corner = new ModeSetting("Угол", "Справа сверху", "Слева сверху", "Справа сверху", "Слева снизу", "Справа снизу");
    private final SliderSetting freeX = new SliderSetting("Позиция X (%)", 50.0f, 0.0f, 100.0f, 1.0f);
    private final SliderSetting freeY = new SliderSetting("Позиция Y (%)", 35.0f, 0.0f, 100.0f, 1.0f);
    private final SliderSetting scale = new SliderSetting("Масштаб", 1.0f, 0.7f, 1.6f, 0.05f);
    private final SliderSetting hideDelay = new SliderSetting("Скрывать через (сек)", 6.0f, 2.0f, 30.0f, 0.5f);
    private final SliderSetting pollInterval = new SliderSetting("Опрос Spotify (сек)", 2.5f, 1.0f, 10.0f, 0.5f);
    private final SliderSetting animSpeed = new SliderSetting("Скорость анимации", 12.0f, 4.0f, 24.0f, 0.5f);
    private final BooleanSetting pinned = new BooleanSetting("Закрепить", false);
    private final BooleanSetting showCover = new BooleanSetting("Обложка", true);
    private final BooleanSetting showTimes = new BooleanSetting("Время трека", true);
    private final ColorSetting backgroundColor = new ColorSetting("Цвет фона", Integer.valueOf(ColorUtil.convertToARGB(18, 20, 26, 255)));
    private final ColorSetting accentColor = new ColorSetting("Акцент", Integer.valueOf(ColorUtil.convertToARGB(30, 215, 96, 255)));
    private final ColorSetting textColor = new ColorSetting("Цвет текста", Integer.valueOf(ColorUtil.convertToARGB(240, 240, 245, 255)));
    private final ColorSetting subTextColor = new ColorSetting("Цвет подтекста", Integer.valueOf(ColorUtil.convertToARGB(150, 154, 164, 255)));
    private final StringSetting clientId = new StringSetting("Client ID", "");
    private final StringSetting clientSecret = new StringSetting("Client Secret", "");
    private final StringSetting refreshTokenSetting = new StringSetting("Refresh Token", "");
    private final ButtonSetting testConnection = new ButtonSetting("Проверить подключение", () -> forcePoll());
    private final BindSetting playPauseBind = new BindSetting("Play/Pause", -1).a(() -> controlPlayback("toggle"));
    private final BindSetting nextBind = new BindSetting("Следующий трек", -1).a(() -> controlPlayback("next"));
    private final BindSetting previousBind = new BindSetting("Предыдущий трек", -1).a(() -> controlPlayback("previous"));

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "laura-spotify-hud");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final AtomicReference<byte[]> pendingCover = new AtomicReference<>();

    private volatile Track track;
    private volatile State state = State.SETUP;

    private ScheduledFuture<?> pollTask;
    private String accessToken;
    private long accessTokenExpiresAt;
    private long backoffUntil;
    private long lastPollAt;
    private String errorMessage = "";

    private Identifier coverId;
    private int coverCounter;
    private float visibility;
    private long hideAt;
    private long hintUntil;
    private String shownTrackId;

    // Позиция виджета в свободном режиме (последний отрисованный прямоугольник для перетаскивания)
    private float lastX;
    private float lastY;
    private float lastW;
    private float lastH;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    public SpotifyHUD() {
        this.corner.a(() -> this.positionMode.c().equals(MODE_CORNER));
        this.freeX.a(() -> this.positionMode.c().equals(MODE_FREE));
        this.freeY.a(() -> this.positionMode.c().equals(MODE_FREE));
        a(this.positionMode, this.corner, this.freeX, this.freeY, this.scale, this.hideDelay, this.pollInterval,
                this.animSpeed, this.pinned, this.showCover, this.showTimes, this.backgroundColor, this.accentColor,
                this.textColor, this.subTextColor, this.clientId, this.clientSecret, this.refreshTokenSetting,
                this.testConnection, this.playPauseBind, this.nextBind, this.previousBind);
    }

    @Override
    public void b() {
        super.b();
        this.visibility = 0.0f;
        this.hideAt = 0L;
        this.hintUntil = System.currentTimeMillis() + 14_000L;
        this.shownTrackId = null;
        this.lastPollAt = 0L;
        this.errorMessage = "";
        synchronized (this.executor) {
            if (this.pollTask == null || this.pollTask.isCancelled() || this.pollTask.isDone()) {
                this.pollTask = this.executor.scheduleWithFixedDelay(this::pollSafely, 0L, 500L, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public void c() {
        super.c();
        synchronized (this.executor) {
            if (this.pollTask != null) {
                this.pollTask.cancel(false);
                this.pollTask = null;
            }
        }
        this.track = null;
        this.state = State.SETUP;
        this.dragging = false;
    }

    @EventTarget
    public void a(DrawEvent event) {
        try {
            if (!event.b() || mc.options.hudHidden) {
                return;
            }
            long now = System.currentTimeMillis();

            uploadPendingCover();

            Track current = this.track;
            boolean visible = isPanelVisible(current, now);
            this.visibility = MathUtil.c(this.visibility, visible ? 1.0f : 0.0f, this.animSpeed.c().floatValue());
            if (this.visibility <= 0.004f && !visible) {
                return;
            }
            float alpha = MathUtil.b(this.visibility, 0.0f, 1.0f);
            if (current != null && current.id != null && !current.id.equals(this.shownTrackId)) {
                this.shownTrackId = current.id;
                peek(now);
            }
            if (current != null && current.playing) {
                this.hideAt = now + (long) (this.hideDelay.c().floatValue() * 1000.0f);
            }

            float s = this.scale.c().floatValue();
            float width = 212.0f * s;
            float height = 58.0f * s;
            float margin = 8.0f * s;
            float screenW = (float) mc.getWindow().getScaledWidth();
            float screenH = (float) mc.getWindow().getScaledHeight();
            float x;
            float y;
            if (this.positionMode.c().equals(MODE_CORNER)) {
                float slide = (1.0f - easeOutCubic(alpha)) * (height + margin);
                String mode = this.corner.c();
                if (mode.contains("Справа")) {
                    x = screenW - width - margin;
                } else {
                    x = margin;
                }
                if (mode.contains("снизу")) {
                    y = screenH - height - margin + slide;
                } else {
                    y = margin - slide;
                }
            } else {
                float maxX = Math.max(0.0f, screenW - width);
                float maxY = Math.max(0.0f, screenH - height);
                x = (this.freeX.c().floatValue() / 100.0f) * maxX;
                y = (this.freeY.c().floatValue() / 100.0f) * maxY;
            }

            this.lastX = x;
            this.lastY = y;
            this.lastW = width;
            this.lastH = height;

            MatrixStack matrices = event.i().getMatrices();
            Draw2DProcessor draw = event.getDraw2DProcessor();
            drawPanel(draw, matrices, current, x, y, width, height, s, alpha, now);
        } catch (Exception exception) {
            // Отрисовка виджета не должна ломать интерфейс при любых ошибках данных
        }
    }

    // Перетаскивание виджета в свободном режиме (работает, когда открыт чат)
    @EventTarget
    public void a(ClickEvent event) {
        if (!this.positionMode.c().equals(MODE_FREE) || !(mc.currentScreen instanceof ChatScreen)) {
            return;
        }
        if (event.b() && event.h() == 0) {
            if (this.lastW > 0.0f && this.lastH > 0.0f
                    && MathUtil.a(event.getMouseX(), event.getMouseY(), this.lastX, this.lastY, this.lastW, this.lastH)) {
                this.dragging = true;
                this.dragOffsetX = event.getMouseX() - this.lastX;
                this.dragOffsetY = event.getMouseY() - this.lastY;
            }
        } else if (event.d() && this.dragging && event.h() == 0) {
            float nx = (float) (event.getMouseX() - this.dragOffsetX);
            float ny = (float) (event.getMouseY() - this.dragOffsetY);
            applyFreePosition(nx, ny);
        } else if (event.c() && event.h() == 0) {
            this.dragging = false;
        }
    }

    private void applyFreePosition(float px, float py) {
        float s = this.scale.c().floatValue();
        float width = 212.0f * s;
        float height = 58.0f * s;
        float maxX = Math.max(0.0f, (float) mc.getWindow().getScaledWidth() - width);
        float maxY = Math.max(0.0f, (float) mc.getWindow().getScaledHeight() - height);
        px = MathUtil.b(px, 0.0f, maxX);
        py = MathUtil.b(py, 0.0f, maxY);
        this.freeX.a(maxX > 0.0f ? (px / maxX) * 100.0f : 0.0f);
        this.freeY.a(maxY > 0.0f ? (py / maxY) * 100.0f : 0.0f);
    }

    private boolean isPanelVisible(Track current, long now) {
        if (this.pinned.c().booleanValue()) {
            return true;
        }
        // В свободном режиме при открытом чате показываем виджет, чтобы его можно было перетащить
        if (this.positionMode.c().equals(MODE_FREE) && mc.currentScreen instanceof ChatScreen) {
            return true;
        }
        if (this.state == State.AUTH_ERROR || this.state == State.OFFLINE) {
            return true;
        }
        if (this.state == State.SETUP) {
            return !hasCredentials() || now < this.hintUntil;
        }
        if (current == null) {
            return now < this.hintUntil;
        }
        if (current.playing) {
            return true;
        }
        return now < this.hideAt;
    }

    private void peek() {
        peek(System.currentTimeMillis());
    }

    private void peek(long now) {
        this.hideAt = now + (long) (this.hideDelay.c().floatValue() * 1000.0f);
    }

    private void forcePoll() {
        peek();
        this.lastPollAt = 0L;
        this.backoffUntil = 0L;
        this.executor.execute(this::pollSafely);
    }

    private void drawPanel(Draw2DProcessor draw, MatrixStack matrices, Track current, float x, float y,
            float width, float height, float s, float alpha, long now) {
        int bg = ColorUtil.applyAlphaToColor(this.backgroundColor.c().intValue(), alpha * 0.92f);
        int bgLight = ColorUtil.applyAlphaToColor(ColorUtil.b(this.backgroundColor.c().intValue(), 1.25f), alpha * 0.92f);
        draw.a(matrices, x, y, width, height, 6.0f * s, bg, bg, bgLight, bgLight);

        float pad = 9.0f * s;
        float coverSize = 40.0f * s;
        boolean hasImage = this.showCover.c().booleanValue() && this.coverId != null && current != null;
        if (this.showCover.c().booleanValue()) {
            if (hasImage) {
                draw.a(matrices, this.coverId, x + pad, y + pad, coverSize, coverSize, 5.0f * s,
                        ColorUtil.applyAlphaToColor(-1, alpha));
            } else {
                drawPlaceholder(draw, matrices, x + pad, y + pad, coverSize, s, alpha);
            }
        }

        float textX = this.showCover.c().booleanValue() ? (x + pad + coverSize + 8.0f * s) : (x + pad + 2.0f * s);
        float textW = (x + width) - pad - textX;
        int text = ColorUtil.applyAlphaToColor(this.textColor.c().intValue(), alpha);
        int sub = ColorUtil.applyAlphaToColor(this.subTextColor.c().intValue(), alpha);
        int accent = ColorUtil.applyAlphaToColor(this.accentColor.c().intValue(), alpha);

        drawBadge(draw, matrices, x, y, width, pad, s, sub, accent);

        State panelState = this.state;
        String title;
        String artist;
        float fraction = 0.0f;
        long progressMs = 0L;
        long durationMs = 0L;
        if (panelState == State.TRACK && current != null) {
            title = current.title;
            artist = current.artist;
            progressMs = estimateProgress(current, now);
            durationMs = Math.max(1L, current.durationMs);
            fraction = MathUtil.b(((float) progressMs) / ((float) durationMs), 0.0f, 1.0f);
        } else if (panelState == State.AUTH_ERROR) {
            title = "Ошибка авторизации";
            artist = this.errorMessage;
        } else if (panelState == State.OFFLINE) {
            title = "Нет соединения";
            artist = this.errorMessage;
        } else if (panelState == State.IDLE) {
            title = "Ничего не играет";
            artist = "Запустите музыку в Spotify";
        } else if (!hasCredentials()) {
            title = "Spotify не подключён";
            artist = "Укажите Client ID, Secret и Refresh Token";
        } else {
            title = "Подключение к Spotify...";
            artist = "Идёт запрос к плееру";
        }

        float titleSize = 7.4f * s;
        float titleY = y + pad + 3.0f * s;
        drawMarquee(Fonts.d, matrices, title, textX, titleY, titleSize, textW, text, 0.35f, now);

        float artistSize = 6.4f * s;
        float artistY = titleY + titleSize + 4.0f * s;
        drawMarquee(Fonts.b, matrices, artist, textX, artistY, artistSize, textW, sub, 0.3f, now);

        float barHeight = 2.6f * s;
        float barY = (y + height) - pad - barHeight;
        int barBg = ColorUtil.applyAlphaToColor(-1, alpha * 0.14f);
        draw.a(matrices, textX, barY, textW, barHeight, barHeight * 0.5f, barBg);
        if (fraction > 0.0f) {
            float fillW = Math.max(barHeight, textW * fraction);
            int accentLight = ColorUtil.applyAlphaToColor(ColorUtil.b(this.accentColor.c().intValue(), 1.3f), alpha);
            draw.a(matrices, textX, barY, fillW, barHeight, barHeight * 0.5f, accent, accentLight, accent, accentLight);
        }
        if (this.showTimes.c().booleanValue() && panelState == State.TRACK && current != null) {
            float timeSize = 4.8f * s;
            float timeY = barY - timeSize - 2.0f * s;
            String elapsed = formatTime(progressMs);
            String total = formatTime(durationMs);
            Fonts.b.a(matrices, elapsed, textX, timeY, timeSize, sub, 0.3f);
            float totalW = Fonts.b.a(total, timeSize);
            Fonts.b.a(matrices, total, (textX + textW) - totalW, timeY, timeSize, sub, 0.3f);
        }
    }

    private void drawBadge(Draw2DProcessor draw, MatrixStack matrices, float x, float y, float width, float pad,
            float s, int sub, int accent) {
        String badge = "SPOTIFY";
        float badgeSize = 4.6f * s;
        float badgeW = Fonts.b.a(badge, badgeSize);
        float dot = 3.0f * s;
        float badgeX = (x + width) - pad - badgeW;
        float badgeY = y + pad + 0.5f * s;
        Fonts.b.a(matrices, badge, badgeX, badgeY, badgeSize, sub, 0.3f);
        draw.a(matrices, badgeX - dot - 3.0f * s, badgeY + 0.5f * s, dot, dot, dot * 0.5f, accent);
    }

    private void drawPlaceholder(Draw2DProcessor draw, MatrixStack matrices, float x, float y, float size, float s,
            float alpha) {
        int dark = ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(10, 12, 16, 255), alpha * 0.9f);
        draw.a(matrices, x, y, size, size, 5.0f * s, dark);
        int note = ColorUtil.applyAlphaToColor(this.accentColor.c().intValue(), alpha * 0.9f);
        float cx = x + (size / 2.0f);
        float cy = y + (size / 2.0f);
        float head = 6.0f * s;
        draw.a(matrices, cx - 6.5f * s, cy + 5.0f * s, head, head * 0.8f, head * 0.4f, note);
        draw.a(matrices, (cx - 6.5f * s) + head - 1.4f * s, cy - 6.0f * s, 1.6f * s, 12.0f * s, 0.8f * s, note);
        draw.a(matrices, (cx - 6.5f * s) + head - 1.4f * s, cy - 6.0f * s, 6.0f * s, 2.6f * s, 1.2f * s, note);
    }

    private void drawMarquee(Font font, MatrixStack matrices, String text, float x, float y, float size, float maxW,
            int color, float thickness, long now) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float full = font.a(text, size);
        if (full <= maxW) {
            font.a(matrices, text, x, y, size, color, thickness);
            return;
        }
        float span = full - maxW;
        float pauseSec = 1.5f;
        float speed = 26.0f;
        double period = (2.0f * pauseSec) + ((span + 6.0f) / speed);
        double t = (now % (long) (period * 1000.0d)) / 1000.0d;
        float offset;
        if (t < pauseSec) {
            offset = 0.0f;
        } else if (t > period - pauseSec) {
            offset = span + 6.0f;
        } else {
            offset = (float) ((t - pauseSec) * speed);
        }
        ScissorUtil.a(matrices, x - 1.0f, y - 1.5f, maxW + 2.0f, size + 3.0f);
        font.a(matrices, text, x - offset, y, size, color, thickness);
        ScissorUtil.a(matrices);
    }

    private long estimateProgress(Track current, long now) {
        long progress = current.progressMs;
        if (current.playing) {
            progress += now - current.fetchedAtMs;
        }
        return Math.min(Math.max(0L, progress), Math.max(1L, current.durationMs));
    }

    private String formatTime(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        return (seconds / 60L) + ":" + String.format("%02d", Long.valueOf(seconds % 60L));
    }

    private float easeOutCubic(float value) {
        float inverted = 1.0f - value;
        return 1.0f - (inverted * inverted * inverted);
    }

    private void uploadPendingCover() {
        byte[] bytes = this.pendingCover.getAndSet(null);
        if (bytes == null) {
            return;
        }
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            Identifier next = Identifier.of("laura", "spotify/cover_" + (this.coverCounter++));
            mc.getTextureManager().registerTexture(next, texture);
            if (this.coverId != null) {
                mc.getTextureManager().destroyTexture(this.coverId);
            }
            this.coverId = next;
        } catch (Exception exception) {
            // keep previous cover on malformed image data
        }
    }

    private void pollSafely() {
        try {
            long now = System.currentTimeMillis();
            if (now < this.backoffUntil || (now - this.lastPollAt) < (long) (this.pollInterval.c().floatValue() * 1000.0f)) {
                return;
            }
            if (!hasCredentials()) {
                this.state = State.SETUP;
                this.track = null;
                this.errorMessage = "";
                return;
            }
            this.lastPollAt = now;
            pollNow();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            this.state = State.OFFLINE;
            this.errorMessage = "Не удалось связаться с Spotify. Проверьте интернет.";
            this.backoffUntil = System.currentTimeMillis() + 10_000L;
        }
    }

    private void pollNow() throws Exception {
        long now = System.currentTimeMillis();
        if (!refreshAccessToken(false)) {
            fail("Неверные данные Spotify. Проверьте Client ID, Secret и Refresh Token (HTTP " + this.lastTokenStatus + ")");
            return;
        }
        HttpResponse<String> response = sendPlayerRequest("GET", "/currently-playing", null);
        if (response.statusCode() == 401 && refreshAccessToken(true)) {
            response = sendPlayerRequest("GET", "/currently-playing", null);
        }
        if (response.statusCode() == 429) {
            applyRateLimit(response);
            return;
        }
        if (response.statusCode() == 204 || response.statusCode() == 404) {
            this.state = State.IDLE;
            this.track = null;
            this.errorMessage = "";
            return;
        }
        if (response.statusCode() == 403) {
            fail("Spotify запретил доступ (403). Добавьте в приложение scopes: user-read-playback-state, user-modify-playback-state");
            return;
        }
        if (response.statusCode() != 200) {
            fail("Ошибка Spotify: HTTP " + response.statusCode());
            return;
        }
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!root.has("item") || !root.get("item").isJsonObject()) {
            this.state = State.IDLE;
            this.track = null;
            this.errorMessage = "";
            return;
        }
        JsonObject item = root.getAsJsonObject("item");
        String id = optString(item, "id");
        String title = optString(item, "name");
        String artist = readArtists(item);
        long durationMs = optLong(item, "duration_ms");
        long progressMs = optLong(root, "progress_ms");
        boolean playing = root.has("is_playing") && !root.get("is_playing").isJsonNull()
                && root.get("is_playing").getAsBoolean();
        String previousId = this.track != null ? this.track.id : null;
        String coverUrl = readCoverUrl(item);
        this.track = new Track(id, title, artist, durationMs, progressMs, playing, System.currentTimeMillis());
        this.state = State.TRACK;
        this.errorMessage = "";
        peek(now);
        if (id != null && !id.equals(previousId) && coverUrl != null) {
            downloadCover(coverUrl);
        }
    }

    private void fail(String message) {
        this.state = State.AUTH_ERROR;
        this.track = null;
        this.errorMessage = message;
        this.backoffUntil = System.currentTimeMillis() + 15_000L;
    }

    private void controlPlayback(String action) {
        peek();
        this.executor.execute(() -> {
            try {
                if (!hasCredentials() || !refreshAccessToken(false)) {
                    return;
                }
                String method;
                String path;
                switch (action) {
                    case "toggle":
                        Track current = this.track;
                        boolean playing = current != null && current.playing;
                        method = "PUT";
                        path = playing ? "/pause" : "/play";
                        break;
                    case "next":
                        method = "POST";
                        path = "/next";
                        break;
                    case "previous":
                        method = "POST";
                        path = "/previous";
                        break;
                    default:
                        return;
                }
                HttpResponse<String> response = sendPlayerRequest(method, path, "");
                if (response.statusCode() == 401 && refreshAccessToken(true)) {
                    sendPlayerRequest(method, path, "");
                }
                Thread.sleep(400L);
                this.lastPollAt = 0L;
                pollNow();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                // control commands are best-effort
            }
        });
    }

    private boolean hasCredentials() {
        return !this.clientId.c().isBlank() && !this.clientSecret.c().isBlank() && !this.refreshTokenSetting.c().isBlank();
    }

    private boolean refreshAccessToken(boolean force) throws Exception {
        long now = System.currentTimeMillis();
        if (!force && this.accessToken != null && now < this.accessTokenExpiresAt) {
            return true;
        }
        String credentials = this.clientId.c() + ":" + this.clientSecret.c();
        String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        String body = "grant_type=refresh_token&refresh_token="
                + URLEncoder.encode(this.refreshTokenSetting.c(), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        this.lastTokenStatus = response.statusCode();
        if (response.statusCode() != 200) {
            this.accessToken = null;
            this.errorMessage = describeTokenError(response.statusCode());
            return false;
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!json.has("access_token")) {
            this.errorMessage = "Spotify вернул некорректный ответ (нет access_token)";
            return false;
        }
        this.accessToken = json.get("access_token").getAsString();
        long expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600L;
        this.accessTokenExpiresAt = now + (expiresIn * 1000L) - TOKEN_REFRESH_MARGIN_MS;
        return true;
    }

    private String describeTokenError(int status) {
        switch (status) {
            case 400:
                return "Spotify не принял запрос (400). Проверьте Client ID/Secret/Refresh Token";
            case 401:
                return "Spotify отклонил токен (401). Неверный Client Secret или Refresh Token";
            case 403:
                return "Spotify запретил доступ (403). Проверьте scopes в приложении";
            case 404:
                return "Приложение Spotify не найдено (404). Проверьте Client ID";
            case 429:
                return "Слишком много запросов к Spotify (429). Подождите немного";
            default:
                return "Ошибка авторизации Spotify (HTTP " + status + ")";
        }
    }

    private HttpResponse<String> sendPlayerRequest(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(API_BASE + path))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + this.accessToken);
        if ("GET".equals(method)) {
            builder.GET();
        } else if ("PUT".equals(method)) {
            builder.PUT(body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
        } else {
            builder.POST(body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
        }
        return this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void applyRateLimit(HttpResponse<?> response) {
        long retryAfterSec = response.headers().firstValueAsLong("Retry-After").orElse(5L);
        this.backoffUntil = System.currentTimeMillis() + (Math.max(1L, retryAfterSec) * 1000L);
    }

    private void downloadCover(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200 && response.body().length > 0) {
                this.pendingCover.set(response.body());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            // cover is optional, ignore download errors
        }
    }

    private String readArtists(JsonObject item) {
        if (!item.has("artists") || !item.get("artists").isJsonArray()) {
            return "";
        }
        JsonArray artists = item.getAsJsonArray("artists");
        StringBuilder builder = new StringBuilder();
        for (JsonElement element : artists) {
            if (!element.isJsonObject()) {
                continue;
            }
            String name = optString(element.getAsJsonObject(), "name");
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(name);
        }
        return builder.toString();
    }

    private String readCoverUrl(JsonObject item) {
        if (!item.has("album") || !item.get("album").isJsonObject()) {
            return null;
        }
        JsonObject album = item.getAsJsonObject("album");
        if (!album.has("images") || !album.get("images").isJsonArray()) {
            return null;
        }
        JsonArray images = album.getAsJsonArray("images");
        String best = null;
        int bestHeight = Integer.MAX_VALUE;
        for (JsonElement element : images) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject image = element.getAsJsonObject();
            String url = optString(image, "url");
            int height = (int) optLong(image, "height");
            if (url == null || height <= 0) {
                continue;
            }
            if (best == null || Math.abs(height - 300) < Math.abs(bestHeight - 300)) {
                best = url;
                bestHeight = height;
            }
        }
        return best;
    }

    private String optString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    private long optLong(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : 0L;
    }

    private int lastTokenStatus;

    private enum State {
        SETUP,
        AUTH_ERROR,
        OFFLINE,
        IDLE,
        TRACK
    }

    private static final class Track {
        private final String id;
        private final String title;
        private final String artist;
        private final long durationMs;
        private final long progressMs;
        private final boolean playing;
        private final long fetchedAtMs;

        private Track(String id, String title, String artist, long durationMs, long progressMs, boolean playing,
                long fetchedAtMs) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.durationMs = durationMs;
            this.progressMs = progressMs;
            this.playing = playing;
            this.fetchedAtMs = fetchedAtMs;
        }
    }
}
