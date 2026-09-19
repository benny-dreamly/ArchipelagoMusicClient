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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FolderScannerTest {

    @TempDir
    Path tempDir;

    private static Album findByName(List<Album> albums, String name) {
        return albums.stream().filter(a -> a.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void testScanGroupsFilesByParentDirectory() throws IOException {
        File albumA = new File(tempDir.toFile(), "AlbumA");
        File albumB = new File(tempDir.toFile(), "AlbumB");
        assertTrue(albumA.mkdir());
        assertTrue(albumB.mkdir());

        File one = new File(albumA, "01 - Song One.mp3");
        File two = new File(albumA, "02 - Song Two.mp3");
        File three = new File(albumB, "03 - Song Three.m4a");
        assertTrue(one.createNewFile());
        assertTrue(two.createNewFile());
        assertTrue(three.createNewFile());

        List<Album> albums = FolderScanner.scanFolder(tempDir.toFile());

        assertEquals(2, albums.size());

        Album folderA = findByName(albums, "AlbumA");
        assertEquals(2, folderA.getSongs().size());
        assertEquals("Song One", folderA.getSongs().get(0).getTitle());
        assertEquals("Song Two", folderA.getSongs().get(1).getTitle());
        assertTrue(folderA.isFullAlbumUnlock());
        assertEquals(albumA.getAbsolutePath(), folderA.getFolderPath());
        assertEquals(one.getAbsolutePath(), folderA.getSongs().get(0).getFilePath());

        Album folderB = findByName(albums, "AlbumB");
        assertEquals(1, folderB.getSongs().size());
        assertEquals("Song Three", folderB.getSongs().get(0).getTitle());
    }

    @Test
    void testScanRecursesIntoSubdirectories() throws IOException {
        File artist = new File(tempDir.toFile(), "Artist");
        File albumX = new File(artist, "AlbumX");
        assertTrue(albumX.mkdirs());

        File deep = new File(albumX, "1 - Deep Cut.mp3");
        assertTrue(deep.createNewFile());

        List<Album> albums = FolderScanner.scanFolder(tempDir.toFile());

        assertEquals(1, albums.size());
        Album nested = findByName(albums, "AlbumX");
        assertEquals(1, nested.getSongs().size());
        assertEquals("Deep Cut", nested.getSongs().get(0).getTitle());
    }

    @Test
    void testScanIgnoresNonAudioFiles() throws IOException {
        File albumC = new File(tempDir.toFile(), "AlbumC");
        assertTrue(albumC.mkdir());

        assertTrue(new File(albumC, "cover.jpg").createNewFile());
        assertTrue(new File(albumC, "notes.txt").createNewFile());
        File bare = new File(albumC, "03.mp3");
        assertTrue(bare.createNewFile());

        List<Album> albums = FolderScanner.scanFolder(tempDir.toFile());

        Album folderC = findByName(albums, "AlbumC");
        assertEquals(1, folderC.getSongs().size());
        assertEquals("03", folderC.getSongs().get(0).getTitle());
    }

    @Test
    void testScanRootLevelFilesGroupedUnderRootName() throws IOException {
        File track = new File(tempDir.toFile(), "Loose Track.wav");
        assertTrue(track.createNewFile());

        List<Album> albums = FolderScanner.scanFolder(tempDir.toFile());

        assertEquals(1, albums.size());
        Album rootAlbum = findByName(albums, tempDir.toFile().getName());
        assertEquals(1, rootAlbum.getSongs().size());
        assertEquals("Loose Track", rootAlbum.getSongs().get(0).getTitle());
    }

    @Test
    void testSongsAreEmptyLocationsFullyUnlocked() throws IOException {
        File albumD = new File(tempDir.toFile(), "AlbumD");
        assertTrue(albumD.mkdir());
        assertTrue(new File(albumD, "Song.mp3").createNewFile());

        List<Album> albums = FolderScanner.scanFolder(tempDir.toFile());

        Album folderD = findByName(albums, "AlbumD");
        assertTrue(folderD.isFullAlbumUnlock());
        Song song = folderD.getSongs().get(0);
        assertEquals("", song.getLocation());
        assertEquals("", song.getRequires());
    }

    @Test
    void testDuplicateTitlesAcrossAlbumsRemainDistinct() throws IOException {
        File albumA = new File(tempDir.toFile(), "AlbumA");
        File albumB = new File(tempDir.toFile(), "AlbumB");
        assertTrue(albumA.mkdir());
        assertTrue(albumB.mkdir());

        File a = new File(albumA, "Track 1.mp3");
        File b = new File(albumB, "Track 1.mp3");
        assertTrue(a.createNewFile());
        assertTrue(b.createNewFile());

        List<Album> albums = FolderScanner.scanFolder(tempDir.toFile());

        Album folderA = findByName(albums, "AlbumA");
        Album folderB = findByName(albums, "AlbumB");
        assertEquals(1, folderA.getSongs().size());
        assertEquals(1, folderB.getSongs().size());
        assertEquals("Track 1", folderA.getSongs().get(0).getTitle());
        assertEquals("Track 1", folderB.getSongs().get(0).getTitle());
        assertEquals(a.getAbsolutePath(), folderA.getSongs().get(0).getFilePath());
        assertEquals(b.getAbsolutePath(), folderB.getSongs().get(0).getFilePath());
    }

    @Test
    void testScanFailsOnUnreadableDirectory() throws IOException {
        File locked = new File(tempDir.toFile(), "Locked");
        assertTrue(locked.mkdir());
        if (!locked.setReadable(false) || locked.canRead()) {
            // Owner/platform does not enforce directory read bits — skip
            assertTrue(locked.setReadable(true));
            return;
        }
        try {
            assertThrows(IOException.class, () -> FolderScanner.scanFolder(tempDir.toFile()));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            locked.setReadable(true);
        }
    }
}