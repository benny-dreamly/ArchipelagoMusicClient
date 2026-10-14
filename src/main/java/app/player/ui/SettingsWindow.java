/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import static app.util.ConfigManager.loadDarkMode;
import static app.util.ConfigManager.loadDeathLink;
import static app.util.ConfigManager.loadSessionRestore;

/**
 * Non-modal settings window opened from the connection panel. Holds the
 * toggles that used to squat there (dark mode, deathlink) plus session
 * restore. Behavior wiring lives in MusicAppDemo; this class only owns the
 * controls and the window itself.
 */
public class SettingsWindow {

    private static final String DARK_CSS = "/dark.css";

    private final Stage stage;
    private final Scene scene;
    private final CheckBox darkModeCheck;
    private final CheckBox deathLinkCheck;
    private final CheckBox sessionRestoreCheck;
    private final Button convertLibraryButton;
    private final Button openConfigFolderButton;

    public SettingsWindow(Stage owner) {
        stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("Settings");
        stage.setResizable(false);

        darkModeCheck = new CheckBox("Dark Mode");
        darkModeCheck.setSelected(loadDarkMode());

        deathLinkCheck = new CheckBox("Deathlink");
        deathLinkCheck.setSelected(loadDeathLink());

        sessionRestoreCheck = new CheckBox("Restore session on reconnect");
        sessionRestoreCheck.setSelected(loadSessionRestore());
        sessionRestoreCheck.setTooltip(new Tooltip(
                "When connecting to the same game and slot, restore the queue\n"
                        + "and resume the current song at its previous position."));

        convertLibraryButton = new Button("Generate music_library.json from locations.json");
        convertLibraryButton.setMaxWidth(Double.MAX_VALUE);
        convertLibraryButton.setTooltip(new Tooltip("""
                Rebuilds the game folder's music_library.json from its locations.json,
                merging album_metadata.json and albumFolders.json when present.
                An existing music_library.json is backed up to .bak before overwriting."""));

        openConfigFolderButton = new Button("Open Config Folder");
        openConfigFolderButton.setMaxWidth(Double.MAX_VALUE);
        openConfigFolderButton.setTooltip(new Tooltip("""
                Opens this game's config folder in your file manager, where files such as
                music_library.json, locations.json, albumOrder.json and queue.json live."""));

        VBox root = new VBox(8);
        root.setPadding(new Insets(12));
        root.getChildren().addAll(
                new Label("Appearance"), darkModeCheck,
                new Label("Gameplay"), deathLinkCheck,
                new Label("Session"), sessionRestoreCheck,
                new Label("Library"), convertLibraryButton,
                new Label("Configuration"), openConfigFolderButton
        );

        scene = new Scene(root, 340, 360);
        if (darkModeCheck.isSelected()) {
            scene.getStylesheets().add(getClass().getResource(DARK_CSS).toExternalForm());
        }
        stage.setScene(scene);
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    public void hide() {
        stage.hide();
    }

    public CheckBox getDarkModeCheck() {
        return darkModeCheck;
    }

    public CheckBox getDeathLinkCheck() {
        return deathLinkCheck;
    }

    public CheckBox getSessionRestoreCheck() {
        return sessionRestoreCheck;
    }

    public Button getConvertLibraryButton() {
        return convertLibraryButton;
    }

    public Button getOpenConfigFolderButton() {
        return openConfigFolderButton;
    }

    /** Adds or removes the dark stylesheet on the settings window itself. */
    public void setDarkMode(boolean dark) {
        String css = getClass().getResource(DARK_CSS).toExternalForm();
        scene.getStylesheets().remove(css);
        if (dark) {
            scene.getStylesheets().add(css);
        }
    }
}
