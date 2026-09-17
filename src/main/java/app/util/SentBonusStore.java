/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.LinkedHashSet;
import java.util.List;

import static app.util.ConfigPaths.getConfigDir;

public class SentBonusStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(SentBonusStore.class);

    private static final String FILE_NAME = "sentBonusChecks.json";

    private SentBonusStore() {} // utility class

    public static List<String> load() {
        File file = new File(getConfigDir(), FILE_NAME);
        if (!file.isFile()) return new ArrayList<>();

        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<String>>() {}.getType();
            List<String> list = new Gson().fromJson(reader, type);
            return list == null ? new ArrayList<>() : new ArrayList<>(new LinkedHashSet<>(list));
        } catch (IOException e) {
            LOGGER.warn("Failed to load sent bonus checks from {}", file.getAbsolutePath(), e);
        } catch (JsonSyntaxException e) {
            LOGGER.warn("Malformed sent bonus checks in {}", file.getAbsolutePath(), e);
        }
        return new ArrayList<>();
    }

    public static void markSent(String location) {
        List<String> sent = new ArrayList<>(new LinkedHashSet<>(load()));
        sent.add(location);

        File file = new File(getConfigDir(), FILE_NAME);
        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(sent, writer);
        } catch (IOException e) {
            LOGGER.warn("Failed to save sent bonus checks to {}", file.getAbsolutePath(), e);
        }
    }
}