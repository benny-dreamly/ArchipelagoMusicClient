package app.util;

import app.player.Album;
import app.player.Song;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class AlbumLibraryTest {

    private AlbumLibrary libraryWithTwoAlbums() {
        Album firstAlbum = new Album("Album One", "standard", true);
        firstAlbum.addSong(new Song("Shared Song", "standard"));
        firstAlbum.addSong(new Song("Song A", "standard"));
        Album secondAlbum = new Album("Album Two", "standard", true);
        secondAlbum.addSong(new Song("Shared Song", "standard"));
        return new AlbumLibrary(List.of(firstAlbum, secondAlbum));
    }

    @Test
    void getAlbumByName_returnsMatchingAlbum() {
        AlbumLibrary library = libraryWithTwoAlbums();
        Album result = library.getAlbumByName("Album One");
        assertEquals("Album One", result.getName());
    }

    @Test
    void getAlbumByName_unknownAlbum_returnsNull() {
        AlbumLibrary library = libraryWithTwoAlbums();
        Album result = library.getAlbumByName("Nonexistent Album");
        assertNull(result);
    }

    @Test
    void getAlbumByName_isCaseSensitive() {
        AlbumLibrary library = libraryWithTwoAlbums();
        Album result = library.getAlbumByName("album one");
        assertNull(result);
    }

    @Test
    void getAlbumForSong_returnsAlbumWhenSongPresent() {
        AlbumLibrary library = libraryWithTwoAlbums();
        Album result = library.getAlbumForSong("Song A");
        assertEquals("Album One", result.getName());
    }

    @Test
    void getAlbumForSong_unknownSong_returnsNull() {
        AlbumLibrary library = libraryWithTwoAlbums();
        Album result = library.getAlbumForSong("Unknown Track");
        assertNull(result);
    }

    @Test
    void getAlbumForSong_duplicateTitle_returnsFirstAlbum() {
        AlbumLibrary library = libraryWithTwoAlbums();
        Album result = library.getAlbumForSong("Shared Song");
        assertEquals("Album One", result.getName());
    }

    @Test
    void getSongByTitle_returnsSongWhenTitleMatches() {
        AlbumLibrary library = libraryWithTwoAlbums();
        Song result = library.getSongByTitle("Song A");
        assertEquals("Song A", result.getTitle());
    }

    @Test
    void getSongByTitle_unknownTitle_returnsNull() {
        AlbumLibrary library = libraryWithTwoAlbums();
        Song result = library.getSongByTitle("Unknown Track");
        assertNull(result);
    }

    @Test
    void getSongByTitle_returnsSameInstance() {
        AlbumLibrary library = libraryWithTwoAlbums();
        Song result = library.getSongByTitle("Song A");

        result.setFilePath("/path/to/song.mp3");

        assertEquals("/path/to/song.mp3", library.getSongByTitle("Song A").getFilePath());
    }

    @Test
    void emptyLibrary_returnsNullForLookups() {
        AlbumLibrary emptyLibrary = new AlbumLibrary(List.of());

        assertNull(emptyLibrary.getAlbumByName("Album One"));
        assertNull(emptyLibrary.getAlbumForSong("Song A"));
        assertNull(emptyLibrary.getSongByTitle("Song A"));
    }
}
