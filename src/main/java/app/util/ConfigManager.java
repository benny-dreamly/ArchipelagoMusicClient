package app.util;

import app.archipelago.APClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static app.util.ConfigPaths.getConnectionConfigFile;

public class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);

    private static final String SLOTS_KEY = "slots";

    private ConfigManager() {} // utility class

    public static void saveConnectionSettings(String host, int port, String slot, String password, String gameName) {
        Map<String, Object> data = loadAllSettings();
        data.put("host", host);
        data.put("port", String.valueOf(port));
        slotsMap(data).put(gameName, slot);
        write(data);
        storePassword(password);
    }

    public static String getSlotForGame(String gameName) {
        Object slots = loadAllSettings().get(SLOTS_KEY);
        if (slots instanceof Map<?, ?> map) {
            Object slot = map.get(gameName);
            if (slot == null && gameName != null) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (gameName.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                        slot = entry.getValue();
                        break;
                    }
                }
            }
            return slot == null ? "" : slot.toString();
        }
        return "";
    }

    public static Map<String, String> loadConnectionSettings() {
        Map<String, String> flat = new HashMap<>();
        for (Map.Entry<String, Object> entry : loadAllSettings().entrySet()) {
            if (entry.getValue() instanceof String value) {
                flat.put(entry.getKey(), value);
            }
        }
        String password = loadPassword();
        if (password != null) {
            flat.put("password", password);
        }
        return flat;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> slotsMap(Map<String, Object> data) {
        return (Map<String, String>) data.computeIfAbsent(SLOTS_KEY, _ -> new HashMap<String, String>());
    }

    private static Map<String, Object> loadAllSettings() {
        Map<String, Object> migrated = migrateLegacySettings();
        if (migrated != null) {
            return migrated;
        }
        return readGlobalSettings();
    }

    private static Map<String, Object> migrateLegacySettings() {
        File baseDir = getConnectionConfigFile().getParentFile();
        if (baseDir == null || !baseDir.isDirectory()) return null;

        File[] gameDirs = baseDir.listFiles(File::isDirectory);
        if (gameDirs == null) return null;

        Map<String, Map<String, String>> legacyByGame = new LinkedHashMap<>();
        List<File> migratedFiles = new ArrayList<>();

        for (File gameDir : gameDirs) {
            File legacy = new File(gameDir, "connection.json");
            if (!legacy.isFile()) continue;

            Map<String, String> old = readLegacySettings(legacy);
            if (old == null) continue;

            legacyByGame.put(gameDir.getName(), old);
            migratedFiles.add(legacy);
        }
        if (legacyByGame.isEmpty()) return null;

        Map<String, Object> data = readGlobalSettings();
        boolean oldFormat = !data.containsKey(SLOTS_KEY);
        Map<String, String> slots = slotsMap(data);

        for (Map.Entry<String, Map<String, String>> entry : legacyByGame.entrySet()) {
            String slot = entry.getValue().get("slot");
            if (slot != null) {
                slots.putIfAbsent(entry.getKey(), slot);
            }
        }

        if (oldFormat) {
            String currentGame = APClient.loadSavedGameNameStatic();
            Map<String, String> preferred = findIgnoreCase(legacyByGame, currentGame);
            if (preferred == null) {
                preferred = legacyByGame.values().iterator().next();
            }
            copyIfPresent(preferred, data, "host");
            copyIfPresent(preferred, data, "port");
        }
        data.remove("slot");

        if (!write(data)) {
            LOGGER.error("Write failed; preserving legacy connection files");
            return data;
        }
        for (File legacy : migratedFiles) {
            if (legacy.delete()) {
                LOGGER.info("Migrated and removed legacy connection settings {}", legacy.getAbsolutePath());
            }
        }
        LOGGER.info("Migrated legacy per-game connection settings into {}", getConnectionConfigFile().getAbsolutePath());
        return data;
    }

    private static Map<String, Object> readGlobalSettings() {
        File file = getConnectionConfigFile();
        if (!file.exists()) return new HashMap<>();

        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> data = new Gson().fromJson(reader, type);
            return data == null ? new HashMap<>() : data;
        } catch (IOException e) {
            LOGGER.error("Failed to load connection settings from {}", file.getAbsolutePath(), e);
            return new HashMap<>();
        }
    }

    private static Map<String, String> findIgnoreCase(Map<String, Map<String, String>> map, String key) {
        for (Map.Entry<String, Map<String, String>> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static void copyIfPresent(Map<String, String> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private static Map<String, String> readLegacySettings(File file) {
        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            return new Gson().fromJson(reader, type);
        } catch (IOException e) {
            LOGGER.error("Failed to read legacy connection settings from {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    private static boolean write(Map<String, Object> data) {
        File file = getConnectionConfigFile();
        File parent = file.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(data, writer);
            LOGGER.info("Saved connection settings to {}", file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to save connection settings to {}", file.getAbsolutePath(), e);
            return false;
        }
    }

    private static void storePassword(String password) {
        // TODO: implement OS credential store (e.g. macOS Keychain via `security` CLI)
        if (password != null && !password.isEmpty()) {
            LOGGER.warn("No credential store available; password not persisted");
        }
    }

    private static String loadPassword() {
        // TODO: implement OS credential store (e.g. macOS Keychain via `security` CLI)
        return null;
    }
}
