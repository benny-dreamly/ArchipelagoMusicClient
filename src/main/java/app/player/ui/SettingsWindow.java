/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
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

        VBox root = new VBox(8);
        root.setPadding(new Insets(12));
        root.getChildren().addAll(
                new Label("Appearance"), darkModeCheck,
                new Label("Gameplay"), deathLinkCheck,
                new Label("Session"), sessionRestoreCheck
        );

        scene = new Scene(root, 300, 160);
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

    /** Adds or removes the dark stylesheet on the settings window itself. */
    public void setDarkMode(boolean dark) {
        String css = getClass().getResource(DARK_CSS).toExternalForm();
        scene.getStylesheets().remove(css);
        if (dark) {
            scene.getStylesheets().add(css);
        }
    }
}
