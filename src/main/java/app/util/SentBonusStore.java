/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static app.util.ConfigPaths.getConfigDir;

public class SentBonusStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(SentBonusStore.class);

    private static final String FILE_NAME = "sentBonusChecks.json";

    private SentBonusStore() {} // utility class

    /** Bonus checks already sent for the given slot, or an empty list. */
    public static List<String> load(String slot) {
        return load(getConfigDir(), slot);
    }

    /** Marks a bonus check as sent for the given slot. */
    public static void markSent(String slot, String location) {
        markSent(getConfigDir(), slot, location);
    }

    static List<String> load(File configDir, String slot) {
        return all(configDir).getOrDefault(slot, List.of());
    }

    static void markSent(File configDir, String slot, String location) {
        Map<String, List<String>> sent = all(configDir);
        LinkedHashSet<String> forSlot = new LinkedHashSet<>(sent.getOrDefault(slot, List.of()));
        forSlot.add(location);
        sent.put(slot, new ArrayList<>(forSlot));
        write(configDir, sent);
    }

    private static Map<String, List<String>> all(File configDir) {
        File file = new File(configDir, FILE_NAME);
        if (!file.isFile()) return new HashMap<>();

        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || element.isJsonNull()) return new HashMap<>();
            if (element.isJsonArray()) {
                // Legacy flat-list format: treat as belonging to no named slot
                Type arrayType = new TypeToken<List<String>>() {}.getType();
                List<String> legacy = new Gson().fromJson(element, arrayType);
                Map<String, List<String>> migrated = new HashMap<>();
                migrated.put("", legacy == null ? new ArrayList<>() : legacy);
                return migrated;
            }
            Type mapType = new TypeToken<Map<String, List<String>>>() {}.getType();
            Map<String, List<String>> map = new Gson().fromJson(element, mapType);
            return map == null ? new HashMap<>() : map;
        } catch (IOException e) {
            LOGGER.warn("Failed to load sent bonus checks from {}", file.getAbsolutePath(), e);
        } catch (JsonSyntaxException e) {
            LOGGER.warn("Malformed sent bonus checks in {}", file.getAbsolutePath(), e);
        }
        return new HashMap<>();
    }

    private static void write(File configDir, Map<String, List<String>> sent) {
        File file = new File(configDir, FILE_NAME);
        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(sent, writer);
        } catch (IOException e) {
            LOGGER.warn("Failed to save sent bonus checks to {}", file.getAbsolutePath(), e);
        }
    }
}