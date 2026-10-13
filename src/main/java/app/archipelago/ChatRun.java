/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.archipelago;

import javafx.scene.paint.Paint;

public record ChatRun(String text, Paint fill, boolean bold, boolean underline) {
}