# `MusicAppDemo` Decomposition — Progress & Remaining Opportunities

Durable notes for the on-going split of the `MusicAppDemo` god class into focused
controllers. Written as a "future me" scratchpad: where we are, what is left,
how hard it is, and what to do it in what order.

## Where we are

`MusicAppDemo`: **2531 → 1157 total lines** (≈897 non-blank/non-comment).
Everything below landed on the `decompose-main` branch (now merged/committed),
gate green after every commit:

| Commit | What moved out | Target |
| --- | --- | --- |
| boost/speed | playback boost + speed + energy formatting | `app.player.PlaybackController` |
| engine/track | engine, current song, seek, history, artwork | `app.player.PlaybackController` |
| companion server | server lifecycle, ticker, state broadcast, mode switch | `app.remote.CompanionController` |
| session restore | pending restore, debounce, snapshot | `app.logic.SessionRestoreController` |
| `AppContext` | shared host interface replacing per-controller callbacks | `app.AppContext` |
| player controls | panel buttons + keyboard + volume-adjust mode | `app.player.ui.PlayerControlsController` |
| album tree | build/selection/context-menu/highlight + cell factory | `app.ui.TreeController` |
| library utils | convert locations, open config, browse folder, defaults | `app.logic.LibraryController` |

`MusicAppDemo` implements `app.AppContext` and re-exposes thin delegates for the
few methods the extracted controllers/UI call back into (`refreshTree`,
`publishRemoteState`, `highlightCurrentSong`, `updateQueueDisplay`,
`togglePlayPause`, `scheduleSessionRestoreApply`, …).

## The seam: `AppContext`

`AppContext` is a single interface implemented once by `MusicAppDemo`. New
controllers depend on it instead of adding another set of bespoke callbacks.
Current surface lives in `src/main/java/app/AppContext.java`; the implementation
is the run of one-line `@Override` methods near the bottom of `MusicAppDemo`
(roughly lines 985–1131).

## Remaining extractable clusters

Two big clusters remain, plus some small wrappers and the `start()` wiring.
Line numbers are current as of this note; sizes are approximate.

### 1. Library load flow — ~370 lines — *high difficulty*

- `getLoadTask` (`MusicAppDemo.java:333`–516, ~184)
- `reloadGameLibrary` (`:518`–565, ~48)
- `browseFolder` (`:567`–679, ~113)
- `restoreLibraryControls` (`:681`–692), `applyFailureControls` (`:694`–705)
- `onGoalProgressChanged` (`:707`–709)

Target: `app.logic.LibraryLoader`.

Why it's hard: it mutates nearly all of the app's mutable library state —
`albums`, `library`, `queueManager`, `goalManager`, `pendingPlayed*`,
`usingMusicLibrary`, `bonusLocations` — plus touches `itemListener`, `companion`,
`playback`, `connectionPanel`, `treeController`, `sessionRestore` and the load
generation guard.

### 2. Connection lifecycle — ~185 lines — *high difficulty*

- `connectToServer` (`:883`–971, ~89)
- `disconnectFromServer` (`:853`–881, ~29)
- `enableOfflineMode` (`:778`–796), `disableOfflineMode` (`:798`–810)
- `applyOfflineUnlocks` (`:812`–814), `applyDeathLinkEnabled` (`:816`–821),
  `handleDeathLink` (`:823`–832)
- text-client/setter shims: `resetTextClientReadyState`, `setConnectButtonText`,
  `setGameFieldDisabled`, `startEnergyLinkSync` (`:834`–851)

Target: `app.remote.ConnectionController`, owning `client`, `itemListener`,
`energyLinkListener`, `offlineMode`.

Why it's hard: it owns/replaces the Archipelago client and its listeners, and
flips `offlineMode`, which other code (tree, `AppContext.connected()`) reads.

### 3. `start()` UI construction — ~145 lines — *medium*

- `start` (`:160`–304) builds every panel, wires listeners and lays out the scene.
  Could shed helper methods (`createBottomBar` already exists) or a small builder,
  but the payoff is modest and it is easy to make less readable.

### 4. Small wrappers — ~60 lines — *low difficulty*

- `unlockAlbum` (`:717`), `getEnabledSets` (`:721`), `enableAlbumForDisplay`
  (`:725`), `getUnlockedSongs` (`:761`), `getUnlockedAlbums` (`:765`),
  `applySlotData` (`:769`–776), `albumMusicRoots` (`:729`–738), `getLibrary`
  (`:1140`), `getLoadGeneration` (`:1144`), `getGoalManager` (`:1148`),
  `setPendingPlayedSongs` (`:1152`).
- Some of these may be dead (see TODO "Dead Code"); audit callers before moving.

## The real prerequisite: state ownership

Both remaining big clusters mutate the *same* fields. The clean way to finish is
**first** to pull that shared mutable state into a model, then extract the
loader/connection controllers against it:

1. Introduce a `SessionState`/`LibraryModel` (e.g. `app.logic`) owning:
   `albums`, `library`, `queueManager`, `goalManager`, `unlockManager`,
   `pendingPlayed*`, `usingMusicLibrary`, `bonusLocations`.
2. Point `AppContext` getters (and `TreeController`) at the model.
3. Extract `LibraryLoader` (cluster 1) and `ConnectionController` (cluster 2)
   against the model — now mostly mechanical.
4. Optionally shrink the `AppContext` adapter as fields move off `MusicAppDemo`.

## Realistic floor

With clusters 1 and 2 gone: **~450–550 non-blank lines** left — `main`,
`start`/`stop`, the `AppContext` implementation, and a handful of delegates.
That is close to the irreducible "window + wiring + interface impl" core.

## Rules of engagement (unchanged)

- Behavior must stay identical; this is a pure refactor.
- Run the full gate after **every** extraction:
  `./gradlew compileJava test checkstyleMain checkstyleTest spotbugsMain spotbugsTest`
- There are **no unit tests** covering `MusicAppDemo` logic — validate by gate +
  diff review against the parent commit. The only end-to-end coverage is
  `app.e2e.CompanionHarness`.
- Keep the extraction pattern: controller in a focused package, constructed with
  `AppContext` + injected collaborators; panels stay dumb.
- File header `SPDX-License-Identifier: MPL-2.0`; no star imports; ≤120 cols.
