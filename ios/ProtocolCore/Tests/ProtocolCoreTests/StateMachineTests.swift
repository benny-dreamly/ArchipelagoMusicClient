import Testing
@testable import ProtocolCore

struct StateMachineTests {
    private func makePlayingMachine() -> RemotePlayerStateMachine {
        var machine = RemotePlayerStateMachine()
        machine.beginPlayback(streamPath: "Library/song.mp3", title: "Song")
        return machine
    }

    @Test func fullSessionLifecycle() {
        var machine = makePlayingMachine()

        let ready = machine.apply(.ready(durationMs: 1000))
        #expect(ready.count == 1)
        #expect(ready[0].event == "started")
        #expect(ready[0].streamPath == "Library/song.mp3")
        #expect(ready[0].title == "Song")
        #expect(ready[0].durationMs == 1000)
        #expect(machine.state == .buffering)

        let began = machine.apply(.isPlaying(true))
        #expect(began == [.resumed(0)])
        #expect(machine.state == .playing)

        #expect(machine.apply(.position(100)) == [.position(100)])
        #expect(machine.apply(.position(200)) == [.position(200)])

        let paused = machine.apply(.isPlaying(false))
        #expect(paused == [.paused(200)])
        #expect(machine.state == .paused)

        _ = machine.apply(.position(205))
        #expect(machine.apply(.position(210)).isEmpty,
                "position ticks must not be reported while paused")

        let resumed = machine.apply(.isPlaying(true))
        #expect(resumed == [.resumed(210)])

        let ended = machine.apply(.didPlayToEnd)
        #expect(ended == [.ended(210)])
        #expect(machine.state == .ended)
        #expect(!machine.hasActiveSession)
    }

    @Test func startedIsSentOnlyOncePerSession() {
        var machine = makePlayingMachine()
        _ = machine.apply(.ready(durationMs: 1000))
        let secondReady = machine.apply(.ready(durationMs: 1000))
        #expect(secondReady.isEmpty, "repeated ready must not resend started")
    }

    @Test func eventsBeforeReadyAreHarmless() {
        var machine = makePlayingMachine()
        _ = machine.apply(.position(50))
        #expect(machine.apply(.isPlaying(true)) == [.resumed(50)])
    }

    @Test func failureEmitsErrorAndFlipsState() {
        var machine = makePlayingMachine()
        let events = machine.apply(.failed(message: "connection refused"))
        #expect(events == [.error("connection refused")])
        #expect(machine.state == .failed(message: "connection refused"))
    }

    @Test func playbackAfterEndStartsFreshSession() {
        var machine = makePlayingMachine()
        _ = machine.apply(.ready(durationMs: 1000))
        _ = machine.apply(.isPlaying(true))
        _ = machine.apply(.position(900))
        _ = machine.apply(.didPlayToEnd)

        machine.beginPlayback(streamPath: "Library/next.mp3", title: nil)
        let ready = machine.apply(.ready(durationMs: 800))
        #expect(ready[0].streamPath == "Library/next.mp3")
        #expect(ready[0].title == nil)

        #expect(machine.apply(.isPlaying(true)) == [.resumed(0)])
    }

    @Test func stopPlaybackResets() {
        var machine = makePlayingMachine()
        _ = machine.apply(.ready(durationMs: 1000))
        machine.stopPlayback()
        #expect(machine.state == .idle)
        #expect(!machine.hasActiveSession)
        #expect(machine.apply(.isPlaying(true)).isEmpty)
    }
}