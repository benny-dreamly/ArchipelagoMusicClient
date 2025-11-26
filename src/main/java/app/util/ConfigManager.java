package app.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static app.util.ConfigPaths.getConnectionConfigFile;

public class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);

    private ConfigManager() {} // utility class

    public static void saveConnectionSettings(String host, int port, String slot, String password) {
        Map<String, String> data = new HashMap<>();
        data.put("host", host);
        data.put("port", String.valueOf(port));
        data.put("slot", slot);
        data.put("password", password);

        File file = getConnectionConfigFile();
        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(data, writer);
            LOGGER.info("Saved connection settings to {}", file.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to save connection settings to {}", file.getAbsolutePath(), e);
        }
    }

    public static Map<String, String> loadConnectionSettings() {
        File file = getConnectionConfigFile();
        if (!file.exists()) return new HashMap<>();

        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            return new Gson().fromJson(reader, type);
        } catch (IOException e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
            return new HashMap<>();
        }
    }
}
