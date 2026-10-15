/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.logic;

import app.AppContext;
import app.player.Album;
import app.player.PlaybackController;
import app.player.PlaybackEngine;
import app.player.Song;
import app.util.SessionStore;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static app.util.ConfigManager.loadSessionRestore;

/**
 * Persists and restores the play queue and playback position across sessions.
 * A snapshot is saved periodically and on shutdown; on a later connect to the
 * same game/slot it is applied once the starting-inventory item burst has
 * settled. Extracted from {@code MusicAppDemo}; the application-specific bits
 * are reached through the {@link AppContext} and playback flow goes through
 * {@link PlaybackController}.
 */
public class SessionRestoreController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionRestoreController.class);

    private static final Duration DEBOUNCE = Duration.millis(150);

    private final AppContext host;
    private final PlaybackController playback;
    private final Timeline debounce = new Timeline();

    private SessionStore.Snapshot pending;
    private int pendingGeneration;

    public SessionRestoreController(AppContext host, PlaybackController playback) {
        this.host = host;
        this.playback = playback;
    }

    /**
     * Arms a restore when the setting is on and the stored snapshot matches the
     * game/slot being connected to. Must be called after the library reload so
     * the pending restore tracks the load this connect started.
     */
    public void arm(String gameName, String slot) {
        pending = null;
        if (!loadSessionRestore()) {
            return;
        }
        SessionStore.Snapshot snapshot = SessionStore.load();
        if (snapshot == null || !snapshot.hasContent()) {
            return;
        }
        if (!snapshot.game().equals(gameName) || !snapshot.slot().equals(slot)) {
            LOGGER.info("Session restore skipped: snapshot is for game={} slot={}, "
                    + "connecting to game={} slot={}",
                    snapshot.game(), snapshot.slot(), gameName, slot);
            return;
        }
        pending = snapshot;
        pendingGeneration = host.loadGeneration();
        LOGGER.info("Session restore armed: {} queued songs, current={} ({} ms)",
                snapshot.queue().size(), snapshot.currentTitle(), snapshot.positionMs());
    }

    /**
     * Requests that the pending restore be applied after a short quiescence.
     * The library fires one item event per starting-inventory item with no
     * batch-complete signal, so we debounce on the item stream instead: every
     * item event re-arms this timer, and the restore only applies once the
     * events stop and unlock state has settled. No-op when nothing is pending.
     * Must be called on the FX thread.
     */
    public void scheduleApply() {
        if (pending == null) {
            return;
        }
        debounce.stop();
        debounce.getKeyFrames().setAll(new KeyFrame(DEBOUNCE, _ -> apply()));
        debounce.play();
    }

    /**
     * Applies the pending restore once the library and connection are ready and
     * the starting-inventory item burst has settled (via {@link #scheduleApply}).
     * Only applies while the queue is empty so it can never clobber live state;
     * if nothing is playable yet (items still arriving) the restore stays
     * pending.
     */
    public void apply() {
        SessionStore.Snapshot snapshot = pending;
        if (snapshot == null) {
            return;
        }
        if (pendingGeneration != host.loadGeneration()) {
            pending = null; // superseded by a newer load
            return;
        }
        QueueManager queueManager = host.queueManager();
        if (!host.connected() || queueManager == null || !host.libraryReady()) {
            return; // not ready yet — the debounce re-runs this once ready
        }
        if (!queueManager.isEmpty()) {
            LOGGER.info("Session restore skipped: queue is not empty");
            pending = null; // the user has taken over — abandon it
            return;
        }
        if (!snapshot.hasContent()) {
            pending = null;
            return;
        }

        List<Song> resolved = new ArrayList<>();
        for (Map<String, String> entry : snapshot.queue()) {
            Song song = findSong(entry.get("title"), entry.get("type"));
            if (song != null && host.canQueue(song, host.albumForSong(song))) {
                resolved.add(song);
            }
        }
        Song current = snapshot.currentTitle() != null
                ? findSong(snapshot.currentTitle(), snapshot.currentType())
                : null;
        boolean currentPlayable = current != null && host.canPlay(current, host.albumForSong(current));

        if (resolved.isEmpty() && !currentPlayable) {
            // Nothing is playable yet — usually the starting-inventory items that
            // would unlock these songs have not been applied. Keep the restore
            // pending so the next item event's debounce re-attempts it once
            // unlock state is complete.
            LOGGER.debug("Session restore deferred: no songs playable yet (items may still be arriving)");
            return;
        }

        pending = null;
        queueManager.replaceAll(resolved);
        host.updateQueueDisplay();
        if (currentPlayable) {
            playback.setPendingSeek(Duration.millis((double) Math.max(0, snapshot.positionMs())));
            playback.playSong(current);
        }
        LOGGER.info("Session restored: {} queued songs{}", resolved.size(),
                currentPlayable ? ", resuming \"" + current.getTitle() + "\"" : "");
    }

    /**
     * Persists the current queue and playback position when session restore is
     * enabled. Skipped when disconnected with nothing queued so a fresh or
     * failed connection can't overwrite the previous session's snapshot with
     * the empty state left behind by the library reload.
     */
    public void saveSnapshot() {
        QueueManager queueManager = host.queueManager();
        if (queueManager == null || !host.sessionRestoreEnabled()) {
            return;
        }
        boolean connected = host.connected();
        Song currentSong = playback.getCurrentSong();
        boolean hasState = !queueManager.isEmpty() || currentSong != null;
        if (!connected && !hasState) {
            return;
        }
        PlaybackEngine engine = playback.getEngine();
        long positionMs = 0;
        if (engine != null && engine.getCurrentTime() != null) {
            positionMs = (long) engine.getCurrentTime().toMillis();
        }
        SessionStore.save(new SessionStore.Snapshot(
                host.gameName(),
                host.slot(),
                queueManager.toEntries(),
                currentSong != null ? currentSong.getTitle() : null,
                currentSong != null ? currentSong.getType() : null,
                positionMs));
    }

    private Song findSong(String title, String type) {
        if (title == null || !host.libraryReady()) {
            return null;
        }
        for (Album album : host.albums()) {
            for (Song song : album.getSongs()) {
                if (song.getTitle().equals(title) && (type == null || song.getType().equals(type))) {
                    return song;
                }
            }
        }
        return null;
    }
}
