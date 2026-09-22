/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player.ui;

import app.archipelago.APClient;
import app.archipelago.ChatColors;
import app.archipelago.ChatRun;
import io.github.archipelagomw.ClientStatus;
import io.github.archipelagomw.parts.Game;
import io.github.archipelagomw.parts.NetworkItem;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.fxmisc.richtext.InlineCssTextArea;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class TextClientWindow {

    private final Supplier<APClient> clientSupplier;

    private final InlineCssTextArea outputArea = new InlineCssTextArea();
    private final TextField inputField = new TextField();
    private final Button sendBtn = new Button("Send");

    private Stage stage;

    private boolean ready = false;
    private Map<String, List<String>> itemGroups = Collections.emptyMap();
    private Map<String, List<String>> locationGroups = Collections.emptyMap();

    public TextClientWindow(Supplier<APClient> clientSupplier) {
        this.clientSupplier = clientSupplier;
    }

    public void show() {
        if (stage != null) {
            stage.show();
            stage.toFront();
            return;
        }

        stage = new Stage();
        stage.setTitle("Text Client");

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.setId("output-area");
        VBox.setVgrow(outputArea, Priority.ALWAYS);

        inputField.setPromptText("Type message here");

        Runnable sendMessage = () -> {
            String msg = inputField.getText();
            APClient client = clientSupplier.get();
            if (!msg.isEmpty() && client != null) {
                if (msg.startsWith("/")) {
                    handleCommand(msg);
                } else {
                    client.sendChat(msg);
                }
                inputField.clear();
            }
        };

        sendBtn.setOnAction(_ -> sendMessage.run());
        inputField.setOnAction(_ -> sendMessage.run());

        HBox buttons = new HBox(10, sendBtn);
        root.getChildren().addAll(outputArea, inputField, buttons);

        Scene scene = new Scene(root, 400, 300);
        scene.getStylesheets().add(getClass().getResource("/text-client.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    public void appendOutput(String text) {
        appendMessage(List.of(new ChatRun(text, ChatColors.DEFAULT, false, false)));
    }

    public void appendMessage(List<ChatRun> runs) {
        for (ChatRun run : runs) {
            outputArea.append(run.text(), toCss(run));
        }
        outputArea.append("\n", "");
        outputArea.moveTo(outputArea.getLength());
        outputArea.requestFollowCaret();
    }

    public void onNameGroupsRetrieved(Map<String, List<String>> itemGroups,
                                      Map<String, List<String>> locationGroups) {
        this.itemGroups = itemGroups;
        this.locationGroups = locationGroups;
    }

    private void appendLine(String text) {
        appendMessage(List.of(new ChatRun(text, ChatColors.DEFAULT, false, false)));
    }

    private void handleCommand(String input) {
        String[] parts = input.substring(1).split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "help" -> handleHelpCommand();
            case "received" -> handleReceivedCommand();
            case "ready" -> handleReadyCommand();
            case "missing" -> handleMissingCommand(arg);
            case "items" -> handleItemsCommand();
            case "locations" -> handleLocationsCommand();
            case "item_groups" -> handleItemGroupsCommand(arg);
            case "location_groups" -> handleLocationGroupsCommand(arg);
            default -> {
                if ("goal".equals(cmd) && isDebugMode()) {
                    handleGoalCommand(arg);
                } else {
                    appendLine("Unknown command: " + cmd);
                }
            }
        }
    }

    private void handleHelpCommand() {
        appendLine("Commands:");
        appendLine("  /help                 Show this help");
        appendLine("  /received             List all items you have received");
        appendLine("  /ready                Toggle your ready status");
        appendLine("  /missing [filter]     List missing and checked locations (optional text filter)");
        appendLine("  /items                List all item names for your game");
        appendLine("  /locations            List all location names for your game");
        appendLine("  /item_groups [key]    List item groups, or items within a group");
        appendLine("  /location_groups [key] List location groups, or locations within a group");
        if (isDebugMode()) {
            appendLine("  /goal yes             (debug) Mark your goal as complete");
        }
    }

    private void handleGoalCommand(String arg) {
        APClient client = connectedClient();
        if (client == null) {
            appendLine("Not connected to a server.");
            return;
        }
        if (!"yes".equalsIgnoreCase(arg.trim())) {
            appendLine("This marks the goal complete for the room. To confirm, type: /goal yes");
            return;
        }
        client.setGameState(ClientStatus.CLIENT_GOAL);
        appendLine("Goal marked as complete.");
    }

    private boolean isDebugMode() {
        return Boolean.parseBoolean(System.getProperty("app.debug", "false"));
    }

    private void handleReceivedCommand() {
        APClient client = connectedClient();
        if (client == null) {
            appendLine("Not connected to a server.");
            return;
        }

        List<NetworkItem> items = client.getItemManager().getReceivedItems();
        appendMessage(List.of(
                new ChatRun(items.size() + " received items, sorted by time:", ChatColors.DEFAULT, false, false)
        ));

        for (NetworkItem item : items) {
            List<ChatRun> runs = new ArrayList<>();
            Paint itemColor = ChatColors.itemColor(item.flags);
            runs.add(new ChatRun(item.itemName, itemColor, false, false));
            runs.add(new ChatRun(" from ", ChatColors.DEFAULT, false, false));
            runs.add(new ChatRun(item.locationName, Color.web("#00FF7F"), false, false));
            runs.add(new ChatRun(" by ", ChatColors.DEFAULT, false, false));
            runs.add(new ChatRun(item.playerName, Color.web("#FAFAD2"), false, false));
            appendMessage(runs);
        }
    }

    private void handleReadyCommand() {
        APClient client = connectedClient();
        if (client == null) {
            appendLine("Not connected to a server.");
            return;
        }

        ready = !ready;
        if (ready) {
            client.setGameState(ClientStatus.CLIENT_READY);
            appendLine("Readied up.");
        } else {
            client.setGameState(ClientStatus.CLIENT_UNKNOWN);
            appendLine("Unreadied.");
        }
    }

    private void handleMissingCommand(String filter) {
        APClient client = connectedClient();
        if (client == null) {
            appendLine("Not connected to a server.");
            return;
        }

        Game gameData = client.getDataPackage().getGame(client.getGame());
        if (gameData == null) {
            appendLine("No data package available.");
            return;
        }

        Set<Long> missing = client.getLocationManager().getMissingLocations();
        Set<Long> checked = client.getLocationManager().getCheckedLocations();

        List<String> missingNames = missing.stream()
                .map(gameData::getLocation)
                .filter(n -> filter.isEmpty()
                        || n.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT)))
                .sorted()
                .toList();
        List<String> checkedNames = checked.stream()
                .map(gameData::getLocation)
                .filter(n -> filter.isEmpty()
                        || n.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT)))
                .sorted()
                .toList();

        for (String name : missingNames) {
            appendLine("Missing: " + name);
        }
        for (String name : checkedNames) {
            appendLine("Checked: " + name);
        }
        appendLine(missingNames.size() + " missing, " + checkedNames.size() + " checked");
    }

    private void handleItemsCommand() {
        APClient client = connectedClient();
        if (client == null) {
            appendLine("Not connected to a server.");
            return;
        }

        Game gameData = client.getDataPackage().getGame(client.getGame());
        if (gameData == null) {
            appendLine("No data package available.");
            return;
        }

        List<String> names = new ArrayList<>(gameData.itemNameToId.keySet());
        Collections.sort(names);
        appendLine("Item names (" + names.size() + "):");
        for (String name : names) {
            appendLine("  " + name);
        }
    }

    private void handleLocationsCommand() {
        APClient client = connectedClient();
        if (client == null) {
            appendLine("Not connected to a server.");
            return;
        }

        Game gameData = client.getDataPackage().getGame(client.getGame());
        if (gameData == null) {
            appendLine("No data package available.");
            return;
        }

        List<String> names = new ArrayList<>(gameData.locationNameToId.keySet());
        Collections.sort(names);
        appendLine("Location names (" + names.size() + "):");
        for (String name : names) {
            appendLine("  " + name);
        }
    }

    private void handleItemGroupsCommand(String key) {
        APClient client = connectedClient();
        if (client == null) {
            appendLine("Not connected to a server.");
            return;
        }

        if (itemGroups.isEmpty()) {
            appendLine("No item group data available.");
            return;
        }

        if (key.isEmpty()) {
            appendLine("Item groups (" + itemGroups.size() + "):");
            itemGroups.keySet().stream().sorted().forEach(k -> appendLine("  " + k));
        } else {
            List<String> items = itemGroups.get(key);
            if (items == null) {
                appendLine("Unknown group: " + key);
            } else {
                appendLine("Items in '" + key + "' (" + items.size() + "):");
                items.stream().sorted().forEach(i -> appendLine("  " + i));
            }
        }
    }

    private void handleLocationGroupsCommand(String key) {
        APClient client = connectedClient();
        if (client == null) {
            appendLine("Not connected to a server.");
            return;
        }

        if (locationGroups.isEmpty()) {
            appendLine("No location group data available.");
            return;
        }

        if (key.isEmpty()) {
            appendLine("Location groups (" + locationGroups.size() + "):");
            locationGroups.keySet().stream().sorted().forEach(k -> appendLine("  " + k));
        } else {
            List<String> locations = locationGroups.get(key);
            if (locations == null) {
                appendLine("Unknown group: " + key);
            } else {
                appendLine("Locations in '" + key + "' (" + locations.size() + "):");
                locations.stream().sorted().forEach(l -> appendLine("  " + l));
            }
        }
    }

    private APClient connectedClient() {
        APClient client = clientSupplier.get();
        return client != null && client.isConnected() ? client : null;
    }

    private static String toCss(ChatRun run) {
        StringBuilder style = new StringBuilder("-fx-fill: " + toHex((Color) run.fill()) + ";");
        if (run.bold()) {
            style.append("-fx-font-weight: bold;");
        }
        if (run.underline()) {
            style.append("-rtfx-underline: true;");
        }
        return style.toString();
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }
}