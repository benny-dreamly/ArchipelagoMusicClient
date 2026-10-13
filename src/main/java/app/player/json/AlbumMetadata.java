/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player.json;

import com.google.gson.annotations.SerializedName;

public class AlbumMetadata {
    private boolean fullAlbumUnlock;
    private String type;

    @SerializedName(value = "requires_album_item", alternate = "requiresAlbumItem")
    private boolean requiresAlbumItem = true;

    @SuppressWarnings("unused")
    public AlbumMetadata() {
        this.fullAlbumUnlock = false; // default
        this.type = "album"; // default
        this.requiresAlbumItem = true; // default
    }

    @SuppressWarnings("unused")
    public AlbumMetadata(boolean fullAlbumUnlock, String type) {
        this.fullAlbumUnlock = fullAlbumUnlock;
        this.type = type;
    }

    public AlbumMetadata(boolean fullAlbumUnlock) {
        this.fullAlbumUnlock = fullAlbumUnlock;
    }

    public boolean isFullAlbumUnlock() {
        return fullAlbumUnlock;
    }

    public boolean requiresAlbumItem() {
        return requiresAlbumItem;
    }

    @SuppressWarnings("unused")
    public void setRequiresAlbumItem(boolean requiresAlbumItem) {
        this.requiresAlbumItem = requiresAlbumItem;
    }

    @SuppressWarnings("unused")
    public void setFullAlbumUnlock(boolean fullAlbumUnlock) {
        this.fullAlbumUnlock = fullAlbumUnlock;
    }

    @SuppressWarnings("unused")
    public String getType() {
        return type;
    }

    @SuppressWarnings("unused")
    public void setType(String type) {
        this.type = type;
    }
}