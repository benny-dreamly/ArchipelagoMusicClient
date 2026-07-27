package app.player.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

public class AlbumArtPanel extends VBox {

    private static final double PREF_WIDTH = 200;
    private static final double IMAGE_MAX_SIZE = 180;

    private final ImageView imageView;
    private final Label infoLabel;

    public AlbumArtPanel() {
        super(8);
        setPrefWidth(PREF_WIDTH);
        setMaxWidth(PREF_WIDTH);
        setAlignment(Pos.TOP_CENTER);
        setPadding(new Insets(10));

        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(IMAGE_MAX_SIZE);
        imageView.setFitHeight(IMAGE_MAX_SIZE);

        infoLabel = new Label("No album art");
        infoLabel.setWrapText(true);
        infoLabel.setAlignment(Pos.CENTER);

        getChildren().addAll(imageView, infoLabel);

        // Start hidden
        setVisible(false);
        setManaged(false);
    }

    public void setArtwork(Image image, String trackInfo) {
        imageView.setImage(image);
        infoLabel.setText(trackInfo != null ? trackInfo : "");
        setVisible(true);
        setManaged(true);
    }

    public void clearArtwork() {
        imageView.setImage(null);
        infoLabel.setText("No album art");
        setVisible(false);
        setManaged(false);
    }
}
