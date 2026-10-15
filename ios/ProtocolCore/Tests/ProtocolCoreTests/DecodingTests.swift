import Foundation
import Testing
@testable import ProtocolCore

struct DecodingTests {
    @Test func decodesStateSampleMatchingProtocol() throws {
        let json = """
        {
          "songTitle": "Song A",
          "album": "Album",
          "streamPath": "Library/music.mp3",
          "durationMs": 245789,
          "positionMs": 45222,
          "playing": true,
          "volume": 60,
          "queue": ["Song A", "Song B"],
          "activeSource": "phone"
        }
        """
        let state = try #require(
            try JSONDecoder().decode(CompanionState.self, from: Data(json.utf8)))
        #expect(state.songTitle == "Song A")
        #expect(state.album == "Album")
        #expect(state.streamPath == "Library/music.mp3")
        #expect(state.durationMs == 245_789)
        #expect(state.positionMs == 45_222)
        #expect(state.playing)
        #expect(state.volume == 60)
        #expect(state.queue == ["Song A", "Song B"])
        #expect(state.activeSource == CompanionState.sourcePhone)
    }

    @Test func decodesMinimalStateWithDefaults() throws {
        let json = #"{"songTitle":"Track"}"#
        let state = try #require(
            try JSONDecoder().decode(CompanionState.self, from: Data(json.utf8)))
        #expect(state.songTitle == "Track")
        #expect(state.durationMs == 0)
        #expect(state.positionMs == 0)
        #expect(!state.playing)
        #expect(state.volume == 0)
        #expect(state.queue.isEmpty)
        #expect(state.activeSource == CompanionState.sourceDesktop)
    }

    @Test func emptyScalarIsDesktop() {
        #expect(CompanionState.empty.activeSource == CompanionState.sourceDesktop)
        #expect(CompanionState.empty.volume == 0)
        #expect(!CompanionState.empty.playing)
    }

    @Test func decodesSeekCommand() throws {
        let json = #"{"type": "command", "cmd": "seek", "positionMs": 420000}"#
        let command = try #require(
            try JSONDecoder().decode(RemoteCommand.self, from: Data(json.utf8)))
        #expect(command.cmd == "seek")
        #expect(command.positionMs == 420_000)
        #expect(command.streamPath == nil)
    }

    @Test func decodesPlayCommand() throws {
        let json = #"{"type": "command", "cmd": "play", "streamPath": "Library/x.mp3", "title": "X"}"#
        let command = try #require(
            try JSONDecoder().decode(RemoteCommand.self, from: Data(json.utf8)))
        #expect(command.cmd == "play")
        #expect(command.streamPath == "Library/x.mp3")
        #expect(command.title == "X")
    }
}