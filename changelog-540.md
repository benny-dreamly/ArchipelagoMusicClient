v5.4.0 is an incremental update with a settings window and session restore.

# What's New

- Settings window: dark mode and deathlink move out of the connection panel into a dedicated settings window opened by a new "⚙ Settings" button, so the default window size no longer needs to fit every toggle. The connection fields now sit behind their own "▾ Connection" collapse toggle, alongside Offline Mode and Browse Folder.
- Session restore: new opt-in "Restore session on reconnect" setting that saves your queue, current song, and playback position, then restores them when you connect to the same game and slot. Position resumes where you left off; nothing is restored for a different game/slot or while a queue is already loaded.
- App configuration now lives in a global `settings.json` next to `connection.json` (which keeps connection data only). Existing preferences are migrated automatically on first launch.
- Song file aliases: songs in `music_library.json` can declare an optional `aliases` list with alternative names, so album files that don't match the check name still get matched (e.g. a song titled "Some Chords - Dillon Francis Remix" matches a file named "2-01 Some Chords (Dillon Francis Remix).m4a" via `"aliases": ["Some Chords (Dillon Francis Remix)"]`). The exact title is still preferred over any alias.
- App icon and identity: the app now ships with a custom icon instead of the default "java" square, shown in the Dock, taskbar, and window on macOS, Windows, and Linux. Running from the packed app also renames the process (e.g. "Archipelago Music Client" in Activity Monitor) instead of "java". A new `packageApp` Gradle task builds the platform bundle with jpackage.
- Per-platform release bundles: releases now attach packaged apps for macOS (`ArchipelagoMusicClient-<version>-macos-arm64.zip`), Windows (`...-windows-x64.zip`), and Linux (`...-linux-x64.zip`) alongside the cross-platform jar, and all artifacts share the release version.

# Bug Fixes

- (none since v5.3.2)
