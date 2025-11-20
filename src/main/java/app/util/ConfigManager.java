package app.util;

import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import static app.util.ConfigPaths.getConnectionConfigFile;

public class ConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    private ConfigManager() {} // utility class

    public static void saveConnectionSettings(String host, int port, String slot, String password) {
        Map<String, String> data = new HashMap<>();
        data.put("host", host);
        data.put("port", String.valueOf(port));
        data.put("slot", slot);
        data.put("password", password);

        File file = getConnectionConfigFile();
        try (Writer writer = new FileWriter(file)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(data, writer);
            logger.info("Saved connection settings to {}", file.getAbsolutePath());
        } catch (IOException e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }
}
