/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.util;

import javafx.scene.control.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Dialogs {

    private static final Logger LOGGER = LoggerFactory.getLogger(Dialogs.class);

    private Dialogs() {} // utility class

    /**
     * Shows a modal error dialog and logs the message rather than the raw exception.
     */
    public static void showError(String title, String header, String content) {
        LOGGER.info("{}: {}", header, content);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
