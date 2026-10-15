/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.logic;

import app.player.Album;
import app.player.Song;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void testFindBestMatchMatchesAliasWhenTitleDoesNot() {
        // Title ("Some Chords - Dillon Francis Remix") does not resemble the file
        // name, but the declared alias ("Some Chords (Dillon Francis Remix)") does.
        Song song = new Song("Some Chords - Dillon Francis Remix", "normal", "Some Chords - Dillon Francis Remix",
                "", List.of("Some Chords (Dillon Francis Remix)"));
        List<Song> songs = List.of(song);

        Song matched = SongFileMatcher.findBestMatch("Some Chords (Dillon Francis Remix)", songs);
        assertNotNull(matched);
        assertEquals("Some Chords - Dillon Francis Remix", matched.getTitle());
    }

    @Test
    void testFindBestMatchAliasFallsBackWhenNoMatch() {
        Song song = new Song("Some Chords - Dillon Francis Remix", "normal", "Some Chords - Dillon Francis Remix",
                "", List.of("Some Chords (Dillon Francis Remix)"));
        List<Song> songs = List.of(song);

        assertNull(SongFileMatcher.findBestMatch("Totally Unrelated File Name", songs));
    }

    @Test
    void testFindBestMatchTitleWinsOverAlias() {
        Song song = new Song("Blank Space", "normal", "Blank Space",
                "", List.of("Something Completely Different"));
        List<Song> songs = List.of(song);

        assertEquals("Blank Space", SongFileMatcher.findBestMatch("blank space", songs).getTitle());
    }

    @Test
    void testFindBestMatchLaterTitleWinsOverEarlierAlias() {
        Song earlier = new Song("Alpha", "normal", "Alpha", "", List.of("Beta"));
        Song later = new Song("Beta", "normal");
        List<Song> songs = List.of(earlier, later);

        Song matched = SongFileMatcher.findBestMatch("beta", songs);
        assertNotNull(matched);
        assertEquals("Beta", matched.getTitle());
    }

    @Test
    void testFindBestMatchEarlierTitleStillWinsOverLaterAlias() {
        Song earlier = new Song("Beta", "normal");
        Song later = new Song("Alpha", "normal", "Alpha", "", List.of("Beta"));
        List<Song> songs = List.of(earlier, later);

        Song matched = SongFileMatcher.findBestMatch("beta", songs);
        assertNotNull(matched);
        assertEquals("Beta", matched.getTitle());
    }

    @Test
    void testAssignFilesToSongsMatchesUsingAlias() throws IOException {
        File albumFolder = tempDir.toFile();
        File audioFile = new File(albumFolder, "2-01 Some Chords (Dillon Francis Remix).mp3");
        assertTrue(audioFile.createNewFile());

        Song song = new Song("Some Chords - Dillon Francis Remix", "normal", "Some Chords - Dillon Francis Remix",
                "", List.of("Some Chords (Dillon Francis Remix)"));
        Album album = new Album("Test Album", "standard");
        album.getSongs().add(song);
        album.setFolderPath(albumFolder.getAbsolutePath());

        SongFileMatcher.assignFilesToSongs(List.of(album));

        assertEquals(audioFile.getAbsolutePath(), song.getFilePath());
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

    @Test
    void testBuildCandidatesStripsArtistAlbumTrackPrefix() {
        List<String> candidates = SongFileMatcher.buildCandidates(
                "Vargskelethor - Super Ghostbusters - 01 Ghostbusters.mp3", "");

        assertTrue(candidates.contains("Ghostbusters"), candidates.toString());
    }

    @Test
    void testBuildCandidatesStripsFolderPrefix() {
        List<String> candidates = SongFileMatcher.buildCandidates(
                "Vargskelethor - Super Ghostbusters - 01 Ghostbusters.mp3",
                "Vargskelethor - Super Ghostbusters - ");

        assertTrue(candidates.contains("Ghostbusters"), candidates.toString());
    }

    @Test
    void testCommonFolderPrefixTrimsToSeparator() {
        File[] files = {
                new File("Vargskelethor - Super Ghostbusters - 01 Ghostbusters.mp3"),
                new File("Vargskelethor - Super Ghostbusters - 02 Ghost Buster.mp3"),
        };

        assertEquals("Vargskelethor - Super Ghostbusters - ", SongFileMatcher.commonFolderPrefix(files));
    }

    @Test
    void testAssignFilesToSongsMatchesArtistAlbumTrackPrefix() throws IOException {
        File albumFolder = tempDir.toFile();
        File first = new File(albumFolder, "Vargskelethor - Super Ghostbusters - 01 Ghostbusters.mp3");
        File second = new File(albumFolder, "Vargskelethor - Super Ghostbusters - 02 Ghost Buster.mp3");
        assertTrue(first.createNewFile());
        assertTrue(second.createNewFile());

        Song song1 = new Song("Ghostbusters", "normal");
        Song song2 = new Song("Ghost Buster", "normal");
        Album album = new Album("Super Ghostbusters", "normal");
        album.getSongs().add(song1);
        album.getSongs().add(song2);
        album.setFolderPath(albumFolder.getAbsolutePath());

        SongFileMatcher.assignFilesToSongs(List.of(album));

        assertEquals(first.getAbsolutePath(), song1.getFilePath());
        assertEquals(second.getAbsolutePath(), song2.getFilePath());
    }

    @Test
    void testParseTrackNumberCommonLayouts() {
        assertEquals(5, SongFileMatcher.parseTrackNumber("05 Title.mp3"));
        assertEquals(5, SongFileMatcher.parseTrackNumber("5 - Title.mp3"));
        assertEquals(5, SongFileMatcher.parseTrackNumber("Artist - Album - 05 Title.mp3"));
        assertEquals(1, SongFileMatcher.parseTrackNumber("2-01 Title.mp3"));
        assertEquals(-1, SongFileMatcher.parseTrackNumber("No Number Here.mp3"));
    }

    @Test
    void testBuildPositionalMappingForCollidingAlbum() {
        File[] files = {
                new File("Vargskelethor - Super Ghostbusters - 01 Ghostbusters.mp3"),
                new File("Vargskelethor - Super Ghostbusters - 02 Ghost Buster.mp3"),
                new File("Vargskelethor - Super Ghostbusters - 03 Ghostdubster.mp3"),
        };
        Song song1 = new Song("Ghostbusters", "normal");
        Song song2 = new Song("Ghost Buster", "normal");
        Song song3 = new Song("Ghostdubster", "normal");

        Map<String, Song> mapping = SongFileMatcher.buildPositionalMapping(files, List.of(song1, song2, song3));

        assertEquals(song1, mapping.get(files[0].getName()));
        assertEquals(song2, mapping.get(files[1].getName()));
        assertEquals(song3, mapping.get(files[2].getName()));
    }

    @Test
    void testBuildPositionalMappingEmptyWithoutTrackNumbers() {
        File[] files = { new File("Ghostbusters.mp3"), new File("Ghost Buster.mp3") };
        List<Song> songs = List.of(new Song("Ghostbusters", "normal"), new Song("Ghost Buster", "normal"));

        assertTrue(SongFileMatcher.buildPositionalMapping(files, songs).isEmpty());
    }

    @Test
    void testFuzzyReturnsNullWhenTwoSongsNearlyEquidistant() {
        // "ghost dubster" is 1 edit from Ghostdubster and 2 from Ghost Buster;
        // with no positional signal the matcher refuses to guess.
        List<Song> songs = List.of(new Song("Ghost Buster", "normal"), new Song("Ghostdubster", "normal"));

        assertNull(SongFileMatcher.findBestMatch("ghost dubster", songs));
    }

    @Test
    void testPositionalSongBeatsAmbiguousFuzzy() {
        Song wrong = new Song("Ghost Buster", "normal");
        Song right = new Song("Ghostdubster", "normal");
        List<Song> songs = List.of(wrong, right);

        assertEquals(right, SongFileMatcher.findBestMatch(List.of("ghost dubster"), songs, right));
    }

    @Test
    void testAssignFilesToSongsDisambiguatesCollidingTitles() throws IOException {
        File albumFolder = tempDir.toFile();
        File first = new File(albumFolder, "Vargskelethor - Super Ghostbusters - 01 Ghostbusters.mp3");
        File second = new File(albumFolder, "Vargskelethor - Super Ghostbusters - 02 Ghostbusterz.mp3");
        assertTrue(first.createNewFile());
        assertTrue(second.createNewFile());

        Song song1 = new Song("Ghostbusters", "normal");
        Song song2 = new Song("Ghost Buster", "normal");
        Album album = new Album("Super Ghostbusters", "normal");
        album.getSongs().add(song1);
        album.getSongs().add(song2);
        album.setFolderPath(albumFolder.getAbsolutePath());

        SongFileMatcher.assignFilesToSongs(List.of(album));

        // "Ghostbusterz" fuzzy-matches song1 but positional mapping sends it to song2.
        assertEquals(first.getAbsolutePath(), song1.getFilePath());
        assertEquals(second.getAbsolutePath(), song2.getFilePath());
    }
}