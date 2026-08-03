package app.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Song {

    private static final Logger LOGGER = LoggerFactory.getLogger(Song.class);

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
