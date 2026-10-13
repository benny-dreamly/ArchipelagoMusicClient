/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.archipelago;

import io.github.archipelagomw.events.ArchipelagoEventListener;
import io.github.archipelagomw.events.PrintJSONEvent;
import io.github.archipelagomw.Print.APPrint;
import io.github.archipelagomw.Print.APPrintJsonType;
import io.github.archipelagomw.Print.APPrintPart;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@SuppressWarnings("ClassCanBeRecord")
public class PrintJsonListener {
    private final APClient client;
    private final Consumer<List<ChatRun>> output;

    public PrintJsonListener(APClient client, Consumer<List<ChatRun>> output) {
        this.client = client;
        this.output = output;
    }

    @SuppressWarnings("unused")
    @ArchipelagoEventListener
    public void onPrintJson(PrintJSONEvent event) {
        // The type of message
        APPrintJsonType type = event.type;
        APPrint print = event.apPrint;

        // Filter types we don't want
        if (type == APPrintJsonType.TagsChanged ||
                type == APPrintJsonType.Unknown ||
                type == APPrintJsonType.Tutorial ||
                type == APPrintJsonType.ItemCheat) {
            return;
        }

        List<ChatRun> runs = new ArrayList<>();
        if (print.parts != null) {
            for (APPrintPart part : print.parts) {
                String text = part.text;
                if (text == null || text.isEmpty()) {
                    continue;
                }
                runs.add(new ChatRun(text, ChatColors.forPart(part, client.getSlot()),
                        ChatColors.isBold(part), ChatColors.isUnderline(part)));
            }
        }

        Platform.runLater(() -> output.accept(runs));
    }
}