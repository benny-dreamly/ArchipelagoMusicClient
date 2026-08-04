package app.player;

import app.player.json.AlbumMetadata;
import app.player.json.SongJSON;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AlbumConverterTest {

    private SongJSON createSong(String name, String region, List<String> categories) {
        SongJSON song = new SongJSON();
        song.name = name;
        song.region = region;
        song.category = categories != null ? categories : List.of();
        song.requires = List.of();
        return song;
    }

    @Test
    void convert_groupsSongsByRegion() {
        AlbumConverter converter = new AlbumConverter(Map.of());
        SongJSON song1 = createSong("Tim McGraw", "Taylor Swift", List.of("Taylor Swift"));
        SongJSON song2 = createSong("Picture to Burn", "Taylor Swift", List.of("Taylor Swift"));

        List<Album> albums = converter.convert(List.of(song1, song2));

        assertEquals(1, albums.size());
        assertEquals("Taylor Swift", albums.get(0).getName());
        assertEquals(2, albums.get(0).getSongs().size());
    }

    @Test
    void convert_nullOrBlankRegion_groupsUnderSongs() {
        AlbumConverter converter = new AlbumConverter(Map.of());
        SongJSON songNull = createSong("Track 1", null, List.of());
        SongJSON songBlank = createSong("Track 2", "   ", List.of());

        List<Album> albums = converter.convert(List.of(songNull, songBlank));

        assertEquals(1, albums.size());
        assertEquals("Songs", albums.get(0).getName());
        assertEquals(2, albums.get(0).getSongs().size());
    }

    @Test
    void convert_bonusLocationsRegion_skipsSong() {
        AlbumConverter converter = new AlbumConverter(Map.of());
        SongJSON bonus = createSong("Bonus Track", "Bonus Locations", List.of());
        SongJSON normal = createSong("Mine", "Speak Now", List.of("Speak Now"));

        List<Album> albums = converter.convert(List.of(bonus, normal));

        assertEquals(1, albums.size());
        assertEquals("Speak Now", albums.get(0).getName());
        assertEquals(1, albums.get(0).getSongs().size());
    }

    @Test
    void convert_categoryParsing_assignsCorrectSongAndAlbumTypes() {
        AlbumConverter converter = new AlbumConverter(Map.of());

        SongJSON shortSong = createSong("Interlude", "Misc", List.of("Short Songs"));
        SongJSON rerecordSong = createSong("Love Story (Taylor's Version)", "Fearless (Taylor's Version)", List.of("Re-recordings"));
        SongJSON standardSong = createSong("Style", "1989", List.of("1989"));

        List<Album> albums = converter.convert(List.of(shortSong, rerecordSong, standardSong));

        // Re-recordings check (both album & song type should be 'rerecording')
        Album rerecordAlbum = albums.stream().filter(a -> a.getName().equals("Fearless (Taylor's Version)")).findFirst().orElseThrow();
        assertEquals("rerecording", rerecordAlbum.getType());
        assertEquals("rerecording", rerecordAlbum.getSongs().get(0).getType());

        // Short Songs check (song type 'short', album type 'standard')
        Album shortAlbum = albums.stream().filter(a -> a.getName().equals("Misc")).findFirst().orElseThrow();
        assertEquals("standard", shortAlbum.getType());
        assertEquals("short", shortAlbum.getSongs().get(0).getType());

        // Default check (both 'standard')
        Album standardAlbum = albums.stream().filter(a -> a.getName().equals("1989")).findFirst().orElseThrow();
        assertEquals("standard", standardAlbum.getType());
        assertEquals("standard", standardAlbum.getSongs().get(0).getType());
    }

    @Test
    void convert_appliesMetadataFullAlbumUnlock() {
        Map<String, AlbumMetadata> metadata = Map.of(
                "1989", new AlbumMetadata(true)
        );
        AlbumConverter converter = new AlbumConverter(metadata);

        SongJSON song1 = createSong("Blank Space", "1989", List.of("1989"));
        SongJSON song2 = createSong("Cruel Summer", "Lover", List.of("Lover"));

        List<Album> albums = converter.convert(List.of(song1, song2));

        Album unlocked = albums.stream().filter(a -> a.getName().equals("1989")).findFirst().orElseThrow();
        Album locked = albums.stream().filter(a -> a.getName().equals("Lover")).findFirst().orElseThrow();

        assertTrue(unlocked.isFullAlbumUnlock());
        assertFalse(locked.isFullAlbumUnlock());
    }
}