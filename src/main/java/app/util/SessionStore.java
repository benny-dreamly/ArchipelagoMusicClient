/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static app.util.ConfigPaths.getSessionFile;

/**
 * Persists a snapshot of the play queue and the current song's playback
 * position so a later connection to the same game and slot can restore it.
 * The snapshot file is global (sibling of connection.json) and records which
 * game/slot it belongs to; restore only applies on a matching connect.
 */
public final class SessionStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionStore.class);

    public record Snapshot(String game, String slot, List<Map<String, String>> queue,
                           String currentTitle, String currentType, long positionMs) {

        public Snapshot {
            queue = queue == null ? List.of() : List.copyOf(queue);
        }

        public boolean hasContent() {
            return !queue.isEmpty() || currentTitle != null;
        }
    }

    private SessionStore() {} // utility class

    public static Snapshot load() {
        return load(getSessionFile());
    }

    public static boolean save(Snapshot snapshot) {
        return save(snapshot, getSessionFile());
    }

    static Snapshot load(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            Snapshot snapshot = new Gson().fromJson(reader, Snapshot.class);
            if (snapshot == null || snapshot.game() == null || snapshot.slot() == null) {
                LOGGER.warn("Ignoring incomplete session snapshot in {}", file.getAbsolutePath());
                return null;
            }
            return snapshot;
        } catch (IOException e) {
            LOGGER.error("Failed to read session snapshot from {}", file.getAbsolutePath(), e);
            return null;
        } catch (JsonSyntaxException e) {
            LOGGER.error("Malformed session snapshot in {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    static boolean save(Snapshot snapshot, File file) {
        if (snapshot == null || file == null) {
            return false;
        }
        File parent = file.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                    .toJson(snapshot, writer);
            LOGGER.info("Session snapshot saved to {}", file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to save session snapshot to {}", file.getAbsolutePath(), e);
            return false;
        }
    }
}
