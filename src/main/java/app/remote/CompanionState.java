/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.remote;

import com.google.gson.Gson;

import java.util.List;

/**
 * Immutable snapshot of the desktop player state, serialized to JSON and
 * pushed to connected phones. The stream path is relative to the companion
 * server (the phone prefixes it with the desktop's address).
 */
public record CompanionState(
        String songTitle,
        String album,
        String streamPath,
        long durationMs,
        long positionMs,
        boolean playing,
        int volume,
        List<String> queue) {

    private static final Gson GSON = new Gson();

    public String toJson() {
        return GSON.toJson(this);
    }
}