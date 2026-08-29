package app.logic;

import app.player.Album;
import app.player.Song;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SongFileMatcherTest {

    @TempDir
    Path tempDir;

    @Test
    void testIsAudioFile() {
        assertTrue(SongFileMatcher.isAudioFile("song.mp3"));
        assertTrue(SongFileMatcher.isAudioFile("SONG.M4A"));
        assertTrue(SongFileMatcher.isAudioFile("track.wav"));
        assertFalse(SongFileMatcher.isAudioFile("cover.jpg"));
        assertFalse(SongFileMatcher.isAudioFile("notes.txt"));
    }

    @Test
    void testFindBestMatchExactAndCaseInsensitive() {
        Song song1 = new Song("Blank Space", "normal");
        Song song2 = new Song("Style", "normal");
        List<Song> songs = List.of(song1, song2);

        // Exact match (case insensitive)
        Song matched = SongFileMatcher.findBestMatch("blank space", songs);
        assertNotNull(matched);
        assertEquals("Blank Space", matched.getTitle());
    }

    @Test
    void testFindBestMatchFuzzyWithinThreshold() {
        Song song = new Song("Wildest Dreams", "normal");
        List<Song> songs = List.of(song);

        // "Wildest Dream" is edit distance 1 away from "Wildest Dreams" (< 5 threshold)
        Song matched = SongFileMatcher.findBestMatch("Wildest Dream", songs);
        assertNotNull(matched);
        assertEquals("Wildest Dreams", matched.getTitle());
    }

    @Test
    void testFindBestMatchFileOmittingFeatCreditMatches() {
        Song song = new Song("Snow On The Beach (feat. Lana Del Rey)", "normal");
        List<Song> songs = List.of(song);

        Song matched = SongFileMatcher.findBestMatch("Snow On The Beach", songs);
        assertNotNull(matched);
        assertEquals("Snow On The Beach (feat. Lana Del Rey)", matched.getTitle());
    }

    @Test
    void testFindBestMatchFileWithFeatCreditStillMatches() {
        Song song = new Song("Karma (feat. Ice Spice)", "normal");
        List<Song> songs = List.of(song);

        Song matched = SongFileMatcher.findBestMatch("Karma (feat. Ice Spice)", songs);
        assertNotNull(matched);
        assertEquals("Karma (feat. Ice Spice)", matched.getTitle());
    }

    @Test
    void testFindBestMatchFeatCreditNotStrippedFromVaultSuffix() {
        Song song = new Song("Is It Over Now? (Taylor's Version) (From The Vault)", "vault");
        List<Song> songs = List.of(song);

        Song matched = SongFileMatcher.findBestMatch("Is It Over Now? (Taylor's Version)", songs);
        assertNull(matched);
    }

    @Test
    void testFindBestMatchMidWordTruncatedFilenameMatches() {
        Song song = new Song("We Are Never Ever Getting Back Together", "normal");
        List<Song> songs = List.of(song);

        Song matched = SongFileMatcher.findBestMatch("We Are Never Ever Getting Back To", songs);
        assertNotNull(matched);
        assertEquals("We Are Never Ever Getting Back Together", matched.getTitle());
    }

    @Test
    void testFindBestMatchTooDistantReturnsNull() {
        Song song = new Song("Shake It Off", "normal");
        List<Song> songs = List.of(song);

        // Edit distance is way over threshold
        Song matched = SongFileMatcher.findBestMatch("Completely Different Song Title", songs);
        assertNull(matched);
    }

    @Test
    void testAssignFilesToSongsMatching() throws IOException {
        File albumFolder = tempDir.toFile();
        File audioFile = new File(albumFolder, "01 - Style.mp3");
        File textFile = new File(albumFolder, "cover.txt");
        assertTrue(audioFile.createNewFile());
        assertTrue(textFile.createNewFile());

        Song song = new Song("Style", "normal");
        Album album = new Album("1989", "re-recording");
        album.getSongs().add(song);
        album.setFolderPath(albumFolder.getAbsolutePath());

        SongFileMatcher.assignFilesToSongs(List.of(album));

        assertEquals(audioFile.getAbsolutePath(), song.getFilePath());
    }

    @Test
    void testAssignFilesToSongsInvalidOrNullFolderPath() {
        Song song = new Song("Style", "normal");

        Album nullPathAlbum = new Album("Album A", "normal");
        nullPathAlbum.getSongs().add(song);

        Album fakePathAlbum = new Album("Album B", "normal");
        fakePathAlbum.getSongs().add(song);
        fakePathAlbum.setFolderPath("/path/that/does/not/exist/anywhere");

        assertDoesNotThrow(() -> SongFileMatcher.assignFilesToSongs(List.of(nullPathAlbum, fakePathAlbum)));
        assertNull(song.getFilePath());
    }
}