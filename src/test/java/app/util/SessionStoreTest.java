/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.util;

import app.util.SessionStore.Snapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStoreTest {

    @TempDir
    File tempDir;

    private File sessionFile() {
        return new File(tempDir, "session.json");
    }

    @Test
    void saveThenLoadRoundTripsSnapshot() {
        Snapshot original = new Snapshot(
                "Manual_TaylorSwiftDiscography_bennydreamly",
                "Player1",
                List.of(Map.of("title", "Song A", "type", "Album"),
                        Map.of("title", "Song B", "type", "Album")),
                "Song A",
                "Album",
                42_500);

        assertTrue(SessionStore.save(original, sessionFile()));
        Snapshot loaded = SessionStore.load(sessionFile());

        assertNotNull(loaded);
        assertEquals(original.game(), loaded.game());
        assertEquals(original.slot(), loaded.slot());
        assertEquals(original.queue(), loaded.queue());
        assertEquals(original.currentTitle(), loaded.currentTitle());
        assertEquals(original.currentType(), loaded.currentType());
        assertEquals(original.positionMs(), loaded.positionMs());
        assertTrue(loaded.hasContent());
    }

    @Test
    void loadMissingFileReturnsNull() {
        assertNull(SessionStore.load(sessionFile()));
    }

    @Test
    void loadMalformedFileReturnsNull() throws IOException {
        Files.writeString(sessionFile().toPath(), "{ not json", StandardCharsets.UTF_8);
        assertNull(SessionStore.load(sessionFile()));
    }

    @Test
    void loadIncompleteSnapshotReturnsNull() throws IOException {
        Files.writeString(sessionFile().toPath(), "{\"slot\":\"Player1\"}",
                StandardCharsets.UTF_8);
        assertNull(SessionStore.load(sessionFile()));
    }

    @Test
    void snapshotWithoutGameOrQueueHasNoContent() {
        Snapshot empty = new Snapshot("Game", "Player1", List.of(), null, null, 0);
        assertFalse(empty.hasContent());

        Snapshot withCurrent = new Snapshot("Game", "Player1", List.of(), "Song", "Album", 5);
        assertTrue(withCurrent.hasContent());
    }

    @Test
    void saveNullSnapshotReturnsFalse() {
        assertFalse(SessionStore.save(null, sessionFile()));
        assertFalse(SessionStore.save(new Snapshot("G", "S", List.of(), null, null, 0), null));
    }

    @Test
    void nullQueueIsNormalizedToEmpty() {
        Snapshot snapshot = new Snapshot("Game", "Player1", null, null, null, 0);
        assertNotNull(snapshot.queue());
        assertTrue(snapshot.queue().isEmpty());
        assertFalse(snapshot.hasContent());
    }
}
