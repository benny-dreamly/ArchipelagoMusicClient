# Example Configuration Files

Below are examples of each configuration file format used by the Music Player.

---

## `albumFolders.json`

Maps each album name to the path where your audio files are stored.

```json
{
  "Fearless (Taylor's Version)": "C:/Users/Ben/Music/TaylorSwift/FearlessTV",
  "Midnights": "/Users/ben/Music/TaylorSwift/Midnights"
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

## `locations.json`

This file is defined by the manual itself (not the music player).
See the corresponding manual's repository or schema for structure and examples.

For the Taylor Swift manual, see the [ManualForArchipelago repository](https://github.com/ManualForArchipelago/Manual/tree/main/schemas)'s schemas.

Specifically the file you want for the locations file's schema is called `Manual.locations.schema.json`.
