# iOS client — status & plan (for future me)

**Status: shelved.** The Android companion client works end-to-end (desktop +
`mobile/`). The iOS variant was scaffolded but parked because full Xcode
couldn't be installed on this Mac (disk space / OS version). All iOS work is
committed on the `companion-server` branch, unmerged to `main`.

## What the iOS client is

A phone companion that mirrors the Android app: it connects to the desktop's
`CompanionServer` (WS 8312 for control+state, HTTP 8311 for `/health` and
Range streaming), plays what the desktop tells it with AVPlayer, and reports
`started`/`paused`/`resumed`/`position`/`ended`/`error` events back. The
desktop remains the Archipelago session owner (checks, energy, queue).

Wire contract lives in `mobile/PROTOCOL.md` — platform-agnostic, still valid.

## What exists (`ios/ProtocolCore/`)

A pure Swift package, deliberately free of UIKit/AVFoundation so it builds and
tests with just the Swift toolchain (no Xcode). 25 tests green via `swift test`.

- `CompanionState.swift` — mirror of the desktop record; lenient decode.
- `Messages.swift` — outbound `CommandMessage`/`EventMessage`, inbound
  `RemoteCommand` (shapes match `PROTOCOL.md`, nils omitted when encoding).
- `CompanionService.swift` — `@MainActor` client: async `/health` probe, WS
  connect + receive loop, `streamURL(_:)`, all senders. `CompanionPorts.http/ws`.
- `RemotePlayerStateMachine.swift` — pure AVPlayer-lifecycle → protocol-events
  translator (started once per session, no positions while paused, etc.).
- `PlaybackCommandRouter.swift` — maps desktop commands to `PlaybackAction`s.
- Tests in `Tests/ProtocolCoreTests/`.

Commits: `58ba46b` (scaffold) and `ec25a03` (command router).

## Decisions locked in

- Min iOS 17, SwiftUI, AVPlayer for streaming (AVPlayer does HTTP Range natively).
- `ios/` lives in this repo, sibling to `mobile/`.
- Core kept buildable/testable with the CLT Swift toolchain (this turned out to
  matter: Xcode isn't installed here).

## Remaining work

- **I2 (core, testable now without Xcode):** AVPlayer-transparent layer already
  sketched by `RemotePlayerStateMachine` + `PlaybackCommandRouter`. Could add a
  `RemotePlayer` executor behind the state machine.
- **I3 (needs full Xcode):** SwiftUI app (`ios/ArchipelagoPhone/`) wiring
  `CompanionService` to a connect screen, transport controls, seek/volume
  sliders, queue list, buffering indicator, reconnect backoff — mirroring the
  Android `MainActivity` (M4). Info.plist needs `NSAppTransportSecurity`
  cleartext exception + `NSLocalNetworkUsageDescription` (local-network prompt),
  `UIBackgroundModes: audio`.

## Prerequisites when resuming

1. Install full Xcode (App Store, or the `xcodes` tool) — ~30 GB.
2. `cd ios/ProtocolCore && swift test` — should be 25 passing.
3. Create the SwiftUI app under `ios/ArchipelagoPhone`, add ProtocolCore as a
   local package dependency, target iOS 17.
4. Live test: same recipe as Android — run
   `java -cp build/libs/ArchipelagoMusicClient-5.1.0-all.jar app.e2e.CompanionHarness`
   (root Gradle project), connect the app, expect `[event] started` then
   `[event] position` lines in the harness log.

## Traps learned (2026-09)

- `ANDROID_HOME` was misconfigured to `platforms/android-29`; the real SDK root
  is `~/Library/Android/sdk`.
- This Mac: only 3.8 GB free disk → emulator can't provision (needs ~7.5 GB).
  That's why live Android testing moved to another machine.
- Desktop Gradle 9.7.1 wrapper; `shadowJar` (fat jar) is the easy harness launch.