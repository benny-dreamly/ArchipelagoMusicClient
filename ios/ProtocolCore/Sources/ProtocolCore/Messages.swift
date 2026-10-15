import Foundation

/// Outbound phone->desktop command (`{type:"command", ...}`). Optional fields
/// are omitted by the encoder when nil, matching the desktop's expectations.
public struct CommandMessage: Codable, Equatable, Sendable {
    public let type = "command"
    public let cmd: String
    public var value: Int?
    public var positionMs: Int64?
    public var rate: Double?
    public var streamPath: String?
    public var title: String?

    public init(
        cmd: String,
        value: Int? = nil,
        positionMs: Int64? = nil,
        rate: Double? = nil,
        streamPath: String? = nil,
        title: String? = nil
    ) {
        self.cmd = cmd
        self.value = value
        self.positionMs = positionMs
        self.rate = rate
        self.streamPath = streamPath
        self.title = title
    }

    private enum CodingKeys: String, CodingKey {
        case type, cmd, value, positionMs, rate, streamPath, title
    }

    public static let toggle = CommandMessage(cmd: "toggle")
    public static let play = CommandMessage(cmd: "play")
    public static let pause = CommandMessage(cmd: "pause")
    public static let next = CommandMessage(cmd: "next")

    public static func seek(_ positionMs: Int64) -> CommandMessage {
        CommandMessage(cmd: "seek", positionMs: positionMs)
    }

    public static func volume(_ value: Int) -> CommandMessage {
        CommandMessage(cmd: "volume", value: value)
    }
}

/// Outbound phone->desktop event (`{type:"event", ...}`).
public struct EventMessage: Codable, Equatable, Sendable {
    public let type = "event"
    public let event: String
    public var positionMs: Int64
    public var durationMs: Int64?
    public var message: String?
    public var streamPath: String?
    public var title: String?

    public init(
        event: String,
        positionMs: Int64 = 0,
        durationMs: Int64? = nil,
        message: String? = nil,
        streamPath: String? = nil,
        title: String? = nil
    ) {
        self.event = event
        self.positionMs = positionMs
        self.durationMs = durationMs
        self.message = message
        self.streamPath = streamPath
        self.title = title
    }

    private enum CodingKeys: String, CodingKey {
        case type, event, positionMs, durationMs, message, streamPath, title
    }

    public static func started(streamPath: String, title: String?, durationMs: Int64) -> EventMessage {
        EventMessage(event: "started", positionMs: 0, durationMs: durationMs, streamPath: streamPath, title: title)
    }

    public static func position(_ positionMs: Int64) -> EventMessage {
        EventMessage(event: "position", positionMs: positionMs)
    }

    public static func paused(_ positionMs: Int64) -> EventMessage {
        EventMessage(event: "paused", positionMs: positionMs)
    }

    public static func resumed(_ positionMs: Int64) -> EventMessage {
        EventMessage(event: "resumed", positionMs: positionMs)
    }

    public static func ended(_ positionMs: Int64) -> EventMessage {
        EventMessage(event: "ended", positionMs: positionMs)
    }

    public static func error(_ message: String) -> EventMessage {
        EventMessage(event: "error", message: message)
    }
}

/// A desktop->phone command, decoded from the WebSocket. All fields optional
/// except `cmd`, mirroring the shapes the desktop sends.
public struct RemoteCommand: Codable, Equatable, Sendable {
    public var type: String?
    public var cmd: String
    public var value: Int?
    public var positionMs: Int64?
    public var rate: Double?
    public var streamPath: String?
    public var title: String?

    public init(
        cmd: String,
        value: Int? = nil,
        positionMs: Int64? = nil,
        rate: Double? = nil,
        streamPath: String? = nil,
        title: String? = nil
    ) {
        self.cmd = cmd
        self.value = value
        self.positionMs = positionMs
        self.rate = rate
        self.streamPath = streamPath
        self.title = title
    }
}