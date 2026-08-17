package app.player.json;

import java.util.List;

public class MusicLibraryJSON {

    public String artist;
    public List<AlbumJSON> albums;

    public static class AlbumJSON {
        public String name;
        public String type;
        public boolean full_album_unlock;
        public String path;
        public List<SongJSON> songs;
    }

    public static class SongJSON {
        public String title;
        public String location;
        public String path;
        public String type;
    }
}
