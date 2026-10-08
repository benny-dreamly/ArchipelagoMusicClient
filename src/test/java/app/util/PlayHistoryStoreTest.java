/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayHistoryStoreTest {

    @TempDir
    File tempDir;

    private File playHistoryFile() {
        return new File(tempDir, "play_history.json");
    }

    @Test
    void saveThenLoadRoundTripsPlayedKeys() {
        Set<String> played = Set.of(
                "Album B::Song 2",
                "Album A::Song 1",
                "Album A::Song 3");

        assertTrue(PlayHistoryStore.save(played, playHistoryFile()));
        Set<String> loaded = PlayHistoryStore.load(playHistoryFile());

        assertNotNull(loaded);
        assertEquals(played, loaded);
    }

    @Test
    void loadMissingFileReturnsEmptySet() {
        assertTrue(PlayHistoryStore.load(playHistoryFile()).isEmpty());
    }

    @Test
    void loadMalformedFileReturnsEmptySet() throws IOException {
        Files.writeString(playHistoryFile().toPath(), "{ not json", StandardCharsets.UTF_8);
        assertTrue(PlayHistoryStore.load(playHistoryFile()).isEmpty());
    }

    @Test
    void loadDropsNullEntries() throws IOException {
        Files.writeString(playHistoryFile().toPath(), "[\"Album::Song\",null]",
                StandardCharsets.UTF_8);
        assertEquals(Set.of("Album::Song"), PlayHistoryStore.load(playHistoryFile()));
    }

    @Test
    void loadEmptyJsonArrayReturnsEmptySet() throws IOException {
        Files.writeString(playHistoryFile().toPath(), "[]", StandardCharsets.UTF_8);
        assertTrue(PlayHistoryStore.load(playHistoryFile()).isEmpty());
    }

    @Test
    void saveNullsReturnFalse() {
        assertFalse(PlayHistoryStore.save(null, playHistoryFile()));
        assertFalse(PlayHistoryStore.save(Set.of(), null));
    }

    @Test
    void savedFileIsReadableAndPreservesAllEntries() {
        Set<String> played = new LinkedHashSet<>();
        for (int i = 0; i < 500; i++) {
            played.add("Album " + i + "::Song");
        }

        assertTrue(PlayHistoryStore.save(played, playHistoryFile()));
        assertEquals(played, PlayHistoryStore.load(playHistoryFile()));
    }
}