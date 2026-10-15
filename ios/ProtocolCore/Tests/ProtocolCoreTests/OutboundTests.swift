import Foundation
import Testing
@testable import ProtocolCore

struct OutboundTests {
    private func encoded(_ value: some Encodable) -> [String: Any] {
        let data = try! JSONEncoder().encode(value)
        return try! JSONSerialization.jsonObject(with: data) as! [String: Any]
    }

    @Test func toggleCommandShape() {
        let json = encoded(CommandMessage.toggle)
        #expect(json["type"] as? String == "command")
        #expect(json["cmd"] as? String == "toggle")
        #expect(json.count == 2)
    }

    @Test func playCommandShape() {
        let json = encoded(CommandMessage.play)
        #expect(json["cmd"] as? String == "play")
        #expect(json["type"] as? String == "command")
        #expect(json.count == 2)
    }

    @Test func seekCommandShape() {
        let json = encoded(CommandMessage.seek(420_000))
        #expect(json["cmd"] as? String == "seek")
        #expect(json["positionMs"] as? Int64 == 420_000)
        #expect(json.count == 3)
    }

    @Test func volumeCommandShape() {
        let json = encoded(CommandMessage.volume(37))
        #expect(json["cmd"] as? String == "volume")
        #expect(json["value"] as? Int == 37)
        #expect(json.count == 3)
    }

    @Test func startedEventShape() {
        let json = encoded(EventMessage.started(
            streamPath: "Library/x.mp3", title: "X", durationMs: 245_789))
        #expect(json["type"] as? String == "event")
        #expect(json["event"] as? String == "started")
        #expect(json["streamPath"] as? String == "Library/x.mp3")
        #expect(json["title"] as? String == "X")
        #expect(json["positionMs"] as? Int64 == 0)
        #expect(json["durationMs"] as? Int64 == 245_789)
        #expect(json.count == 6)
    }

    @Test func positionEventOmitsEmptyOptionals() {
        let json = encoded(EventMessage.position(45_222))
        #expect(json["event"] as? String == "position")
        #expect(json["positionMs"] as? Int64 == 45_222)
        #expect(json.count == 3)
    }

    @Test func errorEventCarriesMessage() {
        let json = encoded(EventMessage.error("connection refused"))
        #expect(json["event"] as? String == "error")
        #expect(json["message"] as? String == "connection refused")
        #expect(json["positionMs"] as? Int64 == 0)
        #expect(json.count == 4)
    }
}