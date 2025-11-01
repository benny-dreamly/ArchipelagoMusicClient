package app.archipelago;

import app.MusicAppDemo;
import io.github.archipelagomw.events.ArchipelagoEventListener;
import io.github.archipelagomw.events.PrintJSONEvent;
import io.github.archipelagomw.Print.*;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import org.w3c.dom.Text;


public class PrintJsonListener {
    private final APClient client;
    private final MusicAppDemo app;
    private final TextArea outputArea; // <-- add this


    public PrintJsonListener(APClient client, MusicAppDemo app, TextArea outputArea) {
        this.client = client;
        this.app = app;
        this.outputArea = outputArea;
    }

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

        String text = print.getPlainText(); // instance method

        Platform.runLater(() -> outputArea.appendText(text + "\n"));

    }
}
