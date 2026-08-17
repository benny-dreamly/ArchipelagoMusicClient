# Example Configuration Files

Below are examples of each configuration file format used by the Music Player.

---

## `albumFolders.json`

Maps each album name to the path where your audio files are stored.

```json
{
  "Fearless (Taylor's Version)": "C:/Users/ExampleUser/Music/TaylorSwift/FearlessTV",
  "Midnights": "/Users/example_user/Music/TaylorSwift/Midnights"
}
```

---

## `albumOrder.json`

Defines the order in which albums appear in the library tree.

```json
[
  "Taylor Swift",
  "Fearless (Taylor's Version)",
  "Speak Now (Taylor's Version)",
  "1989 (Taylor's Version)",
  "Midnights",
  "The Tortured Poets Department"
]
```

---

## `slot_data.json`

Used to define which features (albums, vaults, categories) are available in your slot.

```json
{
  "slot_data_keys": {
    "include_debut": {
      "type": "album",
      "display_name": "Taylor Swift"
    },
    "include_fearless": {
      "type": "album",
      "display_name": "Fearless (Taylor's Version)"
    },
    "include_midnights": {
      "type": "album",
      "display_name": "Midnights"
    },
    "include_vault_tracks": {
      "type": "song_option",
      "display_name": "Vault Tracks"
    },
    "include_re_recordings": {
      "type": "album",
      "display_name": "Re-recordings"
    }
  }
}
```

---

## `album_metadata.json`

Defines unlock behavior for each album.

```json
{
  "Fearless (Taylor's Version)": {
    "type": "re-recording",
    "full_album_unlock": true
  },
  "Midnights": {
    "type": "standard",
    "full_album_unlock": false
  },
  "The Tortured Poets Department": {
    "type": "standard",
    "full_album_unlock": false
  }
}
```

## `music_library.json`

Defines the complete music library: albums, songs, file paths, and Archipelago location mappings.
This is the primary format for defining your library. If present, it replaces `locations.json`, `album_metadata.json`, and `albumFolders.json`.

```json
{
  "artist": "Taylor Swift",
  "albums": [
    {
      "name": "Fearless (Taylor's Version)",
      "type": "re-recording",
      "full_album_unlock": true,
      "songs": [
        {
          "title": "Love Story (Taylor's Version)",
          "location": "Love Story",
          "type": "normal"
        },
        {
          "title": "You Belong with Me (Taylor's Version)",
          "type": "normal"
        }
      ]
    },
    {
      "name": "Midnights",
      "type": "standard",
      "full_album_unlock": false,
      "songs": [
        {
          "title": "Anti-Hero",
          "type": "normal"
        },
        {
          "title": "Lavender Haze",
          "type": "normal"
        }
      ]
    }
  ]
}
```

---

## `locations.json`

> **Legacy format.** If `music_library.json` exists in the game folder, it takes precedence.
> `locations.json` is still supported as a fallback for backwards compatibility.

This file is defined by the manual itself (not the music player).
See the corresponding manual's repository or schema for structure and examples.

For the Taylor Swift manual, see the [ManualForArchipelago repository](https://github.com/ManualForArchipelago/Manual/tree/main/schemas)'s schemas.

Specifically the file you want for the locations file's schema is called `Manual.locations.schema.json`.
