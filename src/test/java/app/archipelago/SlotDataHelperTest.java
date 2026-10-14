/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.archipelago;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotDataHelperTest {

    @TempDir
    File tempDir;

    private void writeSlotData(String contents) throws IOException {
        Files.writeString(new File(tempDir, "slot_data.json").toPath(), contents, StandardCharsets.UTF_8);
    }

    @Test
    void load_malformedTrailingComma_doesNotThrowAndIgnoresFile() throws IOException {
        writeSlotData("""
                {
                  "slot_data_keys": {
                    "include_deluxe": {
                      "type": "album",
                      "display_name": "Super Ghostbusters Deluxe Edition"
                    },
                  }
                }
                """);

        SlotDataHelper.loadSlotOptions(tempDir);

        assertTrue(SlotDataHelper.getSlotOptions().isEmpty());
    }

    @Test
    void load_missingFile_leavesOptionsEmpty() {
        SlotDataHelper.loadSlotOptions(tempDir);

        assertTrue(SlotDataHelper.getSlotOptions().isEmpty());
    }

    @Test
    void load_validFile_populatesOptions() throws IOException {
        writeSlotData("""
                {
                  "slot_data_keys": {
                    "include_deluxe": {
                      "type": "album",
                      "display_name": "Super Ghostbusters Deluxe Edition"
                    }
                  }
                }
                """);

        SlotDataHelper.loadSlotOptions(tempDir);

        Map<String, SlotDataHelper.SlotOption> options = SlotDataHelper.getSlotOptions();
        assertEquals(1, options.size());
        assertEquals("album", options.get("include_deluxe").type);
        assertEquals("Super Ghostbusters Deluxe Edition", options.get("include_deluxe").display_name);
    }
}
