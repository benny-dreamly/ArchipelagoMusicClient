package app.util;

import javafx.scene.control.Alert;

public class Dialogs {

    private Dialogs() {} // utility class

    public static void showError(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
