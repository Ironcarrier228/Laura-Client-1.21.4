package laura.network;

import laura.core.DiscordUser;
import laura.discord.DiscordBuild;
import laura.discord.FailureInfo;

public interface ConnectionState {

    final class d implements ConnectionState {
    }

    final class c implements ConnectionState {
    }

    record b(DiscordUser a, DiscordBuild b) implements ConnectionState {
    }

    record f(int a, FailureInfo b) implements ConnectionState {
    }

    record e(FailureInfo a) implements ConnectionState {
    }

    final class a implements ConnectionState {
    }
}
