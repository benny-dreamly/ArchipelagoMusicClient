package archipelago.phone.net;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Locks the phone's Gson mapping to the JSON the desktop broadcasts, so a
 * field rename on either side fails loudly here.
 */
public class CompanionStateTest {

    @Test
    public void parsesDesktopPlaysPhoneStateJson() {
        String json = "{"
                + "\"songTitle\":\"Song\",\"album\":\"Album\",\"streamPath\":\"/stream/A/Song.mp3\","
                + "\"durationMs\":100000,\"positionMs\":40000,\"playing\":true,\"volume\":70,"
                + "\"queue\":[\"Song\"],\"activeSource\":\"phone\"}";

        CompanionState state = CompanionState.fromJson(json);

        assertEquals("Song", state.songTitle);
        assertEquals("Album", state.album);
        assertEquals("/stream/A/Song.mp3", state.streamPath);
        assertEquals(100000L, state.durationMs);
        assertEquals(40000L, state.positionMs);
        assertTrue(state.playing);
        assertEquals(70, state.volume);
        assertEquals(List.of("Song"), state.queue);
        assertEquals("phone", state.activeSource);
    }

    @Test
    public void parsesIdleDesktopJson() {
        String json = "{"
                + "\"songTitle\":null,\"album\":null,\"streamPath\":null,"
                + "\"durationMs\":0,\"positionMs\":0,\"playing\":false,\"volume\":50,"
                + "\"queue\":[],\"activeSource\":\"desktop\"}";

        CompanionState state = CompanionState.fromJson(json);

        assertNull(state.songTitle);
        assertNull(state.album);
        assertNull(state.streamPath);
        assertEquals(0L, state.durationMs);
        assertEquals(0L, state.positionMs);
        assertFalse(state.playing);
        assertTrue(state.queue.isEmpty());
        assertEquals("desktop", state.activeSource);
    }
}