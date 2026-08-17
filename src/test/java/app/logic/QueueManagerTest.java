package app.logic;

import app.logic.QueueManager.RepeatMode;
import app.player.Album;
import app.player.Song;
import app.util.AlbumLibrary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueueManagerTest {

    private final UnlockManager unlockManager = new UnlockManager(() -> {});
    private final Song songA = new Song("Song A", "standard");
    private final Song songB = new Song("Song B", "standard");
    private final Song songC = new Song("Song C", "standard");

    {
        unlockManager.getEnabledSets().add("standard");
    }

    @Test
    void nextSong_drainsQueueAndReturnsNullWhenEmpty() {
        QueueManager qm = new QueueManager(new AlbumLibrary(List.of()), unlockManager);

        qm.add(songA);
        qm.add(songB);

        assertEquals(songA, qm.nextSong(null));
        assertEquals(songB, qm.nextSong(null));
        assertNull(qm.nextSong(null));
    }

    @Test
    void nextSong_repeatOff_doesNotRefillEmptyQueue() {
        QueueManager qm = new QueueManager(new AlbumLibrary(List.of()), unlockManager);

        qm.add(songA);

        assertEquals(songA, qm.nextSong(null));
        assertNull(qm.nextSong(null));
    }

    @Test
    void nextSong_repeatQueue_refillsFromSnapshot() {
        QueueManager qm = new QueueManager(new AlbumLibrary(List.of()), unlockManager);
        qm.setRepeatMode(RepeatMode.QUEUE);
        qm.add(songA);
        qm.add(songB);
        qm.recordSnapshot(qm.poll());

        assertEquals(songB, qm.nextSong(songA));
        assertEquals(songA, qm.nextSong(songA));
        assertEquals(songB, qm.nextSong(songA));
        assertEquals(songA, qm.nextSong(songA));
    }

    @Test
    void recordSnapshot_capturedOnlyOnce() {
        QueueManager qm = new QueueManager(new AlbumLibrary(List.of()), unlockManager);
        qm.setRepeatMode(RepeatMode.QUEUE);
        qm.add(songA);
        qm.add(songB);
        qm.recordSnapshot(qm.poll());

        qm.add(songC);
        qm.recordSnapshot(songC);

        assertEquals(songB, qm.nextSong(null));
        assertEquals(songC, qm.nextSong(null));
        assertEquals(songA, qm.nextSong(null));
        assertEquals(songB, qm.nextSong(null));
    }

    @Test
    void recordSnapshot_ignoredWhenNotQueueMode() {
        QueueManager qm = new QueueManager(new AlbumLibrary(List.of()), unlockManager);
        qm.setRepeatMode(RepeatMode.OFF);
        qm.recordSnapshot(songA);

        qm.setRepeatMode(RepeatMode.QUEUE);
        assertNull(qm.nextSong(null));
    }

    @Test
    void nextSong_repeatAlbum_refillsFromCurrentSongAlbum() {
        Album album = new Album("Album", "standard");
        album.getSongs().add(songA);
        album.getSongs().add(songB);
        album.getSongs().add(songC);
        unlockManager.getUnlockedAlbums().add("Album");
        unlockManager.getUnlockedSongs().add("Song A");
        unlockManager.getUnlockedSongs().add("Song B");
        unlockManager.getUnlockedSongs().add("Song C");
        QueueManager qm = new QueueManager(new AlbumLibrary(List.of(album)), unlockManager);
        qm.setRepeatMode(RepeatMode.ALBUM);
        qm.add(songA);
        qm.add(songB);

        assertEquals(songA, qm.nextSong(songA));
        assertEquals(songB, qm.nextSong(songA));
        assertEquals(songA, qm.nextSong(songA));
        assertEquals(songB, qm.nextSong(songA));
        assertEquals(songC, qm.nextSong(songA));
    }

    @Test
    void nextSong_repeatAlbum_unknownCurrentSong_doesNotRefill() {
        Album album = new Album("Album", "standard");
        album.getSongs().add(songA);
        QueueManager qm = new QueueManager(new AlbumLibrary(List.of(album)), unlockManager);
        qm.setRepeatMode(RepeatMode.ALBUM);
        qm.add(songA);

        assertEquals(songA, qm.nextSong(null));
        assertNull(qm.nextSong(null));
    }

    @Test
    void setRepeatModeOff_clearsSnapshot() {
        QueueManager qm = new QueueManager(new AlbumLibrary(List.of()), unlockManager);
        qm.setRepeatMode(RepeatMode.QUEUE);
        qm.add(songA);
        qm.recordSnapshot(qm.poll());
        assertEquals(songA, qm.nextSong(null));

        qm.setRepeatMode(RepeatMode.OFF);
        qm.setRepeatMode(RepeatMode.QUEUE);
        qm.add(songB);
        qm.recordSnapshot(qm.poll());

        assertEquals(songB, qm.nextSong(null));
        assertEquals(songB, qm.nextSong(null));
    }

    @Test
    void addFirst_insertsAtFront() {
        QueueManager qm = new QueueManager(new AlbumLibrary(List.of()), unlockManager);
        qm.add(songA);
        qm.add(songB);
        qm.addFirst(songC);

        assertEquals(List.of(songC, songA, songB), qm.asList());
    }

    @Test
    void move_toLowerIndex() {
        QueueManager qm = new QueueManager(new AlbumLibrary(List.of()), unlockManager);
        qm.add(songA);
        qm.add(songB);
        qm.add(songC);

        qm.move(2, 0);

        assertEquals(List.of(songC, songA, songB), qm.asList());
    }

    @Test
    void move_toHigherIndexWithinBounds() {
        QueueManager qm = new QueueManager(new AlbumLibrary(List.of()), unlockManager);
        qm.add(songA);
        qm.add(songB);
        qm.add(songC);

        qm.move(0, 2);

        assertEquals(List.of(songB, songA, songC), qm.asList());
    }

    @Test
    void move_toEndBoundary() {
        QueueManager qm = new QueueManager(new AlbumLibrary(List.of()), unlockManager);
        qm.add(songA);
        qm.add(songB);
        qm.add(songC);

        qm.move(0, 3);

        assertEquals(List.of(songB, songC, songA), qm.asList());
    }

    @Test
    void move_outOfBoundsSource_isNoOp() {
        QueueManager qm = new QueueManager(new AlbumLibrary(List.of()), unlockManager);
        qm.add(songA);
        qm.add(songB);

        qm.move(5, 0);

        assertEquals(List.of(songA, songB), qm.asList());
    }

    @Test
    void shuffle_preservesAllElements() {
        QueueManager qm = new QueueManager(new AlbumLibrary(List.of()), unlockManager);
        qm.add(songA);
        qm.add(songB);
        qm.add(songC);

        qm.shuffle();

        assertEquals(3, qm.size());
        assertTrue(qm.asList().containsAll(List.of(songA, songB, songC)));
    }

    @Test
    void toEntries_exportsTitleAndType() {
        QueueManager qm = new QueueManager(new AlbumLibrary(List.of()), unlockManager);
        qm.add(songA);
        qm.add(songB);

        List<Map<String, String>> entries = qm.toEntries();

        assertEquals(2, entries.size());
        assertEquals("Song A", entries.get(0).get("title"));
        assertEquals("standard", entries.get(0).get("type"));
        assertEquals("Song B", entries.get(1).get("title"));
        assertEquals("standard", entries.get(1).get("type"));
    }

    @Test
    void replaceAll_swapsQueueContents() {
        QueueManager qm = new QueueManager(new AlbumLibrary(List.of()), unlockManager);
        qm.add(songA);

        qm.replaceAll(List.of(songB, songC));

        assertEquals(List.of(songB, songC), qm.asList());
    }
}
