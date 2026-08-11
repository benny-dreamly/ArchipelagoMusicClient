# TODO

## Known Bugs (flagged during Phase 3 refactor, not fixed — pure refactor only)
- ~~**`queueSongNext` lock bypass for null-album songs** — `MusicAppDemo.java:476` uses `album == null || album.isFullAlbumUnlock() || ...`, so a locked song with no album can be queued. `playSong` correctly requires `songUnlocked` for null-album songs; `queueSongNext` should match.~~ ✅ FIXED — `queueSongNext` now uses `UnlockManager.canPlay`, which requires `songUnlocked` for null-album songs.
- **`"re-recording"` type mismatch** — `UnlockManager.java:116` compares `"re-recording"`, but `AlbumConverter` produces `"rerecording"`, so the Re-recordings slot-data branch never matches. Fix the string and add a test.
- ~~Queue All Songs doesn't obey the currently unlocked state, as well as Play Next~~ ✅ FIXED — `Album.getQueueableSongs`, `UnlockManager.canPlay`/`canQueue`, `playSong`, and `queueSongNext` now all require the album itself to be unlocked (`albumUnlocked && (fullAlbumUnlock || songUnlocked)`), matching `handleTreeSelection` and the design (full-album unlock → album item unlocks the whole album; otherwise song + album items needed).

## Dead Code
- **`MusicAppDemo.unlockSong` is unused** — the public delegate at `MusicAppDemo.java:493` has no callers anywhere (never used by `ItemListener` or anything else). Keep `UnlockManager.unlockSong` (used by `unlockAlbum` + tests). Decide: remove the dead `MusicAppDemo` wrapper.

## Folder Scanner (Browse Folder mode)
Add a "Browse Folder" button that imports local music without needing an Archipelago manual:

- Recursively scan a chosen folder for audio files (.mp3, .m4a, .wav)
- Group files by immediate parent directory into synthetic `Album` objects
- Assign file paths directly (no fuzzy matching needed)
- Set `fullAlbumUnlock = true` on everything so all songs are playable
- Skip `locations.json` / `album_metadata.json` loading entirely in this mode
- Add a button in the connection panel (or alongside the offline checkbox)
- Might need a new class like `FolderScanner` to keep concerns separate

## Queue Improvements
- **Save/restore queue** — persist queue to `queue.json` on exit, restore on startup ✅
- **Shuffle queue** button — randomize the play queue ✅
- **Repeat modes** — repeat song, repeat queue, repeat album
- **Drag-to-reorder queue** — reorder songs in the queue by dragging ✅
- **"Play Next"** — right-click a song to insert at the front of the queue instead of the back ✅
- **Now-playing indicator in queue** — highlight the currently playing entry and keep it visible

## Playback / UI
- **Volume slider** — add a volume control to `PlayerPanel` (currently none) ✅
- **Keyboard shortcuts** — space (play/pause), left/right arrows (seek), cmd+right (next track) ✅
- **Gate seek shortcuts behind "Enable Seek Slider"** — the left/right arrow seek shortcuts in `MusicAppDemo` still work when the seek checkbox (`PlayerPanel.enableSeekCheck`) is off; they should be disabled while seeking is disabled ✅
- **Next/Previous song buttons** — no dedicated next/prev buttons in the player UI; skipping forward currently works only via the `N` key / `playNextInQueue()`, and there is no skip-backward at all
- **Dark mode** — presentable dark theme
- **Album art** — display cover art from the file's metadata if available
- **Crossfade / gapless playback** — smooth transitions between songs
- **Search/filter tree** — filter the album tree by song or album name

## Archipelago
- **Send goal status to server** — report goal/win status; requires tracking progress toward the goal, which means state tracking across connections for the same slot. Likely requires deeper digging into the Archipelago client library — there's basically no docs, so it means reading the library source and how other projects use it.
- **Deathlink support** — stop/skip playback on deathlink
- **Auto-reconnect** — retry connection if the server drops
- **Track completion percentage** — show unlock progress per album/world

## Testing / Quality
- **Unit tests** — JUnit is configured but unused; start with domain logic tests (already started with `AlbumTest`)
- **Extract file matching** — pull `assignFilesToSongs` into a standalone utility for testability (it has zero JavaFX dependency)
- **Logging cleanup** — replace remaining `e.printStackTrace()` calls with SLF4J
- **Config validation** — validate JSON configs on load with proper error messages
