package laura.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Метаданные текущей сборки: тип (dev/public), дата окончания (expire), версия.
 *
 * <p>Значения записываются в {@code assets/laura/build.json} на этапе сборки
 * (см. build.gradle, task processResources). Скрипты {@code build.sh} /
 * {@code build.bat} позволяют выбирать тип сборки:
 * <ul>
 *   <li><b>dev</b> — клиент не просрочен (дата окончания автоматически 01.01.2099);</li>
 *   <li><b>public</b> — дату окончания вводит сам сборщик.</li>
 * </ul>
 *
 * <p>Приоритет: системная property -&gt; переменная окружения -&gt; build.json -&gt; значение по умолчанию.
 */
public final class BuildInfo {
    public static final String DEFAULT_EXPIRE = "01.01.2099 00:00";
    public static final String DEFAULT_TYPE = "public";
    public static final String DEFAULT_VERSION = "1.0.0";
    private static final String RESOURCE = "assets/laura/build.json";

    private static final String TYPE;
    private static final String EXPIRE;
    private static final String VERSION;
    private static final String BUILT_AT;

    static {
        JsonObject json = loadJson();
        TYPE = resolve("laura.build.type", "LAURA_BUILD_TYPE", json == null ? null : str(json, "buildType"), DEFAULT_TYPE);
        EXPIRE = resolve("laura.build.expire", "LAURA_BUILD_EXPIRE", json == null ? null : str(json, "expire"), DEFAULT_EXPIRE);
        VERSION = resolve("laura.build.version", "LAURA_BUILD_VERSION", json == null ? null : str(json, "version"), DEFAULT_VERSION);
        String builtAt = json == null ? null : str(json, "builtAt");
        BUILT_AT = builtAt == null || builtAt.isBlank() ? "" : builtAt.trim();
        System.out.println("[BuildInfo] type=" + TYPE + ", expire=" + EXPIRE + ", version=" + VERSION);
    }

    private BuildInfo() {
    }

    /** Тип сборки: "dev" или "public". */
    public static String type() {
        return TYPE;
    }

    /** Дата окончания действия клиента, формат "dd.MM.yyyy HH:mm". */
    public static String expire() {
        return EXPIRE;
    }

    /** Версия клиента из build.json. */
    public static String version() {
        return VERSION;
    }

    /** Когда собирался релиз (человекочитаемая строка) или пустая строка. */
    public static String builtAt() {
        return BUILT_AT;
    }

    private static String resolve(String prop, String envName, String fromJson, String fallback) {
        String value = System.getProperty(prop);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        if (envName != null) {
            value = System.getenv(envName);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        if (fromJson != null && !fromJson.isBlank()) {
            return fromJson.trim();
        }
        return fallback;
    }

    private static JsonObject loadJson() {
        try (InputStream in = BuildInfo.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
            return json;
        } catch (Exception ex) {
            System.err.println("[BuildInfo] Failed to read " + RESOURCE + ": " + ex.getMessage());
            return null;
        }
    }

    private static String str(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        return json.get(key).getAsString();
    }
}
