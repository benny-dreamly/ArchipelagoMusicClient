package app.logic;

import app.player.Album;
import app.player.Song;
import app.util.AlbumLibrary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class QueueManager {

    public enum RepeatMode {
        OFF,
        QUEUE,
        SONG,
        ALBUM;

        public RepeatMode next() {
            return switch (this) {
                case OFF -> QUEUE;
                case QUEUE -> SONG;
                case SONG -> ALBUM;
                case ALBUM -> OFF;
            };
        }

        public String label() {
            return switch (this) {
                case OFF -> "No Repeat";
                case QUEUE -> "Repeat Queue";
                case SONG -> "Repeat Song";
                case ALBUM -> "Repeat Album";
            };
        }
    }

    @SuppressWarnings("JdkObsolete")
    private final Queue<Song> playQueue = new LinkedList<>();

    @SuppressWarnings("JdkObsolete")
    private List<Song> queueSnapshot = null;

    private RepeatMode repeatMode = RepeatMode.OFF;

    private final AlbumLibrary library;
    private final UnlockManager unlockManager;

    public QueueManager(AlbumLibrary library, UnlockManager unlockManager) {
        this.library = library;
        this.unlockManager = unlockManager;
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(RepeatMode mode) {
        this.repeatMode = mode;
        if (mode == RepeatMode.OFF) {
            queueSnapshot = null;
        }
    }

    public Song poll() {
        return playQueue.poll();
    }

    public Song nextSong(Song currentSong) {
        Song next = playQueue.poll();
        if (next == null && repeatMode != RepeatMode.OFF) {
            if (repeatMode == RepeatMode.QUEUE && queueSnapshot != null) {
                playQueue.addAll(queueSnapshot);
            } else if (repeatMode == RepeatMode.ALBUM && currentSong != null) {
                Album album = library.getAlbumForSong(currentSong.getTitle());
                if (album != null) {
                    playQueue.addAll(album.getQueueableSongs(
                            unlockManager.getEnabledSets(),
                            unlockManager.getUnlockedSongs(),
                            unlockManager.getUnlockedAlbums()));
                }
            }
            next = playQueue.poll();
        }
        return next;
    }

    public void recordSnapshot(Song song) {
        if (repeatMode == RepeatMode.QUEUE && queueSnapshot == null) {
            LinkedList<Song> snapshot = new LinkedList<>();
            snapshot.add(song);
            snapshot.addAll(playQueue);
            queueSnapshot = snapshot;
        }
    }

    public void add(Song song) {
        playQueue.add(song);
    }

    public void addAll(List<Song> songs) {
        playQueue.addAll(songs);
    }

    public void addFirst(Song song) {
        LinkedList<Song> temp = new LinkedList<>(playQueue);
        temp.addFirst(song);
        playQueue.clear();
        playQueue.addAll(temp);
    }

    public void replaceAll(List<Song> songs) {
        playQueue.clear();
        playQueue.addAll(songs);
    }

    public void remove(Song song) {
        playQueue.remove(song);
    }

    public void move(int fromIndex, int toIndex) {
        int i = 0;
        Song song = null;
        for (Song s : playQueue) {
            if (i == fromIndex) { song = s; break; }
            i++;
        }
        if (song == null) return;
        playQueue.remove(song);
        // reinsert at toIndex, adjusting if removing shifted the position
        int adjusted = toIndex > fromIndex ? toIndex - 1 : toIndex;
        LinkedList<Song> temp = new LinkedList<>(playQueue);
        temp.add(adjusted, song);
        playQueue.clear();
        playQueue.addAll(temp);
    }

    public void shuffle() {
        LinkedList<Song> temp = new LinkedList<>(playQueue);
        Collections.shuffle(temp);
        playQueue.clear();
        playQueue.addAll(temp);
    }

    public void clear() {
        playQueue.clear();
    }

    public boolean isEmpty() {
        return playQueue.isEmpty();
    }

    public int size() {
        return playQueue.size();
    }

    public List<Song> asList() {
        return List.copyOf(playQueue);
    }

    public List<Map<String, String>> toEntries() {
        List<Map<String, String>> entries = new ArrayList<>();
        for (Song song : playQueue) {
            Map<String, String> entry = new HashMap<>();
            entry.put("title", song.getTitle());
            entry.put("type", song.getType());
            entries.add(entry);
        }
        return entries;
    }
}
