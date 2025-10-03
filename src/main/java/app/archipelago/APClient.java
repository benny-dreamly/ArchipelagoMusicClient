package app.archipelago;

import io.github.archipelagomw.Client;

import java.net.URISyntaxException;

public class APClient extends Client {

    private String address;

    public APClient(String host, int port, String slot, String password) {
        super();
        this.setGame("manual_TaylorSwiftDiscography_bennydreamly");
        this.address = host + ":" + port;
        setName(slot);
    }

    public void connect() throws URISyntaxException {
        super.connect(this.address);
    }

    @Override
    public void onError(Exception e) {
        disconnect();
    }

    @Override
    public void onClose(String message, int i) {
        disconnect();
    }


}
