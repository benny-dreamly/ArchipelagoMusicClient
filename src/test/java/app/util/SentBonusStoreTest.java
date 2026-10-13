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
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentBonusStoreTest {

    @TempDir
    File tempDir;

    @Test
    void load_unknownSlot_returnsEmpty() {
        assertTrue(SentBonusStore.load(tempDir, "slotA").isEmpty());
    }

    @Test
    void markSent_thenLoad_keepsSlotsIndependent() {
        SentBonusStore.markSent(tempDir, "slotA", "Bonus 1");
        SentBonusStore.markSent(tempDir, "slotB", "Bonus 1");

        assertEquals(List.of("Bonus 1"), SentBonusStore.load(tempDir, "slotA"));
        assertEquals(List.of("Bonus 1"), SentBonusStore.load(tempDir, "slotB"));
        assertTrue(SentBonusStore.load(tempDir, "slotC").isEmpty());
    }

    @Test
    void markSent_multipleForSameSlot_preservesOrder() {
        SentBonusStore.markSent(tempDir, "slotA", "Bonus 1");
        SentBonusStore.markSent(tempDir, "slotA", "Bonus 2");

        assertEquals(List.of("Bonus 1", "Bonus 2"), SentBonusStore.load(tempDir, "slotA"));
    }

    @Test
    void markSent_sameLocationTwice_doesNotDuplicate() {
        SentBonusStore.markSent(tempDir, "slotA", "Bonus 1");
        SentBonusStore.markSent(tempDir, "slotA", "Bonus 1");

        assertEquals(List.of("Bonus 1"), SentBonusStore.load(tempDir, "slotA"));
    }

    @Test
    void legacyFlatList_isIsolatedFromNamedSlots() throws IOException {
        Files.writeString(new File(tempDir, "sentBonusChecks.json").toPath(),
                "[\"Old Bonus\"]", StandardCharsets.UTF_8);

        assertTrue(SentBonusStore.load(tempDir, "newSlot").isEmpty());

        SentBonusStore.markSent(tempDir, "newSlot", "New Bonus");
        assertEquals(List.of("New Bonus"), SentBonusStore.load(tempDir, "newSlot"));
    }
}