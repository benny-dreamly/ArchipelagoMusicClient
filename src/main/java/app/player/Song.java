package app.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class Song {

    private static final Logger LOGGER = LoggerFactory.getLogger(Song.class);

    private final String title;
    private final String type;
    private final String location;
    private final String requires;

    private String filePath;

    public Song(String title, String type) {
        this(title, type, title, "");
    }

    public Song(String title, String type, String location) {
        this(title, type, location, "");
    }

    public Song(String title, String type, String location, String requires) {
        this.title = title;
        this.type = type;
        this.location = location;
        this.requires = requires != null ? requires : "";
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public String getLocation() {
        return location;
    }

    public String getRequires() {
        return requires;
    }

    public Set<String> getRequiredItems() {
        if (requires.isEmpty()) return Collections.emptySet();
        return Arrays.stream(requires.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean requiresMet(Set<String> receivedItems) {
        return getRequiredItems().stream().allMatch(receivedItems::contains);
    }

    public void setFilePath(String absolutePath) {
        LOGGER.info("Setting file path for {} -> {}", title, absolutePath);
        this.filePath = absolutePath;
    }

    public String getFilePath() {
        return filePath;
    }
}
