package app.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NormalizationTest {

    @Test
    void normalize_stripsTrackNumbersAndExtensions() {
        // Test filename cleaning like "07 Question..._.m4a"
        String rawFilename = "07 Question..._.m4a";

        String result = Normalization.normalizeFilename(rawFilename);

        assertEquals("Question...", result);
    }

    @Test
    void normalize_trimsAndCollapsesWhitespace() {
        String rawInput = "  Hello World  ";

        String result = Normalization.normalizeFilename(rawInput);

        assertEquals("Hello World", result);
    }
}