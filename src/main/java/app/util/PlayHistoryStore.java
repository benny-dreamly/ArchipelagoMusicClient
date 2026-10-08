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
import java.util.Set;
import java.util.TreeSet;

import static app.util.ConfigPaths.getPlayHistoryFile;

/**
 * Persists locally-tracked played songs (as "album::song" keys, the same
 * format {@code GoalManager} uses) to a global play_history.json sibling of
 * connection.json. Used while disconnected, in offline mode, and in Browse
 * Folder mode so played counts and "Queue Unplayed" work without a server.
 */
public final class PlayHistoryStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayHistoryStore.class);
    private static final Type SET_TYPE = new TypeToken<Set<String>>(){}.getType();

    private PlayHistoryStore() {} // utility class

    public static Set<String> load() {
        return load(getPlayHistoryFile());
    }

    public static boolean save(Set<String> playedKeys) {
        return save(playedKeys, getPlayHistoryFile());
    }

    static Set<String> load(File file) {
        if (file == null || !file.exists()) {
            return new LinkedHashSet<>();
        }
        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            Set<String> loaded = new Gson().fromJson(reader, SET_TYPE);
            if (loaded == null) {
                return new LinkedHashSet<>();
            }
            loaded.removeIf(java.util.Objects::isNull);
            return loaded;
        } catch (IOException e) {
            LOGGER.error("Failed to read play history from {}", file.getAbsolutePath(), e);
            return new LinkedHashSet<>();
        } catch (JsonSyntaxException e) {
            LOGGER.error("Malformed play history in {}", file.getAbsolutePath(), e);
            return new LinkedHashSet<>();
        }
    }

    static boolean save(Set<String> playedKeys, File file) {
        if (playedKeys == null || file == null) {
            return false;
        }
        File parent = file.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        // Deterministic order keeps consecutive writes stable and diffs readable.
        List<String> sorted = new ArrayList<>(new TreeSet<>(playedKeys));
        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                    .toJson(sorted, writer);
            LOGGER.info("Saved {} played songs to {}", sorted.size(), file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to save play history to {}", file.getAbsolutePath(), e);
            return false;
        }
    }
}