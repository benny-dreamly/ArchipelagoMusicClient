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

    @Test
    void normalizeFilename_expandsTaylorVersionAbbreviation() {
        String rawInput = "01 Style (Taylor's Ver).mp3";

        String result = Normalization.normalizeFilename(rawInput);

        assertEquals("Style (Taylor's Version)", result);
    }

    @Test
    void normalizeFilename_standardizesFeatAbbreviation() {
        String rawInput = "Karma ft. Ice Spice.flac";

        String result = Normalization.normalizeFilename(rawInput);

        assertEquals("Karma feat. Ice Spice", result);
    }

    @Test
    void normalizeFilename_stripsCdAndTrackPrefix() {
        String rawInput = "CD1 02 Clean.m4a";

        String result = Normalization.normalizeFilename(rawInput);

        assertEquals("Clean", result);
    }

    @Test
    void normalizeFilename_preservesFilenameWithoutExtension() {
        String rawInput = "Mastermind";

        String result = Normalization.normalizeFilename(rawInput);

        assertEquals("Mastermind", result);
    }

    @Test
    void stripFeatCredit_removesFeatParenthetical() {
        assertEquals("Snow On The Beach", Normalization.stripFeatCredit("Snow On The Beach (feat. Lana Del Rey)"));
        assertEquals("Karma", Normalization.stripFeatCredit("Karma (ft. Ice Spice)"));
        assertEquals("The Last Time", Normalization.stripFeatCredit("The Last Time (Feat. Gary Lightbody)"));
    }

    @Test
    void stripFeatCredit_leavesNonFeatParentheticals() {
        assertEquals("Is It Over Now? (Taylor's Version) (From The Vault)",
                Normalization.stripFeatCredit("Is It Over Now? (Taylor's Version) (From The Vault)"));
        assertEquals("No Parenthetical", Normalization.stripFeatCredit("No Parenthetical"));
    }
}