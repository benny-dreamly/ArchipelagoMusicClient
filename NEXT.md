# Next Up

## Folder Scanner
Add a "Browse Folder" button that imports local music without needing an Archipelago manual:

- Recursively scan a chosen folder for audio files (.mp3, .m4a, .wav)
- Group files by immediate parent directory into synthetic `Album` objects
- Assign file paths directly (no fuzzy matching needed)
- Set `fullAlbumUnlock = true` on everything so all songs are playable
- Skip `locations.json` / `album_metadata.json` loading entirely in this mode
- Add a button in the connection panel (or alongside the offline checkbox)
- New class like `FolderScanner` to keep concerns separate

## Queue / Playback
- **Save/restore queue on restart** — persist queue to `queue.json` on exit, restore on startup (~30 lines) ✅
    - we could also do a save queue/load queue button instead? that might be preferable because then we don't have a queue for a wrong artist/manual loaded up...
- **Volume slider** — add a volume control to `PlayerPanel` (currently none) ✅
- **Keyboard shortcuts** — space (play/pause), left/right arrows (seek), cmd+right (next track) ✅
- **Right-click "Play Next"** — insert song at the front of the queue instead of the back ✅
- **Shuffle queue button** — randomize the play queue ✅
- **Drag-to-reorder queue** — reorder songs by dragging (requires `ListView<Song>` + drag support; already changed to `ListView<Song>`) ✅
- **Now-playing highlight in tree** — auto-scroll and highlight the currently playing song in the album tree ✅
- **Repeat modes** — repeat song, repeat queue, repeat album ✅
- **Crossfade / gapless playback** — smooth transitions between songs
- **Skip to next track button** — skips to next track in queue (works via `N` key / `playNextInQueue()`; needs a visible player button)
- **Previous track button** — no skip-backward exists at all yet
- **Gate seek shortcuts behind "Enable Seek Slider"** — left/right arrow seek shortcuts still work when the seek checkbox is off; they should be disabled while seeking is disabled

## Archipelago
- **Send goal status to server** — report goal/win status; requires tracking progress toward the goal (state tracking across connections for the same slot). Likely requires deeper digging into the Archipelago client library — no docs, so read the library source and how other projects use it

## UI

- **Dark mode** — presentable dark theme
- **Album art** — display cover art from the file's metadata if available
- **Search/filter tree** — filter the album tree by song or album name