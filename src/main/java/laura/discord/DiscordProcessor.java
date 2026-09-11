package laura.discord;


import laura.config.BaseProcessor;
import laura.core.Laura;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordProcessor extends BaseProcessor {
    // ВАЖНО: Замени на свой Discord Application Client ID
    // Создай приложение на https://discord.com/developers/applications
    // Включи Rich Presence, загрузи ассеты если нужно, скопируй Application ID
    // Если оставишь дефолтный ID - убедись что такое приложение существует, иначе будет ошибка Invalid client ID
    // Можно передать свой ID через JVM аргумент -Dlaura.discord.clientId=YOUR_ID или env LAURA_DISCORD_CLIENT_ID
    private static final long DEFAULT_CLIENT_ID = 1400721859848298640L; // Пример - замени на свой
    private static final long CLIENT_ID = getClientId();

    private DiscordIPC b;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Laura-DiscordRPC-Retry");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean closed = false;

    private static long getClientId() {
        try {
            String prop = System.getProperty("laura.discord.clientId");
            if (prop != null && !prop.isBlank()) {
                long id = Long.parseLong(prop.trim());
                System.out.println("[DiscordRPC] Using clientId from system property: " + id);
                return id;
            }
        } catch (Exception e) {
            System.err.println("[DiscordRPC] Failed to parse clientId from system property: " + e.getMessage());
        }
        try {
            String env = System.getenv("LAURA_DISCORD_CLIENT_ID");
            if (env != null && !env.isBlank()) {
                long id = Long.parseLong(env.trim());
                System.out.println("[DiscordRPC] Using clientId from env: " + id);
                return id;
            }
        } catch (Exception e) {
            System.err.println("[DiscordRPC] Failed to parse clientId from env: " + e.getMessage());
        }
        return DEFAULT_CLIENT_ID;
    }

    @Override
    public void setup() {
        this.closed = false;
        System.out.println("[DiscordRPC] Initializing with clientId=" + CLIENT_ID);
        System.out.println("[DiscordRPC] If you see 'Invalid client ID' error, create your own Discord app and set ID via -Dlaura.discord.clientId=YOUR_ID");
        try {
            DiscordIPCConfig config = DiscordIPCConfig.a()
                    .clientId(CLIENT_ID)
                    .reconnect(true)
                    .maxReconnectAttempts(0) // 0 = бесконечно
                    .b(1000L) // reconnectBaseDelayMs
                    .c(30000L) // reconnectMaxDelayMs
                    .d(10000L) // commandTimeoutMs
                    .build();
            this.b = DiscordIPC.a(config);
            connectAsync();
        } catch (Exception e) {
            System.err.println("[DiscordRPC] Failed to init IPC: " + e.getMessage());
            e.printStackTrace();
            scheduleRetry();
        }
    }

    private void connectAsync() {
        if (this.closed) return;
        if (this.b == null) {
            System.err.println("[DiscordRPC] IPC is null in connectAsync");
            return;
        }
        try {
            System.out.println("[DiscordRPC] Attempting to connect to Discord...");
            this.b.b().whenComplete(this::a);
        } catch (Exception e) {
            System.err.println("[DiscordRPC] connectAsync failed: " + e.getMessage());
            e.printStackTrace();
            scheduleRetry();
        }
    }

    private void scheduleRetry() {
        if (this.closed) return;
        System.out.println("[DiscordRPC] Scheduling retry in 15 seconds... (is Discord running?)");
        try {
            scheduler.schedule(this::connectAsync, 15, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("[DiscordRPC] Failed to schedule retry: " + e.getMessage());
        }
    }

    @Override
    public void unSetup() {
        this.closed = true;
        try {
            scheduler.shutdownNow();
        } catch (Exception ignored) {
        }
        try {
            if (this.b != null) {
                this.b.close();
                System.out.println("[DiscordRPC] Closed");
            }
        } catch (Exception e) {
            System.err.println("[DiscordRPC] Error during close: " + e.getMessage());
        }
        this.b = null;
    }

    public DiscordIPC a() {
        return this.b;
    }

    public void a(Void result, Throwable ex) {
        if (ex != null) {
            System.err.println("[DiscordRPC] Connection failed: " + ex.getMessage());
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            cause.printStackTrace();
            if (cause instanceof NoDiscordClientException) {
                System.err.println("[DiscordRPC] Discord client not found. Make sure Discord is running and RPC is enabled.");
            }
            if (cause.getMessage() != null && cause.getMessage().contains("Invalid client ID")) {
                System.err.println("[DiscordRPC] Invalid client ID! Create your own Discord application at https://discord.com/developers/applications and set client ID via -Dlaura.discord.clientId=YOUR_ID");
            }
            scheduleRetry();
            return;
        }
        System.out.println("[DiscordRPC] Connected successfully!");
        try {
            updateActivity();
        } catch (Exception e) {
            System.err.println("[DiscordRPC] Failed to set activity after connect: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateActivity() throws IOException {
        if (this.b == null) {
            System.err.println("[DiscordRPC] IPC is null, cannot set activity");
            return;
        }
        String username = "Unknown";
        try {
            if (Laura.getInstance() != null && Laura.getInstance().g() != null) {
                String u = Laura.getInstance().g().username();
                if (u != null && !u.isBlank()) {
                    username = u;
                }
            }
        } catch (Exception ignored) {
        }

        String buildType = "public";
        try {
            if (Laura.getInstance() != null && Laura.getInstance().c() != null) {
                buildType = "development";
            }
        } catch (Exception ignored) {
        }

        // Фикс оригинального бага: было два вызова largeImage, первый с пустым ключом ""
        // Теперь один корректный вызов. Discord поддерживает https URL как ключ если включена опция external images,
        // иначе нужно загрузить ассет в портале разработчика и использовать его имя.
        Activity activity = new Activity.a()
                .type(ActivityType.PLAYING)
                .b("username: " + username) // details
                .state("build: " + buildType) // state
                .largeImage("https://i.imgur.com/E6dkFRc.jpeg", "Laura Client | https://github.com/Ironcarrier228/Laura-Client")
                .startAt(System.currentTimeMillis() / 1000L)
                .c("Новости", "https://github.com/Ironcarrier228/Laura-Client")
                .build();

        System.out.println("[DiscordRPC] Setting activity: " + activity.j());
        this.b.a(activity);
        System.out.println("[DiscordRPC] Activity set successfully");
    }

    public void update() {
        if (this.b != null) {
            try {
                if (this.b.d()) {
                    updateActivity();
                } else {
                    System.out.println("[DiscordRPC] Not connected, trying to reconnect...");
                    connectAsync();
                }
            } catch (Exception e) {
                System.err.println("[DiscordRPC] update() failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
