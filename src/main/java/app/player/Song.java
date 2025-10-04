package app.player;

public class Song {

    private final String title;
    private final String type;

    private String filePath;

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

    public void setFilePath(String absolutePath) {
        this.filePath = filePath;
    }

    public String getFilePath() {
        return filePath;
    }
}
