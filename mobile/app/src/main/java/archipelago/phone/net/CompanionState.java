package archipelago.phone.net;

import com.google.gson.Gson;

import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * Mirror of the desktop's {@code CompanionState} record. The Gson field names
 * must stay in sync with {@code app.remote.CompanionState}.
 */
public final class CompanionState {

    private static final Gson GSON = new Gson();

    @SerializedName("songTitle")
    public String songTitle;

    @SerializedName("album")
    public String album;

    @SerializedName("streamPath")
    public String streamPath;

    @SerializedName("durationMs")
    public long durationMs;

    @SerializedName("positionMs")
    public long positionMs;

    @SerializedName("playing")
    public boolean playing;

    @SerializedName("volume")
    public int volume;

    @SerializedName("queue")
    public List<String> queue;

    @SerializedName("activeSource")
    public String activeSource;

    public static CompanionState fromJson(String json) {
        return GSON.fromJson(json, CompanionState.class);
    }
}