package app.player;

import static app.MusicAppDemo.LOGGER;

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
        LOGGER.info("Setting file path for {} -> {}", title, absolutePath);
        this.filePath = absolutePath;
    }

    public String getFilePath() {
        return filePath;
    }
}
