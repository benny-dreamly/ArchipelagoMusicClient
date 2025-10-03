package app.archipelago;

import io.github.archipelagomw.Client;
import io.github.archipelagomw.flags.ItemsHandling;
import javafx.application.Platform;

import java.net.URISyntaxException;
import java.util.function.Consumer;

public class APClient extends Client {

    private final String address;
    private Consumer<Exception> onErrorCallback;

    public APClient(String host, int port, String slot, String password) {
        super();
        setGame("Manual_TaylorSwiftDiscography_bennydreamly");
        setPassword(password);
        setItemsHandlingFlags(ItemsHandling.SEND_ITEMS + ItemsHandling.SEND_OWN_ITEMS + ItemsHandling.SEND_STARTING_INVENTORY);
        this.address = host + ":" + port;
        setName(slot);
    }

    public void setOnErrorCallback(Consumer<Exception> callback) {
        this.onErrorCallback = callback;
    }


    public void connect() throws URISyntaxException {
        super.connect(this.address);
    }

    @Override
    public void onError(Exception e) {
        if (onErrorCallback != null) {
            Platform.runLater(() -> onErrorCallback.accept(e));
        }
        disconnect();
    }

    @Override
    public void onClose(String message, int i) {
        disconnect();
    }


}
