package app.player;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AlbumTest {

    @Test
    void getQueueableSongs_returnsAllSongs_whenFullAlbumUnlock() {
        Album album = new Album("Test Album", "standard", true);
        album.addSong(new Song("Song A", "standard"));
        album.addSong(new Song("Song B", "standard"));

        List<Song> result = album.getQueueableSongs(
                Set.of("standard"), Set.of(), Set.of()
        );

        assertEquals(2, result.size());
    }

    @Test
    void getQueueableSongs_returnsNothing_whenAlbumLockedAndSongsLocked() {
        Album album = new Album("Test Album", "standard", false);
        album.addSong(new Song("Song A", "standard"));

        List<Song> result = album.getQueueableSongs(
                Set.of("standard"), Set.of(), Set.of()
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void getQueueableSongs_returnsNothing_whenTypeDisabled() {
        Album album = new Album("Test Album", "standard", true);
        album.addSong(new Song("Song A", "vault"));

        List<Song> result = album.getQueueableSongs(
                Set.of("standard"), Set.of(), Set.of()
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void getQueueableSongs_requiresBothSongAndAlbumUnlock_whenNotFullAlbumUnlock() {
        Album album = new Album("Test Album", "standard", false);
        Song song = new Song("Song A", "standard");
        album.addSong(song);

        // Song unlocked but album locked → cannot play
        List<Song> withSongUnlock = album.getQueueableSongs(
                Set.of("standard"), Set.of("Song A"), Set.of()
        );
        assertTrue(withSongUnlock.isEmpty());

        // Album unlocked but song locked → cannot play
        List<Song> withAlbumUnlock = album.getQueueableSongs(
                Set.of("standard"), Set.of(), Set.of("Test Album")
        );
        assertTrue(withAlbumUnlock.isEmpty());

        // Both unlocked → can play
        List<Song> bothUnlocked = album.getQueueableSongs(
                Set.of("standard"), Set.of("Song A"), Set.of("Test Album")
        );
        assertEquals(1, bothUnlocked.size());
    }

    @Test
    void getQueueableSongs_mixedSongTypes_onlyIncludesEnabledOnes() {
        Album album = new Album("Test Album", "standard", true);
        album.addSong(new Song("Song A", "standard"));
        album.addSong(new Song("Song B", "vault"));
        album.addSong(new Song("Song C", "short"));

        List<Song> result = album.getQueueableSongs(
                Set.of("standard", "short"), Set.of(), Set.of()
        );

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(s -> s.getTitle().equals("Song A")));
        assertTrue(result.stream().anyMatch(s -> s.getTitle().equals("Song C")));
    }

    @Test
    void getQueueableSongs_handlesEmptyAlbum() {
        Album album = new Album("Empty Album", "standard", true);

        List<Song> result = album.getQueueableSongs(
                Set.of("standard"), Set.of(), Set.of()
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void getQueueableSongs_filtersByAlbumUnlockName() {
        Album album = new Album("Specific Album", "standard", false);
        album.addSong(new Song("Song A", "standard"));

        // Album with different name unlocked → should not match
        List<Song> wrongAlbum = album.getQueueableSongs(
                Set.of("standard"), Set.of("Song A"), Set.of("Wrong Album")
        );
        assertTrue(wrongAlbum.isEmpty());

        // Correct album name unlocked → should match
        List<Song> correctAlbum = album.getQueueableSongs(
                Set.of("standard"), Set.of("Song A"), Set.of("Specific Album")
        );
        assertEquals(1, correctAlbum.size());
    }
}
