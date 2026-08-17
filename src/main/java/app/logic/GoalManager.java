package app.logic;

import app.player.Album;
import app.player.Song;
import io.github.archipelagomw.Client;
import io.github.archipelagomw.ClientStatus;
import io.github.archipelagomw.network.client.SetPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GoalManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoalManager.class);
    private static final String PLAYED_SONGS_KEY = "musictools/played_songs_";

    private final Set<String> playedSongs = new HashSet<>();
    private final Set<String> playedAlbums = new HashSet<>();

    private final UnlockManager unlockManager;
    private final List<Album> albums;

    private boolean goalSent = false;

    public GoalManager(UnlockManager unlockManager, List<Album> albums) {
        this.unlockManager = unlockManager;
        this.albums = albums;
    }

    public void markPlayed(String songTitle, String albumName, Client client) {
        if (goalSent) return;

        boolean isNew = playedSongs.add(songTitle);
        LOGGER.info("Marked song as played: {} (album: {}) [new={}]", songTitle, albumName, isNew);

        persistToServer(client);
        checkAlbumProgress(albumName);
        checkGoal(client);
    }

    private void persistToServer(Client client) {
        if (!client.isConnected()) {
            LOGGER.warn("Cannot persist played songs: not connected");
            return;
        }

        String key = PLAYED_SONGS_KEY + client.getSlot();
        SetPacket packet = new SetPacket(key, new ArrayList<String>());
        packet.addDataStorageOperation(SetPacket.Operation.REPLACE, new ArrayList<>(playedSongs));
        int requestId = client.dataStorageSet(packet);
        LOGGER.info("Persisted {} played songs to server (key={}, requestId={})", playedSongs.size(), key, requestId);
    }

    private void checkAlbumProgress(String albumName) {
        Album album = null;
        for (Album a : albums) {
            if (a.getName().equals(albumName)) {
                album = a;
                break;
            }
        }
        if (album == null) return;

        if (isAlbumFullyPlayed(album)) {
            playedAlbums.add(albumName);
            LOGGER.info("Album fully played: {}", albumName);
        }
    }

    private boolean isAlbumFullyPlayed(Album album) {
        int totalSongs = 0;
        int playedCount = 0;
        for (Song song : album.getSongs()) {
            if (!unlockManager.getEnabledSets().contains(song.getType())) continue;
            totalSongs++;
            if (playedSongs.contains(song.getTitle())) playedCount++;
        }
        if (totalSongs > 0 && playedCount % 5 == 0 && playedCount > 0) {
            LOGGER.info("Album '{}' progress: {}/{} songs played", album.getName(), playedCount, totalSongs);
        }
        return totalSongs > 0 && playedCount == totalSongs;
    }

    private void checkGoal(Client client) {
        if (goalSent) return;

        boolean allPlayed = true;
        int enabledAlbumCount = 0;
        int fullyPlayedCount = 0;
        for (Album album : albums) {
            if (!unlockManager.getEnabledAlbums().contains(album.getName())) continue;
            enabledAlbumCount++;
            if (isAlbumFullyPlayed(album)) {
                fullyPlayedCount++;
            } else {
                allPlayed = false;
            }
        }

        LOGGER.debug("Goal check: {}/{} enabled albums fully played", fullyPlayedCount, enabledAlbumCount);

        if (allPlayed && enabledAlbumCount > 0) {
            LOGGER.info("GOAL MET: All songs in all {} enabled albums have been played!", enabledAlbumCount);
            goalSent = true;
            client.setGameState(ClientStatus.CLIENT_GOAL);
        }
    }

    public void loadFromServer(Set<String> savedPlayedSongs) {
        playedSongs.clear();
        playedAlbums.clear();
        playedSongs.addAll(savedPlayedSongs);

        for (Album album : albums) {
            if (isAlbumFullyPlayed(album)) {
                playedAlbums.add(album.getName());
            }
        }

        LOGGER.info("Loaded {} played songs from server", playedSongs.size());
    }

    public void reset() {
        playedSongs.clear();
        playedAlbums.clear();
        goalSent = false;
    }

    public Set<String> getPlayedSongs() {
        return playedSongs;
    }

    public Set<String> getPlayedAlbums() {
        return playedAlbums;
    }

    public boolean isSongPlayed(String songTitle) {
        return playedSongs.contains(songTitle);
    }

    public boolean isAlbumPlayed(String albumName) {
        return playedAlbums.contains(albumName);
    }

    public boolean isGoalSent() {
        return goalSent;
    }
}
