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
- **Save/restore queue on restart** — persist queue to `queue.json` on exit, restore on startup (~30 lines)
- **Volume slider** — add a volume control to `PlayerPanel` (currently none)
- **Keyboard shortcuts** — space (play/pause), left/right arrows (seek), cmd+right (next track)
- **Right-click "Play Next"** — insert song at the front of the queue instead of the back
- **Shuffle queue button** — randomize the play queue
- **Drag-to-reorder queue** — reorder songs by dragging (requires `ListView<Song>` + drag support; already changed to `ListView<Song>`)
- **Now-playing highlight in tree** — auto-scroll and highlight the currently playing song in the album tree
