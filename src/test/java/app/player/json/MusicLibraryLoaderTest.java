/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player.json;

import app.player.Album;
import app.player.Song;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicLibraryLoaderTest {

    private final MusicLibraryLoader loader = new MusicLibraryLoader();

    @Test
    void testLoadFromReaderParsesAliases() {
        String json = """
                {
                  "artist": "Test Artist",
                  "albums": [
                    {
                      "name": "Test Album",
                      "songs": [
                        {
                          "title": "Some Chords - Dillon Francis Remix",
                          "aliases": ["Some Chords (Dillon Francis Remix)"]
                        }
                      ]
                    }
                  ]
                }
                """;
        List<Album> albums = loader.loadFromReader(new StringReader(json));
        assertEquals(1, albums.size());
        List<Song> songs = albums.get(0).getSongs();
        assertEquals(1, songs.size());
        assertEquals(List.of("Some Chords (Dillon Francis Remix)"), songs.get(0).getAliases());
    }

    @Test
    void testLoadFromReaderDefaultsToNoAliases() {
        String json = """
                {
                  "artist": "Test Artist",
                  "albums": [
                    {
                      "name": "Test Album",
                      "songs": [{"title": "Plain Title"}]
                    }
                  ]
                }
                """;
        List<Album> albums = loader.loadFromReader(new StringReader(json));
        assertTrue(albums.get(0).getSongs().get(0).getAliases().isEmpty());
    }
}