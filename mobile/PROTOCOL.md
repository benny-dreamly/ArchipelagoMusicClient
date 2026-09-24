# Companion protocol

The desktop runs a small LAN server so a phone can act as a remote player.
Ports: HTTP **8311** (streaming + health), WebSocket **8312** (control).

The desktop owns the Archipelago session (checks, energy, queue). The phone
is a dumb endpoint: it streams what it is told and reports player truth back.

## Phone -> desktop

### Commands (JSON over WS, `{ "type": "command" }`)

```json
{ "type": "command", "cmd": "toggle" }
{ "type": "command", "cmd": "play" }
{ "type": "command", "cmd": "pause" }
{ "type": "command", "cmd": "next" }
{ "type": "command", "cmd": "seek", "positionMs": 420000 }
{ "type": "command", "cmd": "volume", "value": 60 }
```

### Events (JSON over WS, `{ "type": "event" }`)

```json
{ "type": "event", "event": "started", "streamPath": "Library/music.mp3", "title": "…", "positionMs": 0, "durationMs": 245789 }
{ "type": "event", "event": "paused",  "positionMs": 40123 }
{ "type": "event", "event": "resumed", "positionMs": 40123 }
{ "type": "event", "event": "position", "positionMs": 45222 }
{ "type": "event", "event": "ended",   "positionMs": 245789 }
{ "type": "event", "event": "error",   "message": "…" }
```

`ended` is the important one: the desktop responds by running its check,
energy grant, and next-track logic. `started` carries the `streamPath` the
phone is playing so the desktop can reconcile.

## Desktop -> phone

### Commands (unicast to the active phone)

```json
{ "type": "command", "cmd": "play",   "streamPath": "Library/music.mp3", "title": "…" }
{ "type": "command", "cmd": "pause" }
{ "type": "command", "cmd": "resume" }
{ "type": "command", "cmd": "stop" }
{ "type": "command", "cmd": "seek",   "positionMs": 420000 }
{ "type": "command", "cmd": "rate",   "rate": 1.5 }
{ "type": "command", "cmd": "volume", "value": 60 }
```

### State broadcast (no `type` field — anything that isn't a command decodes as state)

```json
{
  "songTitle": "…",
  "album": "…",
  "streamPath": "Library/music.mp3",
  "durationMs": 245789,
  "positionMs": 45222,
  "playing": true,
  "volume": 60,
  "queue": ["Song A", "Song B"],
  "activeSource": "phone"
}
```

`activeSource` is `"desktop"` or `"phone"`; broadcasts go out every ~500ms.

## HTTP

- `GET /stream/<path>` — Range-capable file server. The phone seeks by doing
  `Range: bytes=start-end` requests. Stream URL on the phone is
  `http://<host>:8311<streamPath>`.
- `GET /health` — `{"ok": true, "clients": 1, "activePhone": true}`. The phone
  pings this before establishing the WebSocket so a wrong IP fails fast.

## Sessions

- Multiple phones may connect; the first one to connect becomes the active
  phone and receives commands. The active slot only transfers after that
  phone disconnects.