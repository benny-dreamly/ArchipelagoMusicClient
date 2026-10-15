/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.player;

import app.archipelago.EnergyLinkListener;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns the playback rate state: the user-selected speed and the timed EnergyLink
 * "boost". Extracted from {@code MusicAppDemo} so the rate/boost bookkeeping and
 * its timers live in one place.
 */
public class PlaybackController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaybackController.class);

    private static final double BOOST_RATE_150 = 1.5;
    private static final double BOOST_RATE_200 = 2.0;
    private static final double BOOST_COST_150 = 1_500;
    private static final double BOOST_COST_200 = 2_250;
    private static final Duration BOOST_DURATION = Duration.seconds(30);
    private static final double SONG_ENERGY_CREDIT = 500;
    private static final Duration CLICK_WINDOW = Duration.millis(300);

    private final Supplier<PlaybackEngine> engine;
    private final BooleanSupplier clientConnected;
    private final Supplier<EnergyLinkListener> energyLink;
    private final Consumer<String> appendOutput;

    private boolean boostActive = false;
    private double boostRate = 1.0;
    private double playbackSpeed = 1.0;
    private PauseTransition boostTimer;
    private PauseTransition boostClickTimer;

    /**
     * @param engine          current playback engine (may change when switching sources)
     * @param clientConnected whether the Archipelago client is connected
     * @param energyLink      current EnergyLink listener, or {@code null} when absent
     * @param appendOutput    appends a line to the text-client output
     */
    public PlaybackController(Supplier<PlaybackEngine> engine,
                              BooleanSupplier clientConnected,
                              Supplier<EnergyLinkListener> energyLink,
                              Consumer<String> appendOutput) {
        this.engine = engine;
        this.clientConnected = clientConnected;
        this.energyLink = energyLink;
        this.appendOutput = appendOutput;
    }

    /** The rate the engine should currently run at (boost wins over the chosen speed). */
    public double currentRate() {
        return boostActive ? boostRate : playbackSpeed;
    }

    public void onBoostClick() {
        if (boostClickTimer != null) {
            // Second click within the window → double-click = 2x boost
            boostClickTimer.stop();
            boostClickTimer = null;
            startBoost(BOOST_RATE_200, BOOST_COST_200);
            return;
        }
        boostClickTimer = new PauseTransition(CLICK_WINDOW);
        boostClickTimer.setOnFinished(_ -> {
            boostClickTimer = null;
            // No second click followed within the window → single click = 1.5x boost
            startBoost(BOOST_RATE_150, BOOST_COST_150);
        });
        boostClickTimer.play();
    }

    private void startBoost(double rate, double cost) {
        EnergyLinkListener link = energyLink.get();
        if (link != null && clientConnected.getAsBoolean()) {
            Double balance = link.getBalance();
            if (balance == null) {
                LOGGER.info("Boost rejected: EnergyLink is not ready yet");
                appendOutput.accept("EnergyLink is not ready for boost yet");
                return;
            }
            if (balance < cost) {
                LOGGER.info("Boost rejected: need {} but only {} available",
                        EnergyLinkListener.formatEnergy(cost), EnergyLinkListener.formatEnergy(balance));
                appendOutput.accept(
                        String.format("Not enough energy (%s needed, %s available)",
                                EnergyLinkListener.formatEnergy(cost), EnergyLinkListener.formatEnergy(balance)));
                return;
            }
            if (!link.withdraw(cost)) {
                appendOutput.accept(
                        String.format("Not enough energy to boost (%s available)",
                                EnergyLinkListener.formatEnergy(balance)));
                return;
            }
        }
        applyBoost(rate);
    }

    private void applyBoost(double rate) {
        boostRate = rate;
        boostActive = true;
        PlaybackEngine current = engine.get();
        if (current != null) {
            current.setRate(rate);
        }
        if (boostTimer != null) {
            boostTimer.stop();
        }
        boostTimer = new PauseTransition(BOOST_DURATION);
        boostTimer.setOnFinished(_ -> endBoost());
        boostTimer.play();
        LOGGER.info("Playback boost engaged at {}x for {}s", rate, (int) BOOST_DURATION.toSeconds());
    }

    public String setPlaybackSpeedCommand(String arg) {
        String trimmed = arg == null ? "" : arg.trim();
        double rate;
        if (trimmed.isEmpty()) {
            rate = playbackSpeed == 1.0 ? 3.0 : 1.0;
        } else {
            try {
                rate = Double.parseDouble(trimmed);
            } catch (NumberFormatException e) {
                return "Speed must be a number.";
            }
        }
        if (rate < 0.1 || rate > 4.0) {
            return "Speed must be between 0.1 and 4.0.";
        }
        playbackSpeed = rate;
        if (!boostActive) {
            applyPlaybackRate(engine.get());
        }
        return playbackSpeed == 1.0
                ? "Playback speed reset."
                : String.format(Locale.ROOT, "Playback speed set to %.2fx", playbackSpeed);
    }

    public void applyPlaybackRate(PlaybackEngine target) {
        if (target == null) {
            return;
        }
        double rate = currentRate();
        // macOS resets the native rate to 1.0 when playback (re)starts (JDK-8236146),
        // so force the property to change to make the target rate stick.
        target.setRate(1.0);
        target.setRate(rate);
        LOGGER.info("Applied playback rate {}", rate);
    }

    /** Credit the stored energy for a finished song. */
    public void creditSongEnergy() {
        EnergyLinkListener link = energyLink.get();
        if (link != null && clientConnected.getAsBoolean()) {
            link.deposit(SONG_ENERGY_CREDIT);
        }
    }

    /** Stop a running boost and cancel any pending boost click. */
    public void reset() {
        endBoost();
        if (boostClickTimer != null) {
            boostClickTimer.stop();
            boostClickTimer = null;
        }
    }

    private void endBoost() {
        boostActive = false;
        boostRate = 1.0;
        if (boostTimer != null) {
            boostTimer.stop();
            boostTimer = null;
        }
        PlaybackEngine current = engine.get();
        applyPlaybackRate(current);
        LOGGER.info("Playback boost ended");
    }
}
