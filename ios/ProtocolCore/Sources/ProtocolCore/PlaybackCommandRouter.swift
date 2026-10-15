import Foundation

/// A playback instruction decoded from a desktop `RemoteCommand`, ready for
/// the app layer to execute against AVPlayer (or any engine).
public enum PlaybackAction: Equatable, Sendable {
    case play(streamPath: String, title: String?)
    case pause
    case resume
    case stop
    case seek(positionMs: Int64)
    case rate(Double)
    case volume(Int)
}

/// Maps desktop->phone commands onto `PlaybackAction`s, mirroring the
/// Android `MainActivity.onCommand` switch. Unknown or malformed commands
/// yield `nil` and are ignored.
public enum PlaybackCommandRouter {
    public static func action(for command: RemoteCommand) -> PlaybackAction? {
        switch command.cmd {
        case "play":
            guard let streamPath = command.streamPath else { return nil }
            return .play(streamPath: streamPath, title: command.title)
        case "pause":
            return .pause
        case "resume":
            return .resume
        case "stop":
            return .stop
        case "seek":
            guard let positionMs = command.positionMs else { return nil }
            return .seek(positionMs: positionMs)
        case "rate":
            guard let rate = command.rate else { return nil }
            return .rate(rate)
        case "volume":
            guard let value = command.value else { return nil }
            return .volume(value)
        default:
            return nil
        }
    }
}