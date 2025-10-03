package app.player;

public class Song {

    private final String title;
    private final String type;

    public Song(String title, String type) {
        this.title = title;
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }
}
