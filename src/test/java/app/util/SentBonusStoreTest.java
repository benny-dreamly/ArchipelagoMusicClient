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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentBonusStoreTest {

    @TempDir
    File tempDir;

    @Test
    void load_unknownPortAndSlot_returnsEmpty() {
        assertTrue(SentBonusStore.load(tempDir, "38281", "slotA").isEmpty());
    }

    @Test
    void markSent_thenLoad_keepsPortsAndSlotsIndependent() {
        SentBonusStore.markSent(tempDir, "38281", "slotA", "Bonus 1");
        SentBonusStore.markSent(tempDir, "38281", "slotB", "Bonus 1");
        SentBonusStore.markSent(tempDir, "38282", "slotA", "Bonus 1");

        assertEquals(List.of("Bonus 1"), SentBonusStore.load(tempDir, "38281", "slotA"));
        assertEquals(List.of("Bonus 1"), SentBonusStore.load(tempDir, "38281", "slotB"));
        // Same slot name on a different room is a different player
        assertEquals(List.of("Bonus 1"), SentBonusStore.load(tempDir, "38282", "slotA"));
        assertTrue(SentBonusStore.load(tempDir, "38283", "slotA").isEmpty());
    }

    @Test
    void markSent_multipleForSamePortAndSlot_preservesOrder() {
        SentBonusStore.markSent(tempDir, "38281", "slotA", "Bonus 1");
        SentBonusStore.markSent(tempDir, "38281", "slotA", "Bonus 2");

        assertEquals(List.of("Bonus 1", "Bonus 2"), SentBonusStore.load(tempDir, "38281", "slotA"));
    }

    @Test
    void markSent_sameLocationTwice_doesNotDuplicate() {
        SentBonusStore.markSent(tempDir, "38281", "slotA", "Bonus 1");
        SentBonusStore.markSent(tempDir, "38281", "slotA", "Bonus 1");

        assertEquals(List.of("Bonus 1"), SentBonusStore.load(tempDir, "38281", "slotA"));
    }

    @Test
    void legacyFlatList_isIsolatedFromPortSlotEntries() throws IOException {
        Files.writeString(new File(tempDir, "sentBonusChecks.json").toPath(),
                "[\"Old Bonus\"]", StandardCharsets.UTF_8);

        assertTrue(SentBonusStore.load(tempDir, "38281", "newSlot").isEmpty());

        SentBonusStore.markSent(tempDir, "38281", "newSlot", "New Bonus");
        assertEquals(List.of("New Bonus"), SentBonusStore.load(tempDir, "38281", "newSlot"));
    }

    @Test
    void legacySlotOnlyEntries_areDroppedOnWrite() throws IOException {
        Files.writeString(new File(tempDir, "sentBonusChecks.json").toPath(),
                "{\"slotA\": [\"Bonus 1\"]}", StandardCharsets.UTF_8);

        SentBonusStore.markSent(tempDir, "38281", "slotA", "Bonus 2");
        assertEquals(List.of("Bonus 2"), SentBonusStore.load(tempDir, "38281", "slotA"));

        String stored = Files.readString(new File(tempDir, "sentBonusChecks.json").toPath(),
                StandardCharsets.UTF_8);
        assertFalse(stored.contains("\"slotA\"")); // the unattributed slot-only key is gone
        assertTrue(stored.contains("\"38281:slotA\""));
    }
}
