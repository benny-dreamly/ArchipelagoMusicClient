/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.archipelago;

import io.github.archipelagomw.events.ArchipelagoEventListener;
import io.github.archipelagomw.events.RetrievedEvent;
import io.github.archipelagomw.events.SetReplyEvent;
import io.github.archipelagomw.network.client.SetPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleConsumer;

/**
 * Tracks and mutates the shared EnergyLink data storage value.
 *
 * <p>The EnergyLink pool is stored per-team under the key {@code EnergyLink{team}}
 * (e.g. {@code EnergyLink0} for team 0). It is a plain number that every client in
 * the team reads and adjusts through the generic data storage API; there is no
 * bounce-based "EnergyTake" message.
 */
public class EnergyLinkListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnergyLinkListener.class);

    private final APClient client;
    private final DoubleConsumer onBalanceChanged;

    private final AtomicReference<String> keyRef = new AtomicReference<>();
    private final AtomicReference<Double> balanceRef = new AtomicReference<>(null);
    private final Map<Integer, CompletableFuture<SetReplyEvent>> pendingWithdrawals = new ConcurrentHashMap<>();

    private static final long WITHDRAW_TIMEOUT_MS = 3_000;

    /**
     * @param client            the connected AP client
     * @param onBalanceChanged  invoked (possibly off the JavaFX thread) whenever the
     *                          stored energy balance is first read or later changes
     */
    public EnergyLinkListener(APClient client, DoubleConsumer onBalanceChanged) {
        this.client = client;
        this.onBalanceChanged = onBalanceChanged;
    }

    /**
     * Request the current balance and subscribe to future changes. Must be called
     * after the connection handshake has completed (team is known then).
     */
    public void sync() {
        String key = "EnergyLink" + client.getTeam();
        keyRef.set(key);
        balanceRef.set(null);
        LOGGER.info("Subscribing to EnergyLink data storage key '{}'", key);
        client.dataStorageGet(List.of(key));
        client.dataStorageSetNotify(List.of(key));
    }

    @SuppressWarnings("unused")
    @ArchipelagoEventListener
    public void onRetrieved(RetrievedEvent event) {
        String key = keyRef.get();
        if (key == null || !event.data.containsKey(key)) {
            return;
        }
        Object raw = event.data.get(key);
        if (raw instanceof Number n) {
            updateBalance(n.doubleValue());
            return;
        }
        try {
            Double value = event.getValueAsObject(key, Double.class);
            if (value != null) {
                updateBalance(value);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse EnergyLink balance for key '{}'", key, e);
        }
    }

    @SuppressWarnings("unused")
    @ArchipelagoEventListener
    public void onSetReply(SetReplyEvent event) {
        String key = keyRef.get();
        if (key == null || !key.equals(event.key)) {
            return;
        }
        CompletableFuture<SetReplyEvent> pending = pendingWithdrawals.remove(event.getRequestID());
        if (pending != null) {
            pending.complete(event);
            return;
        }
        if (event.value instanceof Number n) {
            updateBalance(n.doubleValue());
        }
    }

    private void updateBalance(double value) {
        balanceRef.set(value);
        if (onBalanceChanged != null) {
            onBalanceChanged.accept(value);
        }
    }

    /** The last known balance, or {@code null} if it has not been read yet. */
    public Double getBalance() {
        return balanceRef.get();
    }

    public boolean isReady() {
        return balanceRef.get() != null;
    }

    /** Add energy to the shared pool. */
    public void deposit(double amount) {
        String key = keyRef.get();
        if (key == null) {
            return;
        }
        SetPacket packet = new SetPacket(key, 0.0);
        packet.addDataStorageOperation(SetPacket.Operation.ADD, amount);
        client.dataStorageSet(packet);
        LOGGER.info("Deposited {} to EnergyLink '{}'", formatEnergy(amount), key);
    }

    /** Withdraw energy from the shared pool. Returns {@code false} if the balance is too low. */
    public boolean withdraw(double amount) {
        String key = keyRef.get();
        Double balance = balanceRef.get();
        if (key == null) {
            return false;
        }
        if (balance != null && balance < amount) {
            LOGGER.info("EnergyLink has {}; need {} to withdraw", formatEnergy(balance), formatEnergy(amount));
            return false;
        }
        SetPacket packet = new SetPacket(key, 0.0);
        packet.want_reply = true;
        packet.addDataStorageOperation(SetPacket.Operation.ADD, -amount);
        CompletableFuture<SetReplyEvent> reply = new CompletableFuture<>();
        int requestId = packet.getRequestID();
        pendingWithdrawals.put(requestId, reply);
        int sentId = client.dataStorageSet(packet);
        if (sentId == 0 || sentId != requestId) {
            pendingWithdrawals.remove(requestId);
            LOGGER.warn("Failed to send EnergyLink withdrawal for '{}'", key);
            return false;
        }
        try {
            SetReplyEvent event = reply.get(WITHDRAW_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!(event.original_value instanceof Number original)
                    || !(event.value instanceof Number current)) {
                LOGGER.warn("EnergyLink withdrawal reply for '{}' was not numeric", key);
                return false;
            }
            double originalBalance = original.doubleValue();
            double currentBalance = current.doubleValue();
            if (originalBalance < amount) {
                LOGGER.info("EnergyLink withdrawal for '{}' rejected: original balance {} was below {}",
                        key, formatEnergy(originalBalance), formatEnergy(amount));
                return false;
            }
            if (Math.abs((originalBalance - amount) - currentBalance) > 1e-9) {
                LOGGER.warn("EnergyLink withdrawal for '{}' did not apply as expected "
                        + "(original {} minus {} != {})", key,
                        formatEnergy(originalBalance), formatEnergy(amount), formatEnergy(currentBalance));
                return false;
            }
            updateBalance(currentBalance);
            LOGGER.info("Withdrew {} from EnergyLink '{}'", formatEnergy(amount), key);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pendingWithdrawals.remove(requestId);
            return false;
        } catch (ExecutionException | TimeoutException e) {
            pendingWithdrawals.remove(requestId);
            LOGGER.warn("Timed out waiting for EnergyLink withdrawal reply for '{}'", key);
            return false;
        }
    }

    private static String formatEnergy(double joules) {
        if (joules >= 1_000_000) {
            return String.format("%.2f MJ", joules / 1_000_000);
        }
        if (joules >= 1_000) {
            return String.format("%.1f kJ", joules / 1_000);
        }
        return String.format("%.1f J", joules);
    }
}