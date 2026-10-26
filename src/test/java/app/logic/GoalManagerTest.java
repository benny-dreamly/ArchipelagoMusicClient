/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.logic;

import app.player.Album;
import app.player.Song;
import io.github.archipelagomw.APResult;
import io.github.archipelagomw.Client;
import io.github.archipelagomw.ClientStatus;
import io.github.archipelagomw.network.client.SetPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalManagerTest {

    /** Records every goal/state and data-storage call, never touches the network. */
    private static final class FakeClient extends Client {
        boolean connected;
        final List<ClientStatus> gameStates = new ArrayList<>();
        final List<SetPacket> persisted = new ArrayList<>();

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public int getSlot() {
            return 1;
        }

        @Override
        public APResult<Integer> dataStorageSet(SetPacket packet) {
            persisted.add(packet);
            return APResult.success(1);
        }

        @Override
        public APResult<Void> setGameState(ClientStatus status) {
            gameStates.add(status);
            return APResult.success();
        }

        @Override
        public void onError(Exception error) {
        }

        @Override
        public void onClose(String reason, int code) {
        }
    }

    private final UnlockManager unlockManager = new UnlockManager(() -> {});
    private final List<Set<String>> savedHistory = new ArrayList<>();

    @BeforeEach
    void setUp() {
        unlockManager.getEnabledSets().add("standard");
    }

    private static Album album(String name, String... songTitles) {
        Album album = new Album(name, "standard", true);
        for (String title : songTitles) {
            album.addSong(new Song(title, "standard"));
        }
        return album;
    }

    private GoalManager manager(List<Album> albums) {
        return new GoalManager(unlockManager, albums, () -> {}, savedHistory::add);
    }

    @Test
    void offlinePlaysAreTrackedLocallyAndNeverSendTheGoal() {
        Album a = album("A", "S1", "S2");
        unlockManager.getEnabledAlbums().add("A");
        GoalManager gm = manager(List.of(a));
        FakeClient client = new FakeClient();
        client.connected = true;

        gm.loadFromServer(Set.of(), client);
        int persistedAfterSnapshot = client.persisted.size();

        gm.markPlayed("S1", "A", null);
        gm.markPlayed("S2", "A", null);

        assertTrue(gm.isSongPlayed("S1", "A"));
        assertTrue(gm.isAlbumPlayed("A"));
        assertEquals(Set.of("A::S1", "A::S2"), savedHistory.getLast());
        // The invalidated snapshot suppresses the goal despite everything being played
        assertFalse(gm.isGoalSent());
        assertTrue(client.gameStates.isEmpty());
        assertEquals(persistedAfterSnapshot, client.persisted.size());
    }

    @Test
    void playsCompletedWhileConnectingAreMergedIntoTheSnapshot() {
        Album a = album("A", "S1", "S2", "S3");
        unlockManager.getEnabledAlbums().add("A");
        GoalManager gm = manager(List.of(a));
        FakeClient client = new FakeClient();
        client.connected = true;

        gm.markPlayed("S1", "A", client); // before the snapshot arrives

        assertTrue(client.persisted.isEmpty()); // deferred, not persisted yet

        gm.loadFromServer(Set.of("A::S2"), client);

        assertTrue(gm.isSongPlayed("S1", "A")); // the in-flight play was merged back in
        assertTrue(gm.isSongPlayed("S2", "A"));
        assertFalse(gm.isSongPlayed("S3", "A"));
        assertFalse(gm.isGoalSent());
        assertEquals(1, client.persisted.size()); // persisted once, after the snapshot
    }

    @Test
    void goalIsSentOnceWhenEveryEnabledAlbumIsFullyPlayed() {
        Album a = album("A", "S1", "S2");
        Album b = album("B", "T1");
        unlockManager.getEnabledAlbums().add("A");
        unlockManager.getEnabledAlbums().add("B");
        GoalManager gm = manager(List.of(a, b));
        FakeClient client = new FakeClient();
        client.connected = true;

        gm.loadFromServer(Set.of("A::S1"), client);
        assertFalse(gm.isGoalSent());

        gm.markPlayed("S2", "A", client);
        assertTrue(gm.isAlbumPlayed("A"));
        assertFalse(gm.isGoalSent()); // B is still pending

        gm.markPlayed("T1", "B", client);
        assertTrue(gm.isGoalSent());
        assertEquals(List.of(ClientStatus.CLIENT_GOAL), client.gameStates);

        gm.markPlayed("T1", "B", client); // duplicate play
        assertEquals(1, client.gameStates.size()); // CLIENT_GOAL is never re-sent
    }

    @Test
    void goalIsNotSentBeforeTheServerSnapshotHasLoaded() {
        Album a = album("A", "S1", "S2");
        Album b = album("B", "T1");
        unlockManager.getEnabledAlbums().add("A");
        unlockManager.getEnabledAlbums().add("B");
        GoalManager gm = manager(List.of(a, b));
        FakeClient client = new FakeClient();
        client.connected = true;

        gm.markPlayed("S1", "A", client);
        gm.markPlayed("S2", "A", client);
        gm.markPlayed("T1", "B", client);

        // Everything is played, but the snapshot hasn't arrived: nothing is sent
        assertTrue(client.gameStates.isEmpty());
        assertTrue(client.persisted.isEmpty());
        assertFalse(gm.isGoalSent());

        gm.loadFromServer(Set.of(), client); // merges the deferred plays in

        assertTrue(gm.isGoalSent());
        assertEquals(List.of(ClientStatus.CLIENT_GOAL), client.gameStates);
    }

    @Test
    void serverSnapshotReplacesLocallySeededHistory() {
        Album a = album("A", "S1", "S2");
        unlockManager.getEnabledAlbums().add("A");
        GoalManager gm = manager(List.of(a));
        FakeClient client = new FakeClient();

        gm.loadFromLocal(Set.of("A::S1"));
        assertTrue(gm.isSongPlayed("S1", "A"));

        client.connected = true;
        gm.loadFromServer(Set.of("A::S2"), client);

        assertFalse(gm.isSongPlayed("S1", "A")); // the server is authoritative while connected
        assertTrue(gm.isSongPlayed("S2", "A"));
    }

    @Test
    void disconnectedPlaysRequireAFreshSnapshotBeforePersistingAgain() {
        Album a = album("A", "S1", "S2");
        Album b = album("B", "T1");
        unlockManager.getEnabledAlbums().add("A");
        unlockManager.getEnabledAlbums().add("B");
        GoalManager gm = manager(List.of(a, b));
        FakeClient client = new FakeClient();

        client.connected = true;
        gm.loadFromServer(Set.of("A::S1"), client);
        int persisted = client.persisted.size();

        client.connected = false; // a disconnected play invalidates the snapshot
        gm.markPlayed("S2", "A", client);
        assertEquals(persisted, client.persisted.size());

        client.connected = true; // reconnected: the stale snapshot must not be trusted
        gm.markPlayed("T1", "B", client);
        assertEquals(persisted, client.persisted.size()); // deferred, not persisted
        assertTrue(client.gameStates.isEmpty());

        gm.loadFromServer(Set.of("A::S1", "A::S2", "B::T1"), client);

        assertEquals(persisted + 1, client.persisted.size());
        assertTrue(gm.isGoalSent());
    }

    @Test
    void playedCountOnlyCountsSongsInsideTheEnabledSets() {
        Album a = new Album("A", "standard", true);
        a.addSong(new Song("S1", "standard"));
        a.addSong(new Song("V1", "vault"));
        unlockManager.getEnabledAlbums().add("A");
        GoalManager gm = manager(List.of(a));

        gm.loadFromLocal(Set.of("A::S1", "A::V1"));

        assertEquals(1, gm.getPlayedCount(a)); // the vault song doesn't count
        assertTrue(gm.isAlbumPlayed("A")); // S1 alone completes it with vault disabled
    }

    @Test
    void resetClearsPlaysAndAllowsTheGoalToBeSentAgain() {
        Album a = album("A", "S1");
        unlockManager.getEnabledAlbums().add("A");
        GoalManager gm = manager(List.of(a));
        FakeClient client = new FakeClient();
        client.connected = true;

        gm.loadFromServer(Set.of("A::S1"), client);
        assertTrue(gm.isGoalSent());

        gm.reset();

        assertTrue(gm.getPlayedSongs().isEmpty());
        assertEquals(0, gm.getPlayedCount(a));
        assertFalse(gm.isAlbumPlayed("A"));
        assertFalse(gm.isGoalSent());

        client.gameStates.clear();
        gm.loadFromServer(Set.of("A::S1"), client);
        assertTrue(gm.isGoalSent());
        assertEquals(1, client.gameStates.size());
    }
}
