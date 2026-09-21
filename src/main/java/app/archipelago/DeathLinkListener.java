/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.archipelago;

import app.MusicAppDemo;
import io.github.archipelagomw.events.ArchipelagoEventListener;
import io.github.archipelagomw.events.DeathLinkEvent;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("ClassCanBeRecord")
public class DeathLinkListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeathLinkListener.class);

    private final MusicAppDemo app;

    public DeathLinkListener(MusicAppDemo app) {
        this.app = app;
    }

    @SuppressWarnings("unused")
    @ArchipelagoEventListener
    public void onDeathLink(DeathLinkEvent event) {
        LOGGER.info("Deathlink received from {}: {}", event.source, event.cause);
        Platform.runLater(() -> app.handleDeathLink(event.source, event.cause));
    }
}