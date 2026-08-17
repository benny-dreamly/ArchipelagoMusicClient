package app.logic;

import app.archipelago.SlotDataHelper;
import app.player.Album;
import app.player.Song;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UnlockManagerTest {

    private static final String SLOT_DATA_JSON = """
            {
              "slot_data_keys": {
                "1989": {"type": "album", "display_name": "1989"},
                "folklore": {"type": "album", "display_name": "folklore"},
                "re_recordings": {"type": "album", "display_name": "Re-recordings"},
                "include_short_songs": {"type": "song_category", "display_name": "Short Songs"},
                "include_vault_songs": {"type": "song_category", "display_name": "Vault Songs"}
              }
            }
            """;

    @TempDir
    Path tempDir;

    private UnlockManager unlockManager;
    private int onChangeCount;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(tempDir.resolve("slot_data.json"), SLOT_DATA_JSON, StandardCharsets.UTF_8);
        SlotDataHelper.loadSlotOptions(tempDir.toFile());
        onChangeCount = 0;
        unlockManager = new UnlockManager(() -> onChangeCount++);
    }

    @Test
    void canPlay_nullAlbum_unlockedSong_returnsTrue() {
        Song song = new Song("Lonely Track", "standard");
        unlockManager.unlockSong(song.getTitle());
        assertTrue(unlockManager.canPlay(song, null));
    }

    @Test
    void canPlay_nullAlbum_lockedSong_returnsFalse() {
        Song song = new Song("Lonely Track", "standard");
        assertFalse(unlockManager.canPlay(song, null));
    }

    @Test
    void canPlay_fullAlbumUnlockAlbumUnlocked_returnsTrue() {
        Song song = new Song("Shake It Off", "standard");
        Album album = new Album("1989", "standard", true);
        unlockManager.getUnlockedAlbums().add(album.getName());
        assertTrue(unlockManager.canPlay(song, album));
    }

    @Test
    void canPlay_fullAlbumUnlockAlbumLocked_returnsFalse() {
        Song song = new Song("Shake It Off", "standard");
        Album album = new Album("1989", "standard", true);
        assertFalse(unlockManager.canPlay(song, album));
    }

    @Test
    void canPlay_songAndAlbumUnlocked_returnsTrue() {
        Song song = new Song("Shake It Off", "standard");
        Album album = new Album("1989", "standard");
        unlockManager.unlockSong(song.getTitle());
        unlockManager.getUnlockedAlbums().add(album.getName());
        assertTrue(unlockManager.canPlay(song, album));
    }

    @Test
    void canPlay_songUnlockedAlbumLocked_returnsFalse() {
        Song song = new Song("Shake It Off", "standard");
        Album album = new Album("1989", "standard");
        unlockManager.unlockSong(song.getTitle());
        assertFalse(unlockManager.canPlay(song, album));
    }

    @Test
    void canPlay_songLockedAlbumUnlocked_returnsFalse() {
        Song song = new Song("Shake It Off", "standard");
        Album album = new Album("1989", "standard");
        unlockManager.getUnlockedAlbums().add(album.getName());
        assertFalse(unlockManager.canPlay(song, album));
    }

    @Test
    void canPlay_albumSongBothLocked_returnsFalse() {
        Song song = new Song("Shake It Off", "standard");
        Album album = new Album("1989", "standard");
        assertFalse(unlockManager.canPlay(song, album));
    }

    @Test
    void canQueue_typeEnabledAndUnlocked_returnsTrue() {
        Song song = new Song("Shake It Off", "standard");
        Album album = new Album("1989", "standard");
        unlockManager.unlockSong(song.getTitle());
        unlockManager.getUnlockedAlbums().add(album.getName());
        unlockManager.getEnabledSets().add("standard");
        assertTrue(unlockManager.canQueue(song, album));
    }

    @Test
    void canQueue_typeDisabled_returnsFalse() {
        Song song = new Song("Shake It Off", "standard");
        Album album = new Album("1989", "standard");
        unlockManager.unlockSong(song.getTitle());
        unlockManager.getUnlockedAlbums().add(album.getName());
        assertFalse(unlockManager.canQueue(song, album));
    }

    @Test
    void canQueue_fullAlbumUnlockTypeEnabledAlbumUnlocked_returnsTrue() {
        Song song = new Song("Shake It Off", "standard");
        Album album = new Album("1989", "standard", true);
        unlockManager.getEnabledSets().add("standard");
        unlockManager.getUnlockedAlbums().add(album.getName());
        assertTrue(unlockManager.canQueue(song, album));
    }

    @Test
    void canQueue_fullAlbumUnlockTypeEnabledAlbumLocked_returnsFalse() {
        Song song = new Song("Shake It Off", "standard");
        Album album = new Album("1989", "standard", true);
        unlockManager.getEnabledSets().add("standard");
        assertFalse(unlockManager.canQueue(song, album));
    }

    @Test
    void canQueue_albumLockedTypeEnabled_returnsFalse() {
        Song song = new Song("Shake It Off", "standard");
        Album album = new Album("1989", "standard");
        unlockManager.unlockSong(song.getTitle());
        unlockManager.getEnabledSets().add("standard");
        assertFalse(unlockManager.canQueue(song, album));
    }

    @Test
    void canQueue_fullAlbumUnlockTypeDisabled_returnsFalse() {
        Song song = new Song("Shake It Off", "standard");
        Album album = new Album("1989", "standard", true);
        assertFalse(unlockManager.canQueue(song, album));
    }

    @Test
    void glassAnimalsStyle_needsBothAlbumItemAndSongItem() {
        Album album = new Album("Dreamland", "standard");
        Song song = new Song("Heat Waves", "standard");
        album.addSong(song);

        // Song item received, but no album item yet (ItemListener case 3)
        unlockManager.unlockSong(song.getTitle());
        unlockManager.getEnabledSets().add(album.getType());
        assertFalse(unlockManager.canPlay(song, album));
        assertFalse(unlockManager.canQueue(song, album));

        // Album item received (ItemListener case 2: "Dreamland (Album)")
        unlockManager.getUnlockedAlbums().add(album.getName());
        assertTrue(unlockManager.canPlay(song, album));
        assertTrue(unlockManager.canQueue(song, album));
    }

    @Test
    void fullAlbumUnlockStyle_albumItemUnlocksWholeAlbum() {
        Album album = new Album("1989", "standard", true);
        Song song = new Song("Shake It Off", "standard");
        album.addSong(song);

        // Album item not received yet → nothing playable
        assertFalse(unlockManager.canPlay(song, album));
        assertFalse(unlockManager.canQueue(song, album));

        // Album item received → whole album playable, no per-song items needed (ItemListener case 1)
        unlockManager.getUnlockedAlbums().add(album.getName());
        unlockManager.getEnabledSets().add(album.getType());
        assertTrue(unlockManager.canPlay(song, album));
        assertTrue(unlockManager.canQueue(song, album));
    }

    @Test
    void unlockSong_addsTitleToUnlockedSongs() {
        unlockManager.unlockSong("Style");
        assertTrue(unlockManager.isSongUnlocked("Style"));
        assertEquals(1, onChangeCount);
    }

    @Test
    void unlockSong_alreadyUnlocked_doesNotFireOnChange() {
        unlockManager.unlockSong("Style");
        unlockManager.unlockSong("Style");
        assertEquals(1, onChangeCount);
    }

    @Test
    void unlockAlbum_unlocksSongsAndEnablesType() {
        Album album = new Album("1989", "standard");
        album.addSong(new Song("Style", "standard"));
        album.addSong(new Song("Blank Space", "standard"));

        unlockManager.unlockAlbum("1989", List.of(album));

        assertTrue(unlockManager.isSongUnlocked("Style"));
        assertTrue(unlockManager.isSongUnlocked("Blank Space"));
        assertTrue(unlockManager.isTypeEnabled("standard"));
        assertEquals(2, onChangeCount);
    }

    @Test
    void unlockAlbum_unknownAlbum_doesNothing() {
        Album album = new Album("1989", "standard");
        unlockManager.unlockAlbum("folklore", List.of(album));
        assertFalse(unlockManager.isAlbumUnlocked("folklore"));
        assertFalse(unlockManager.isAlbumUnlocked("1989"));
        assertEquals(0, onChangeCount);
    }

    @Test
    void applySlotData_enablesAlbumsFromSlotData() {
        Album tswift = new Album("1989", "standard");
        Album folklore = new Album("folklore", "standard");
        List<Album> albums = List.of(tswift, folklore);

        unlockManager.applySlotData(Map.of("1989", 1, "folklore", 0), albums);

        assertTrue(unlockManager.getEnabledAlbums().contains("1989"));
        assertFalse(unlockManager.getEnabledAlbums().contains("folklore"));
        assertTrue(unlockManager.isTypeEnabled("standard"));
        assertEquals(1, onChangeCount);
    }

    @Test
    void applySlotData_rerecordingsSlot_enablesRerecordingTypeAlbums() {
        Album rerecording = new Album("1989 (Taylor's Version)", "rerecording");
        Album folklore = new Album("folklore", "standard");
        List<Album> albums = List.of(rerecording, folklore);

        unlockManager.applySlotData(Map.of("re_recordings", 1), albums);

        assertTrue(unlockManager.getEnabledAlbums().contains("1989 (Taylor's Version)"));
        assertFalse(unlockManager.getEnabledAlbums().contains("folklore"));
    }

    @Test
    void filterSongCategories_shortSongsDisabled_removesShortSongs() {
        Album album = new Album("1989", "standard");
        Song normal = new Song("Shake It Off", "standard");
        Song shortSong = new Song("Bad Blood (Short)", "short");
        album.addSong(normal);
        album.addSong(shortSong);
        List<Album> albums = List.of(album);

        unlockManager.unlockSong(normal.getTitle());
        unlockManager.unlockSong(shortSong.getTitle());

        unlockManager.applySlotData(Map.of("include_short_songs", false), albums);

        assertTrue(unlockManager.isSongUnlocked("Shake It Off"));
        assertFalse(unlockManager.isSongUnlocked("Bad Blood (Short)"));
    }

    @Test
    void filterSongCategories_vaultSongsDisabled_removesVaultSongs() {
        Album album = new Album("1989", "standard");
        Song vaultSong = new Song("Wildest Dreams (Vault)", "vault");
        album.addSong(vaultSong);
        List<Album> albums = List.of(album);

        unlockManager.unlockSong(vaultSong.getTitle());

        unlockManager.applySlotData(Map.of("include_vault_songs", false), albums);

        assertFalse(unlockManager.isSongUnlocked("Wildest Dreams (Vault)"));
    }

    @Test
    void filterSongCategories_shortAndVaultEnabled_keepsAllSongs() {
        Album album = new Album("1989", "standard");
        Song normal = new Song("Shake It Off", "standard");
        Song shortSong = new Song("Bad Blood (Short)", "short");
        Song vaultSong = new Song("Wildest Dreams (Vault)", "vault");
        album.addSong(normal);
        album.addSong(shortSong);
        album.addSong(vaultSong);
        List<Album> albums = List.of(album);

        unlockManager.unlockSong(normal.getTitle());
        unlockManager.unlockSong(shortSong.getTitle());
        unlockManager.unlockSong(vaultSong.getTitle());

        unlockManager.applySlotData(Map.of("include_short_songs", true, "include_vault_songs", true), albums);

        assertTrue(unlockManager.isSongUnlocked("Shake It Off"));
        assertTrue(unlockManager.isSongUnlocked("Bad Blood (Short)"));
        assertTrue(unlockManager.isSongUnlocked("Wildest Dreams (Vault)"));
    }

    @Test
    void applyOfflineUnlocks_unlocksAllAlbumsAndSongs() {
        Album tswift = new Album("1989", "standard");
        tswift.addSong(new Song("Style", "standard"));
        Album folklore = new Album("folklore", "standard");
        folklore.addSong(new Song("cardigan", "standard"));
        List<Album> albums = List.of(tswift, folklore);

        unlockManager.applyOfflineUnlocks(albums);

        assertTrue(unlockManager.isAlbumUnlocked("1989"));
        assertTrue(unlockManager.isAlbumUnlocked("folklore"));
        assertTrue(unlockManager.isSongUnlocked("Style"));
        assertTrue(unlockManager.isSongUnlocked("cardigan"));
        assertTrue(unlockManager.isTypeEnabled("standard"));
        assertTrue(unlockManager.getEnabledAlbums().contains("1989"));
        assertTrue(unlockManager.getEnabledAlbums().contains("folklore"));
        assertEquals(1, onChangeCount);
    }

    @Test
    void applyOfflineUnlocks_clearsPreviousState() {
        Album tswift = new Album("1989", "standard");
        tswift.addSong(new Song("Style", "standard"));
        Album folklore = new Album("folklore", "standard");
        folklore.addSong(new Song("cardigan", "standard"));
        List<Album> albums = List.of(tswift, folklore);

        unlockManager.applySlotData(Map.of(), albums);
        unlockManager.applyOfflineUnlocks(albums);

        assertTrue(unlockManager.isSongUnlocked("Style"));
        assertTrue(unlockManager.isSongUnlocked("cardigan"));
        assertTrue(unlockManager.isAlbumUnlocked("1989"));
        assertTrue(unlockManager.isAlbumUnlocked("folklore"));
        assertTrue(unlockManager.getEnabledAlbums().contains("1989"));
        assertTrue(unlockManager.getEnabledAlbums().contains("folklore"));
    }
}
