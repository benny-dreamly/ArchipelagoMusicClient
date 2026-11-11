/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigManagerTest {

    @Test
    void clampVolume_acceptsNumbersInsideTheSliderRange() {
        assertEquals(0.0, ConfigManager.clampVolume(0));
        assertEquals(42.5, ConfigManager.clampVolume(42.5));
        assertEquals(100.0, ConfigManager.clampVolume(100));
        assertEquals(37.0, ConfigManager.clampVolume(37L));
    }

    @Test
    void clampVolume_clampsOutOfRangeValuesIntoTheSliderRange() {
        assertEquals(100.0, ConfigManager.clampVolume(150));
        assertEquals(0.0, ConfigManager.clampVolume(-3));
        assertEquals(100.0, ConfigManager.clampVolume(1e9));
    }

    @Test
    void clampVolume_fallsBackToTheDefaultForMissingOrInvalidValues() {
        assertEquals(ConfigManager.DEFAULT_VOLUME, ConfigManager.clampVolume(null));
        assertEquals(ConfigManager.DEFAULT_VOLUME, ConfigManager.clampVolume("loud"));
        assertEquals(ConfigManager.DEFAULT_VOLUME, ConfigManager.clampVolume(true));
        assertEquals(ConfigManager.DEFAULT_VOLUME, ConfigManager.clampVolume(Double.NaN));
        assertEquals(ConfigManager.DEFAULT_VOLUME, ConfigManager.clampVolume(Double.NEGATIVE_INFINITY));
    }
}
