# Music Player for Archipelago

This is a music player app that allows you to automate the process of sending checks in an Archipelago music Manual. 
It was originally built to work exclusively with my Taylor Swift music manual, but I have since tweaked it to work mostly generically with other music manuals. 
You might have to do some work upfront to get your music manual to work properly with this, but other than that it should be mostly plug and play. 

## How does this work?

This makes use of the Archipelago Java Client library in order to communicate with the Archipelago server. 
Configuration is simple enough, but I will elaborate more on the schema of the required configuration files,
and the structure of the configuration folder in the `docs` folder of this repository. This will include every file you need and examples.

You do need to have the songs locally on your computer, either by ripping them from a CD or obtaining them from iTunes or a similar online music marketplace.
Streaming will not work with this client. I was considering implementing it in the future, however Spotify's API won't let me do this, and I'm assuming the rest of the streaming apps are the same. As of right now,
any music streaming services such as Spotify and Apple Music will not work with this client, and will likely never work with it. 

## How can I get started using the client?

This client makes use of JavaFX, which you will need to make sure you have installed alongside your JDK.

### For End Users (Running a Release)
Simply download the latest release for your operating system from the [**Releases**](releases/latest) tab.
* **Java:** Requires **Java 25** (or newer) installed on your system.
* **JavaFX:** No manual installation needed! JavaFX is bundled directly with the release executable.

### For Developers (Building from Source)
If you want to modify the code or run the latest source directly:

1. **Clone the repository**:
   ```bash
   git clone https://github.com/benny-dreamly/ArchipelagoMusicClient.git
   cd ArchipelagoMusicClient
   ```

2. **Requirements**:
    - Java 25 or newer
    - **JavaFX**: You do not need to install JavaFX manually. The included Gradle build script handles JavaFX dependencies automatically.

3. **Run the app from source**:
   - macOS / Linux: `./gradlew run`
   - Windows: `gradlew.bat run`

4. **Build a runnable executable**:
    - macOS / Linux: `./gradlew build`
    - Windows: `gradlew.bat build`

## How the heck do I configure this?

Well, you're in luck. If you go to the docs/ folder, you should be able to find a schema and detailed documentation about where the configuration folders are.
See the [Configuration & File Reference](#configuration--file-reference) section of the readme for this.

# Configuration & File Reference

This folder is a breakdown of how the configuration and metadata files work.

If you’re setting up your own music manual (or modifying an existing one), you’ll find here:
- File schemas describing each JSON config file.
- Example configuration files.
- File system locations on different operating systems.
- Troubleshooting steps for common issues.

## Configuration files overview
| File                                     | Purpose                                                                                                            |
|------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `music_library.json` *(primary)*         | Defines albums, songs, file paths, bonus locations, and item requirements. Replaces legacy files when present.      |
| `albumFolders.json` *(fallback)*         | Maps album names to folder paths for local music files. Used when `music_library.json` doesn't specify paths.       |
| `albumOrder.json`                        | Controls the display order of albums in the music library tree.                                                     |
| `locations.json` *(legacy)*              | Defines all available songs, albums, and their relationships. Superseded by `music_library.json`.                   |
| `slot_data.json`                         | Describes what is unlocked or available to the player in randomizer/Archipelago mode.                               |
| `album_metadata.json` *(legacy)*         | Provides metadata such as album type, year, or album version flag. Superseded by `music_library.json`.              |

All configuration files live inside your **per-game folder**, which is automatically created under your operating system’s application data directory.

See [`config-locations.md`](docs/config-locations.md) for platform-specific paths.

## Keyboard Shortcuts

| Key                 | Action                            |
|---------------------|-----------------------------------|
| Space               | Toggle play/pause                 |
| Left / Right        | Seek backward / forward 5 seconds |
| N                   | Play next song in queue           |
| T                   | Open text client window           |
| O                   | Toggle offline mode               |
| V                   | Enter volume adjust mode          |
| ↑ / ↓ (volume mode) | Volume +10 / -10                  |
| ← / → (volume mode) | Volume +1 / -1                    |
| 0-9 (volume mode)   | Type exact volume value           |
| Enter (volume mode) | Apply typed volume                |
| Esc (volume mode)   | Cancel volume adjustment          |
