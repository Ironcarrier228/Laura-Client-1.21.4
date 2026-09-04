package laura.discord;


import laura.config.BaseProcessor;
import laura.core.Laura;

import java.io.IOException;

public class DiscordProcessor extends BaseProcessor {
    private DiscordIPC b;

    @Override

    public void setup() {
    }

    @Override
    public void unSetup() {
    }

    public DiscordIPC a() {
        return this.b;
    }

    public void a(Void result, Throwable ex) {
        if (ex == null) {
            try {
                this.b.a(new Activity.a().type(ActivityType.PLAYING).b("username: " + Laura.getInstance().g().username()).state("build: " + (Laura.getInstance().c() != null ? "development" : "public")).largeImage("", "https://github.com/Ironcarrier228/Laura-Client").startAt(System.currentTimeMillis() / 1000).largeImage("https://i.imgur.com/E6dkFRc.jpeg", "https://github.com/Ironcarrier228/Laura-Client").c("Новости", "https://github.com/Ironcarrier228/Laura-Client").build());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
