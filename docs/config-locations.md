# Configuration Folder Locations

The Music Player automatically creates a configuration directory per manual/game when you first run or connect to a server.

## Default folder paths

| Platform    | Path                                                                      |
|-------------|---------------------------------------------------------------------------|
| **Windows** | `C:\Users\<USERNAME>\AppData\Roaming\MusicAppDemo\<ManualName>`           |
| **macOS**   | `/Users/<USERNAME>/Library/Application Support/MusicAppDemo/<ManualName>` |
| **Linux**   | `/home/<USERNAME>/.local/share/MusicAppDemo/<ManualName>`                 |

Each manual folder contains:

- music_library.json (primary format — replaces locations.json and album_metadata.json when present)
- locations.json (legacy fallback)
- albumFolders.json (per-album fallback for file paths when not specified in music_library.json)
- albumOrder.json
- slot_data.json
- album_metadata.json (legacy, superseded by music_library.json)

If any file is missing, the client will generate a default version automatically for most files except for the locations.json and metadata files.

---

## 📂 Example Configurations

See [docs/schema/example-configs.md](./schema/example-configs.md) for working JSON examples of these files.

---