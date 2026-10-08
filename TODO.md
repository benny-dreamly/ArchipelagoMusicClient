# TODO

## Known Bugs (flagged during Phase 3 refactor, not fixed — pure refactor only)
- ~~**`queueSongNext` lock bypass for null-album songs** — `MusicAppDemo.java:476` uses `album == null || album.isFullAlbumUnlock() || ...`, so a locked song with no album can be queued. `playSong` correctly requires `songUnlocked` for null-album songs; `queueSongNext` should match.~~ ✅ FIXED — `queueSongNext` now uses `UnlockManager.canPlay`, which requires `songUnlocked` for null-album songs.
- ~~Queue All Songs doesn't obey the currently unlocked state, as well as Play Next~~ ✅ FIXED — `Album.getQueueableSongs`, `UnlockManager.canPlay`/`canQueue`, `playSong`, and `queueSongNext` now all require the album itself to be unlocked (`albumUnlocked && (fullAlbumUnlock || songUnlocked)`), matching `handleTreeSelection` and the design (full-album unlock → album item unlocks the whole album; otherwise song + album items needed).

## Dead Code
- **`MusicAppDemo.unlockSong` is unused** — the public delegate at `MusicAppDemo.java:493` has no callers anywhere (never used by `ItemListener` or anything else). Keep `UnlockManager.unlockSong` (used by `unlockAlbum` + tests). Decide: remove the dead `MusicAppDemo` wrapper.

## Folder Scanner (Browse Folder mode)
Add a "Browse Folder" button that imports local music without needing an Archipelago manual:

- Recursively scan a chosen folder for audio files (.mp3, .m4a, .wav) ✅
- Group files by immediate parent directory into synthetic `Album` objects ✅
- Assign file paths directly (no fuzzy matching needed) ✅
- Set `fullAlbumUnlock = true` on everything so all songs are playable ✅
- Skip `locations.json` / `album_metadata.json` loading entirely in this mode ✅
- Add a button in the connection panel (or alongside the offline checkbox) ✅
- Might need a new class like `FolderScanner` to keep concerns separate ✅

Resolved design decisions: entering Browse Folder forces offline mode (disconnects any
connection, disables it, enables all unlocks); the chosen folder is persisted to
`connection.json` (`browse_folder`) and restored on next launch; scan recurses fully
(`root/Artist/Album` → one synthetic album per immediate parent dir).

## Queue Improvements
- **Save/restore queue** — persist queue to `queue.json` on exit, restore on startup ✅
- **Shuffle queue** button — randomize the play queue ✅
- **Repeat modes** — repeat song, repeat queue, repeat album ✅
- **Drag-to-reorder queue** — reorder songs in the queue by dragging ✅
- **"Play Next"** — right-click a song to insert at the front of the queue instead of the back ✅
- **Now-playing indicator in queue** — highlight the currently playing entry and keep it visible ✅
- **Played-aware "Queue All Songs"** — `MusicAppDemo.queueAlbum` (MusicAppDemo.java:851) queues every queueable song in an album. When connected to Archipelago, songs whose check id is already played (we track played songs by id) shouldn't be re-queued, so re-queueing an album plays the unplayed checks instead of re-running completed ones. ✅
  - Resolved design: added a second context-menu item **"Queue Unplayed"** (shown while connected) next to the existing "Queue All Songs" — `MusicAppDemo.queueUnplayedAlbum` (MusicAppDemo.java:877) filters through `GoalManager.isSongPlayed` (key: `albumName + "::" + songTitle`, GoalManager.java:44). "Queue All Songs" keeps queueing everything for deliberate re-listens, no extra settings toggle.
- **Local playback tracking** — `GoalManager` only persists played-song tracking via Archipelago server data storage (`musictools/played_songs_<slot>`, GoalManager.java:23), so played state is effectively Archipelago-only. Separate overhaul (later): track and persist played songs for local/offline libraries (e.g. `queue.json`-style local store) so "Queue Unplayed" and played counts also work in "Browse Folder" / offline mode. ✅
  - Resolved design: `PlayHistoryStore` (`app.util`) persists the same `album::song` key set to a global `play_history.json` (sibling of `connection.json`). `GoalManager.markPlayed` writes through to the server when connected, otherwise to the local store; `loadFromLocal` seeds a freshly-built library, and **strict separation** — `loadFromServer` replaces rather than merges, so offline plays never inflate a slot's server state or auto-send `CLIENT_GOAL` (guarded: `checkGoal` only runs on a live connection). "Queue Unplayed" is now offered whenever a library/GoalManager exists (offline + Browse included) and tree played-counts reflect local history automatically. Covering `PlayHistoryStoreTest`.
  - Key finding: `GoalManager.reset()` had no callers; each library load already recreates the manager, so seeding via `loadFromLocal` is the single clean entry point.
- **Session restore on reconnect** — on reconnecting to the same slot, restore the queue state and the current song's playback position. Feels like an opt-in toggle rather than a default behavior (you may not want an accidental reconnect to resume/push you into a queue). Where that toggle lives ties into the Settings/Configuration item below.

## Settings / Configuration
- **Options beyond the UI** — some behaviors (e.g. the session-restore toggle above, or playback tracking scope) don't belong as more UI toggles; they should live in a config file (like `connection.json` does today) so the UI stays uncluttered. Figure out the right home/format for non-UI configuration, and how it feeds these behaviors.
- **Settings belong in a dedicated place, not crammed into the connection panel** — dark mode (appearance), offline mode, and deathlink are currently toggles squatting in the connection panel; moving them out risks them being harder to find. Prefer a **settings popup/window** (e.g. a gear/settings button) rather than more inline controls, because the default window size can't fit all options when album art is open. Decide which settings move to the popup vs stay in the panel, and weigh discoverability of relocated toggles.
  - This section is a **prerequisite for the 6.0.0 mobile player** — the configuration format and settings popup should land before remote-control playback ships.

## Playback / UI
- **Volume slider** — add a volume control to `PlayerPanel` (currently none) ✅
- **Keyboard shortcuts** — space (play/pause), left/right arrows (seek), cmd+right (next track) ✅
- **Gate seek shortcuts behind "Enable Seek Slider"** — the left/right arrow seek shortcuts in `MusicAppDemo` still work when the seek checkbox (`PlayerPanel.enableSeekCheck`) is off; they should be disabled while seeking is disabled ✅
- **Next/Previous song buttons** — no dedicated next/prev buttons in the player UI; skipping forward currently works only via the `N` key / `playNextInQueue()`, and there is no skip-backward at all ✅
- **Dark mode** — presentable dark theme ✅
- **Album art** — display cover art from the file's metadata if available ✅
- **Crossfade / gapless playback** — smooth transitions between songs. Previously attempted on the `crossfade` branch but never worked well; the refactors since mean it would have to be recreated from scratch if attempted again. Parked — may never work, fine to leave as a stretch goal.
- **Search/filter tree** — filter the album tree by song or album name ✅

## Archipelago
- **Send goal status to server** — report goal/win status; requires tracking progress toward the goal, which means state tracking across connections for the same slot. Likely requires deeper digging into the Archipelago client library — there's basically no docs, so it means reading the library source and how other projects use it. ✅
- **Deathlink support** — stop/skip playback on deathlink ✅
- **Auto-reconnect** — retry connection if the server drops ✅
- **Track completion percentage** — show unlock progress per album/world ✅

## Mobile / Remote Player — **6.0.0 target**
- **Player-system rewrite for remote control** — a whole rewrite of the playback core to allow the phone app to control playback. Sits on the `companion-server` branch with `mobile/` (Android) and `ios/ProtocolCore/` (iOS) built against it.
  - Status: started, but hit issues during testing; couldn't get everything working and tested on a separate computer without completing it. Needs the user's own fixes merged back into the branch before continuing.
  - **Prerequisite: configuration work must land first** — the Settings/Configuration section (proper config format + a general settings popup) is required before this can ship; the rewrite is good but shouldn't go out without real configuration behind it.
  - **Blockers:** Xcode is not realistically installable on this Mac right now — would need an OS upgrade and likely a paid Apple developer certificate to run on real iOS hardware. Also unresolved: is the whole thing actually worth it? (see risk line)
  - **Android emulator findings:** playback was janky, no audio worked in the emulator, and some text didn't render — adequate to keep this as a "possibility", not a plan.
  - **Port principle:** the companion server must only bind/listen when remote mode is explicitly enabled (off by default). No port allocation on every launch — that's the argument for the config-first order and for remote mode living behind the settings toggle.
  - Risk: another "might never actually work" feature — remote playback needs to work reliably on a phone (seek, volume, queue control, connection) or it isn't worth 6.0.0. If it ships, it's a major because it rewrites the player API; otherwise it stays parked.

## Testing / Quality
- **Decompose `MusicAppDemo`** — currently ~1956 lines (god class). Split into focused controllers/components (tree/context menu, playback/energy/link, queue UI, connection/deathlink/energylink, offline/browse folder). Hands-on task to reclaim the solving. Keep behavior identical; run the full gate after each extraction.
- **Unit tests** — JUnit is configured but unused; start with domain logic tests (already started with `AlbumTest`)
- **Extract file matching** — pull `assignFilesToSongs` into a standalone utility for testability (it has zero JavaFX dependency) ✅
- **Logging cleanup** — replace remaining `e.printStackTrace()` calls with SLF4J ✅
- **Config validation** — validate JSON configs on load with proper error messages ✅

## Git / Workflow Conventions
- **main is the integration line** — not branch-protected, so wait to run the gate before pushing: `./gradlew compileJava test checkstyleMain checkstyleTest spotbugsMain spotbugsTest -q`
- **Short topic branches for anything non-trivial**; time-sensitive fixes/issues can land directly on main
- **Version bumps are ordinary commits on main** (avoid the protected-main trap from other projects: no PR-only rule here)
- Everything in this TODO file is fair game for a future session to pick up; treat this file as the durable "future me" scratchpad.
