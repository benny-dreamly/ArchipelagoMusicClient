/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhonePlaybackTest {

    private final List<String> sent = new ArrayList<>();

    private static Song song(String title, String filePath) {
        Song song = new Song(title, "mp3", title);
        song.setFilePath(filePath);
        return song;
    }

    private PhonePlayback newPlayback() {
        return new PhonePlayback(sent::add, s -> "/stream/" + s.getFilePath());
    }

    private JsonObject lastCommand() {
        return JsonParser.parseString(sent.get(sent.size() - 1)).getAsJsonObject();
    }

    @Test
    void loadAndPlaySendsPlayCommand() {
        PhonePlayback playback = newPlayback();
        playback.loadAndPlay(song("Song Title", "some/path/song.mp3"));

        assertEquals(1, sent.size());
        JsonObject cmd = lastCommand();
        assertEquals("command", cmd.get("type").getAsString());
        assertEquals("play", cmd.get("cmd").getAsString());
        assertEquals("/stream/some/path/song.mp3", cmd.get("streamPath").getAsString());
        assertEquals("Song Title", cmd.get("title").getAsString());
        assertTrue(playback.isLoaded());
        assertFalse(playback.isPlaying());
    }

    @Test
    void pauseResumeAndStopSendCommands() {
        PhonePlayback playback = newPlayback();
        playback.loadAndPlay(song("t", "f"));

        playback.pause();
        assertEquals("pause", lastCommand().get("cmd").getAsString());
        assertTrue(playback.isPaused());
        assertFalse(playback.isPlaying());

        playback.resume();
        assertEquals("resume", lastCommand().get("cmd").getAsString());
        assertTrue(playback.isPlaying());

        playback.stopAndRelease();
        assertEquals("stop", lastCommand().get("cmd").getAsString());
        assertFalse(playback.isLoaded());
        assertEquals(MediaPlayer.Status.UNKNOWN, playback.getStatus());
    }

    @Test
    void seekRateAndVolumeForwardNumericParameters() {
        PhonePlayback playback = newPlayback();
        playback.loadAndPlay(song("t", "f"));

        playback.seek(Duration.millis(12_345));
        JsonObject seek = lastCommand();
        assertEquals("seek", seek.get("cmd").getAsString());
        assertEquals(12_345L, seek.get("positionMs").getAsLong());

        playback.setRate(1.5);
        assertEquals(1.5, lastCommand().get("rate").getAsDouble());

        playback.setVolume(0.42);
        assertEquals(42, lastCommand().get("value").getAsInt());
    }

    @Test
    void startedEventMarksPlayingAndFiresReady() {
        PhonePlayback playback = newPlayback();
        AtomicReference<Boolean> ready = new AtomicReference<>(false);
        playback.setOnReady(() -> ready.set(true));
        playback.loadAndPlay(song("t", "f"));

        playback.handlePhoneEvent(event("started", 200_000, 300_000));

        assertTrue(ready.get());
        assertTrue(playback.isPlaying());
        assertEquals(MediaPlayer.Status.PLAYING, playback.getStatus());
        assertEquals(Duration.millis(300_000), playback.getTotalDuration());
        assertEquals(Duration.millis(200_000), playback.getCurrentTime());
    }

    @Test
    void pauseAndResumeEventsUpdateStatus() {
        PhonePlayback playback = newPlayback();
        playback.loadAndPlay(song("t", "f"));
        playback.handlePhoneEvent(event("started", 0, 100_000));

        playback.handlePhoneEvent(event("paused", 50_000, 0));
        assertFalse(playback.isPlaying());
        assertTrue(playback.isPaused());
        assertEquals(Duration.millis(50_000), playback.getCurrentTime());

        playback.handlePhoneEvent(event("resumed", 50_000, 0));
        assertTrue(playback.isPlaying());
    }

    @Test
    void positionEventFiresCurrentTimeCallback() {
        PhonePlayback playback = newPlayback();
        AtomicLong lastPosition = new AtomicLong(-1);
        playback.setOnCurrentTime(t -> lastPosition.set((long) t.toMillis()));
        playback.loadAndPlay(song("t", "f"));

        playback.handlePhoneEvent(event("position", 42_000, 0));

        assertEquals(42_000L, lastPosition.get());
        assertEquals(Duration.millis(42_000), playback.getCurrentTime());
    }

    @Test
    void endedEventFiresEndedCallback() {
        PhonePlayback playback = newPlayback();
        AtomicReference<Boolean> ended = new AtomicReference<>(false);
        playback.setOnEnded(() -> ended.set(true));
        playback.loadAndPlay(song("t", "f"));

        playback.handlePhoneEvent(event("ended", 100_000, 0));

        assertTrue(ended.get());
        assertFalse(playback.isPlaying());
    }

    @Test
    void errorEventFiresErrorCallbackWithMessage() {
        PhonePlayback playback = newPlayback();
        AtomicReference<String> error = new AtomicReference<>();
        playback.setOnError(error::set);
        playback.loadAndPlay(song("t", "f"));

        playback.handlePhoneEvent(event("error", 0, 0));
        assertEquals("Unknown error", error.get());

        JsonObject err = event("error", 0, 0);
        err.addProperty("message", "Format not supported");
        playback.handlePhoneEvent(err);

        assertEquals("Format not supported", error.get());
    }

    @Test
    void withoutStartedEventDurationStaysUnknown() {
        PhonePlayback playback = newPlayback();
        playback.loadAndPlay(song("t", "f"));

        assertNull(playback.getTotalDuration());
        assertTrue(playback.isPaused());
    }

    private static JsonObject event(String name, long positionMs, long durationMs) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "event");
        json.addProperty("event", name);
        json.addProperty("positionMs", positionMs);
        json.addProperty("durationMs", durationMs);
        return json;
    }
}