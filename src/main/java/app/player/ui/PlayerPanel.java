package app.player.ui;

import app.player.Song;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaPlayer;

public class PlayerPanel extends VBox {

    private final Label currentSongLabel;

    private final CheckBox enableSeekCheck;
    private final Slider progressSlider;
    private final Label elapsedLabel;
    private final Label durationLabel;

    private final HBox progressBox;

    private final ListView<String> queueListView;
    private final ScrollPane queueScrollPane;

    private final HBox queueButtons;
    private final Button playButton;
    private final Button pauseButton;
    private final Button removeSelectedBtn;
    private final Button clearQueueBtn;
    private final HBox playerButtons;

    public PlayerPanel() {
        super(6);
        setAlignment(Pos.CENTER_RIGHT);

        currentSongLabel = new Label("Currently Playing: None");

        enableSeekCheck = new CheckBox("Enable Seek Slider");
        enableSeekCheck.setSelected(false); // default off

        progressSlider = new Slider();
        progressSlider.setMin(0);
        progressSlider.setMax(1); // normalized
        progressSlider.setValue(0);
        progressSlider.setPrefWidth(400);
        progressSlider.setDisable(true);

        elapsedLabel = new Label("0:00");
        durationLabel = new Label("0:00");

        progressBox = new HBox(5, elapsedLabel, progressSlider, durationLabel);
        progressBox.setAlignment(Pos.CENTER);

        queueListView = new ListView<>();
        queueListView.setPrefHeight(120);

        queueScrollPane = new ScrollPane(queueListView);
        queueScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        queueScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // Fit the ListView nicely inside the ScrollPane
        queueScrollPane.setFitToHeight(true);
        queueListView.setMinWidth(Region.USE_PREF_SIZE);

        // Map vertical scroll to horizontal scroll
        queueScrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() != 0) {
                double h = queueScrollPane.getHvalue() - event.getDeltaY() * 0.005; // smoother scaling
                queueScrollPane.setHvalue(Math.min(Math.max(h, 0), 1));
                event.consume();
            }
        });

        playerButtons = new HBox(6);
        playButton = new Button("▶");
        pauseButton = new Button("⏸");
        playerButtons.getChildren().addAll(playButton, pauseButton);

        // Queue control buttons
        queueButtons = new HBox(6);
        removeSelectedBtn = new Button("Remove Selected");
        clearQueueBtn = new Button("Clear Queue");
        queueButtons.getChildren().addAll(removeSelectedBtn, clearQueueBtn);

        getChildren().addAll(currentSongLabel, enableSeekCheck , progressBox, playerButtons, new Label("Queue:"), queueScrollPane, queueButtons);
        setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(this, Priority.ALWAYS);

    }

    // ----- GETTERS -----
    public Button getPlayButton() { return playButton; }
    public Button getPauseButton() { return pauseButton; }
    public Button getRemoveSelectedBtn() { return removeSelectedBtn; }
    public Button getClearQueueBtn() { return clearQueueBtn; }

    public ListView<String> getQueueListView() { return queueListView; }

    public CheckBox getEnableSeekCheck() { return enableSeekCheck; }
    public Slider getProgressSlider() { return progressSlider; }

    public Label getCurrentSongLabel() { return currentSongLabel; }
    public Label getElapsedLabel() { return elapsedLabel; }
    public Label getDurationLabel() { return durationLabel; }

    public void setCurrentSongLabel(String text) {
        currentSongLabel.setText(text);
    }

    public void addToQueueDisplay(String title) {
        queueListView.getItems().add(title);
    }

    public void clearQueueDisplay() {
        queueListView.getItems().clear();
    }

    public void bindSeekCheckBox(ChangeListener<Boolean> seekListener) {
        enableSeekCheck.selectedProperty().addListener((_, _, isSelected) -> {
            progressSlider.setDisable(!isSelected);

            if (isSelected) {
                progressSlider.valueChangingProperty().addListener(seekListener);
            } else {
                progressSlider.valueChangingProperty().removeListener(seekListener);
            }
        });
    }
}
