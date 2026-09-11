package laura.discord;

import laura.config.BaseProcessor;
import laura.core.Laura;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Module-controlled RPC. All connection and activity commands run off the render thread. */
public class DiscordProcessor extends BaseProcessor {
    private static final long DEFAULT_CLIENT_ID = 1400721859848298640L;
    private volatile DiscordIPC ipc;
    private ScheduledExecutorService scheduler;

    public static long getClientId() {
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
        // DiscordRPC is started only by its module, not unconditionally at startup.
    }

    public synchronized void start(long clientId) {
        if (clientId <= 0) {
            throw new IllegalArgumentException("Client ID must be positive");
        }
        unSetup();
        DiscordIPC session = DiscordIPC.a(DiscordIPCConfig.a()
                .clientId(clientId).reconnect(false).d(10000L).build());
        ipc = session;
        long startedAt = System.currentTimeMillis() / 1000L;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "Laura-DiscordRPC");
            thread.setDaemon(true);
            return thread;
        });
        // Also retries if Discord was closed or was started after Minecraft.
        scheduler.scheduleWithFixedDelay(() -> update(session, startedAt), 0, 15, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void unSetup() {
        DiscordIPC previous = ipc;
        ipc = null;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (previous != null) {
            previous.close();
        }
    }

    public DiscordIPC a() {
        return ipc;
    }

    private void update(DiscordIPC session, long startedAt) {
        if (ipc != session) return;
        try {
            if (!session.d()) session.a();
            if (ipc != session) return;
            updateActivity(session, startedAt);
        } catch (Exception ex) {
            if (ipc == session) {
                System.err.println("[DiscordRPC] Cannot update activity; retrying in 15 seconds: " + ex.getMessage());
            }
        }
    }

    private void updateActivity(DiscordIPC session, long startedAt) throws IOException {
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
                .startAt(startedAt)
                .c("Новости", "https://github.com/Ironcarrier228/Laura-Client")
                .build();

        session.a(activity);
    }
}
