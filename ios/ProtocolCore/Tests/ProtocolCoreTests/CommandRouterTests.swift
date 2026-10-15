import Testing
@testable import ProtocolCore

struct CommandRouterTests {
    @Test func playCommandBecomesPlayAction() {
        let action = PlaybackCommandRouter.action(for: RemoteCommand(
            cmd: "play", streamPath: "Library/x.mp3", title: "X"))
        #expect(action == .play(streamPath: "Library/x.mp3", title: "X"))
    }

    @Test func playWithoutPathIsIgnored() {
        #expect(PlaybackCommandRouter.action(for: RemoteCommand(cmd: "play")) == nil)
    }

    @Test func pauseResumeStopMapDirectly() {
        #expect(PlaybackCommandRouter.action(for: RemoteCommand(cmd: "pause")) == .pause)
        #expect(PlaybackCommandRouter.action(for: RemoteCommand(cmd: "resume")) == .resume)
        #expect(PlaybackCommandRouter.action(for: RemoteCommand(cmd: "stop")) == .stop)
    }

    @Test func seekMapsToPosition() {
        #expect(PlaybackCommandRouter.action(for: RemoteCommand(cmd: "seek", positionMs: 420_000))
            == .seek(positionMs: 420_000))
        #expect(PlaybackCommandRouter.action(for: RemoteCommand(cmd: "seek")) == nil)
    }

    @Test func rateMapsToDouble() {
        #expect(PlaybackCommandRouter.action(for: RemoteCommand(cmd: "rate", rate: 1.5))
            == .rate(1.5))
        #expect(PlaybackCommandRouter.action(for: RemoteCommand(cmd: "rate")) == nil)
    }

    @Test func volumeMapsToInt() {
        #expect(PlaybackCommandRouter.action(for: RemoteCommand(cmd: "volume", value: 60))
            == .volume(60))
        #expect(PlaybackCommandRouter.action(for: RemoteCommand(cmd: "volume")) == nil)
    }

    @Test func unknownCommandIgnored() {
        #expect(PlaybackCommandRouter.action(for: RemoteCommand(cmd: "eject")) == nil)
        #expect(PlaybackCommandRouter.action(for: RemoteCommand(cmd: "")) == nil)
    }
}