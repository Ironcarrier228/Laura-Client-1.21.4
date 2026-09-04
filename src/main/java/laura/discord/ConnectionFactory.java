package laura.discord;


import laura.lib.jsoup.Connection;

import java.io.IOException;

@FunctionalInterface
public interface ConnectionFactory {
    Connection create(String str) throws IOException;
}
