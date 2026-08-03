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
}
