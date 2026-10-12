/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player.json;

import app.player.Album;
import app.player.Song;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicLibraryConverterTest {

    private SongJSON song(String name, String region, List<String> categories, List<String> requires) {
        SongJSON songJSON = new SongJSON();
        songJSON.name = name;
        songJSON.region = region;
        songJSON.category = categories;
        songJSON.requires = requires;
        return songJSON;
    }

    @Test
    void convert_groupsSongsByRegion_preservingOrder() {
        MusicLibraryConverter converter = new MusicLibraryConverter();
        List<SongJSON> songs = List.of(
                song("Tim McGraw", "Taylor Swift", List.of("Taylor Swift"), List.of()),
                song("Picture to Burn", "Taylor Swift", List.of("Taylor Swift"), List.of()),
                song("Fifteen", "Fearless", List.of("Fearless"), List.of()));

        MusicLibraryJSON library = converter.convert(songs, Map.of(), Map.of());

        assertEquals(2, library.albums.size());
        assertEquals("Taylor Swift", library.albums.get(0).name);
        assertEquals(List.of("Tim McGraw", "Picture to Burn"),
                library.albums.get(0).songs.stream().map(s -> s.title).toList());
        assertEquals("Fearless", library.albums.get(1).name);
        assertNull(library.albums.get(0).songs.get(0).requires);
    }

    @Test
    void convert_infersSongAndAlbumTypesFromCategories() {
        MusicLibraryConverter converter = new MusicLibraryConverter();
        List<SongJSON> songs = List.of(
                song("Style", "1989", List.of("1989"), List.of()),
                song("Love Story (Taylor's Version)", "Fearless (Taylor's Version)",
                        List.of("Fearless (Taylor's Version)", "Re-recordings"), List.of()),
                song("Mr. Perfectly Fine", "Fearless (Taylor's Version)",
                        List.of("Fearless (Taylor's Version)", "Re-recordings", "Vault Tracks"), List.of()),
                song("Interlude", "Misc", List.of("Short Songs"), List.of()));

        MusicLibraryJSON library = converter.convert(songs, Map.of(), Map.of());

        assertEquals("normal", library.albums.get(0).songs.get(0).type);
        assertEquals("standard", library.albums.get(0).type);

        MusicLibraryJSON.AlbumJSON fearlessly = library.albums.get(1);
        assertEquals("re-recording", fearlessly.type);
        assertEquals("re-recording", fearlessly.songs.get(0).type);
        assertEquals("vault", fearlessly.songs.get(1).type);

        assertEquals("short", library.albums.get(2).songs.get(0).type);
        assertEquals("standard", library.albums.get(2).type);
    }

    @Test
    void convert_firstSongCategoryDeterminesAlbumType() {
        MusicLibraryConverter converter = new MusicLibraryConverter();
        List<SongJSON> songs = List.of(
                song("Track 1", "Misc", List.of("Misc"), List.of()),
                song("Track 2", "Misc", List.of("Misc", "Re-recordings"), List.of()));

        MusicLibraryJSON library = converter.convert(songs, Map.of(), Map.of());

        assertEquals("standard", library.albums.get(0).type);
        assertEquals("re-recording", library.albums.get(0).songs.get(1).type);
    }

    @Test
    void convert_bonusLocationsRegion_droppedIntoBonusList() {
        MusicLibraryConverter converter = new MusicLibraryConverter();
        List<SongJSON> songs = List.of(
                song("Welcome to Dreamland", "Bonus Locations", List.of(), List.of()),
                song("Dreamland", "Dreamland", List.of("Dreamland"), List.of()));

        MusicLibraryJSON library = converter.convert(songs, Map.of(), Map.of());

        assertEquals(1, library.albums.size());
        assertEquals(List.of("Welcome to Dreamland"), library.bonus_locations);
    }

    @Test
    void convert_blankRegion_groupsUnderSongs() {
        MusicLibraryConverter converter = new MusicLibraryConverter();
        List<SongJSON> songs = List.of(
                song("Track 1", null, List.of(), List.of()),
                song("Track 2", "   ", List.of(), List.of()));

        MusicLibraryJSON library = converter.convert(songs, Map.of(), Map.of());

        assertEquals(1, library.albums.size());
        assertEquals("Songs", library.albums.get(0).name);
        assertEquals(2, library.albums.get(0).songs.size());
    }

    @Test
    void convert_mergesMetadataAndAlbumFolders() {
        MusicLibraryConverter converter = new MusicLibraryConverter();
        Map<String, AlbumMetadata> metadata = Map.of(
                "Fearless (Taylor's Version)", new AlbumMetadata(true));
        Map<String, String> folders = Map.of(
                "Fearless (Taylor's Version)", "/music/taylor/fearless-tv");
        List<SongJSON> songs = List.of(
                song("Love Story (Taylor's Version)", "Fearless (Taylor's Version)",
                        List.of("Fearless (Taylor's Version)", "Re-recordings"), List.of()));

        MusicLibraryJSON library = converter.convert(songs, metadata, folders);

        MusicLibraryJSON.AlbumJSON album = library.albums.get(0);
        assertTrue(album.full_album_unlock);
        assertEquals("/music/taylor/fearless-tv", album.path);
        assertEquals("re-recording", album.type);
    }

    @Test
    void convert_defaultsMetdataAndFolders_whenAbsent() {
        MusicLibraryConverter converter = new MusicLibraryConverter();
        List<SongJSON> songs = List.of(
                song("Style", "1989", List.of("1989"), List.of()));

        MusicLibraryJSON library = converter.convert(songs, Map.of(), Map.of());

        MusicLibraryJSON.AlbumJSON album = library.albums.get(0);
        assertFalse(album.full_album_unlock);
        assertEquals("", album.path);
        assertEquals("standard", album.type);
    }

    @Test
    void convert_normalizesRequires() {
        MusicLibraryConverter converter = new MusicLibraryConverter();
        List<SongJSON> songs = List.of(
                song("Mr. Perfectly Fine", "Fearless (Taylor's Version)",
                        List.of("Re-recordings", "Vault Tracks"), List.of("|Vault Tracks|")),
                song("Some Chords", "5 Years of mau5", List.of(), List.of("|Disc 1|")),
                song("Ghosts 'n' Stuff - Extended Mix", "5 Years of mau5", List.of(), List.of("Disc 1")));

        MusicLibraryJSON library = converter.convert(songs, Map.of(), Map.of());

        assertEquals("|Vault Tracks|", library.albums.get(0).songs.get(0).requires);
        assertEquals("|Disc 1|", library.albums.get(1).songs.get(0).requires);
        assertEquals("Disc 1", library.albums.get(1).songs.get(1).requires);
    }

    @Test
    void write_replacesExistingFile_andLeavesNoTempFiles(@TempDir Path tempDir) throws Exception {
        Path out = tempDir.resolve("music_library.json");
        Files.writeString(out, "{}", StandardCharsets.UTF_8);

        MusicLibraryConverter converter = new MusicLibraryConverter();
        MusicLibraryJSON library = converter.convert(
                List.of(song("Style", "1989", List.of("1989"), List.of())), Map.of(), Map.of());
        converter.write(library, out.toFile());

        assertEquals(1, new MusicLibraryLoader().loadFromFile(out.toFile()).size());
        try (var stream = Files.list(tempDir)) {
            assertEquals(1, stream.filter(p -> p.getFileName().toString().contains("music_library.json")).count());
        }
    }

    @Test
    void convert_malformedAlbumFolders_usesEmptyFolders(@TempDir Path tempDir) throws Exception {
        Path locations = tempDir.resolve("locations.json");
        Files.writeString(locations, """
                [ { "name": "Style", "region": "1989", "category": ["1989"], "requires": [] } ]
                """.trim(), StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("albumFolders.json"), "{ not json",
                StandardCharsets.UTF_8);

        MusicLibraryConverter converter = new MusicLibraryConverter();
        MusicLibraryJSON library = converter.convert(locations.toFile(), tempDir.toFile());

        assertEquals("", library.albums.get(0).path);
    }

    @Test
    void roundtrip_fileToMusicLibraryFile(@TempDir Path tempDir) throws Exception {
        Path locations = tempDir.resolve("locations.json");
        Files.writeString(locations, """
                [
                  { "name": "Style", "region": "1989", "category": ["1989"], "requires": [] },
                  { "name": "Mr. Perfectly Fine", "region": "Fearless (Taylor's Version)",
                    "category": ["Fearless (Taylor's Version)", "Re-recordings", "Vault Tracks"],
                    "requires": ["|Vault Tracks|"] }
                ]
                """.trim(), StandardCharsets.UTF_8);
        Path metadata = tempDir.resolve("album_metadata.json");
        Files.writeString(metadata, """
                { "1989": { "fullAlbumUnlock": true, "type": "album" } }
                """.trim(), StandardCharsets.UTF_8);
        Path folders = tempDir.resolve("albumFolders.json");
        Files.writeString(folders, """
                { "1989": "/music/1989" }
                """.trim(), StandardCharsets.UTF_8);
        Path out = tempDir.resolve("music_library.json");

        MusicLibraryConverter converter = new MusicLibraryConverter();
        MusicLibraryJSON library = converter.convert(locations.toFile(), tempDir.toFile());
        converter.write(library, out.toFile());

        List<Album> albums = new MusicLibraryLoader().loadFromFile(out.toFile());
        assertEquals(2, albums.size());

        Album nine = albums.stream().filter(a -> a.getName().equals("1989")).findFirst().orElseThrow();
        assertTrue(nine.isFullAlbumUnlock());
        assertEquals("/music/1989", nine.getFolderPath());
        assertEquals("standard", nine.getType());

        Album fearlessly = albums.stream().filter(a -> a.getName().equals("Fearless (Taylor's Version)"))
                .findFirst().orElseThrow();
        assertEquals("re-recording", fearlessly.getType());
        Song vault = fearlessly.getSongs().get(0);
        assertEquals("vault", vault.getType());
        assertEquals(Set.of("Vault Tracks"), vault.getRequiredItems());
    }

    @Test
    void roundtrip_manualLocationsObject_withData(@TempDir Path tempDir) throws Exception {
        Path locations = tempDir.resolve("locations.json");
        Files.writeString(locations, """
                {
                  "$schema": "https://example.com/locations.schema.json",
                  "data": [
                    { "name": "Some Chords", "region": "5 Years of mau5",
                      "category": ["5 Years of mau5"], "requires": "|Disc 1|" }
                  ]
                }
                """.trim(), StandardCharsets.UTF_8);
        Path out = tempDir.resolve("music_library.json");

        MusicLibraryConverter converter = new MusicLibraryConverter();
        MusicLibraryJSON library = converter.convert(locations.toFile(), tempDir.toFile());
        converter.write(library, out.toFile());

        List<Album> albums = new MusicLibraryLoader().loadFromFile(out.toFile());
        assertEquals(1, albums.size());
        assertEquals("5 Years of mau5", albums.get(0).getName());
        assertEquals(Set.of("Disc 1"), albums.get(0).getSongs().get(0).getRequiredItems());
    }
}