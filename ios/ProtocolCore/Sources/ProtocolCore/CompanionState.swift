import Foundation

/// Mirror of the desktop's `CompanionState` record, broadcast roughly every
/// 500ms. The desktop owns the session; phones only interpret this snapshot.
public struct CompanionState: Codable, Equatable, Sendable {
    /// Title of the currently loaded song (desktop or phone source).
    public var songTitle: String?
    /// Album the song belongs to.
    public var album: String?
    /// Stream path relative to the companion server (localhost in UI shows
    /// the "0:" Music folder, e.g. "Library/song.mp3").
    public var streamPath: String?
    public var durationMs: Int64
    public var positionMs: Int64
    public var playing: Bool
    public var volume: Int
    public var queue: [String]
    /// "desktop" or "phone" - who is the active playback engine.
    public var activeSource: String

    public static let sourceDesktop = "desktop"
    public static let sourcePhone = "phone"

    public static let empty = CompanionState(
        songTitle: nil,
        album: nil,
        streamPath: nil,
        durationMs: 0,
        positionMs: 0,
        playing: false,
        volume: 0,
        queue: [],
        activeSource: sourceDesktop
    )

    public init(
        songTitle: String?,
        album: String?,
        streamPath: String?,
        durationMs: Int64,
        positionMs: Int64,
        playing: Bool,
        volume: Int,
        queue: [String],
        activeSource: String
    ) {
        self.songTitle = songTitle
        self.album = album
        self.streamPath = streamPath
        self.durationMs = durationMs
        self.positionMs = positionMs
        self.playing = playing
        self.volume = volume
        self.queue = queue
        self.activeSource = activeSource
    }

    /// Lenient decode: missing fields fall back to defaults so a state
    /// broadcast from a slightly different desktop version still parses.
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        songTitle = try container.decodeIfPresent(String.self, forKey: .songTitle)
        album = try container.decodeIfPresent(String.self, forKey: .album)
        streamPath = try container.decodeIfPresent(String.self, forKey: .streamPath)
        durationMs = try container.decodeIfPresent(Int64.self, forKey: .durationMs) ?? 0
        positionMs = try container.decodeIfPresent(Int64.self, forKey: .positionMs) ?? 0
        playing = try container.decodeIfPresent(Bool.self, forKey: .playing) ?? false
        volume = try container.decodeIfPresent(Int.self, forKey: .volume) ?? 0
        queue = try container.decodeIfPresent([String].self, forKey: .queue) ?? []
        activeSource = try container.decodeIfPresent(String.self, forKey: .activeSource)
            ?? Self.sourceDesktop
    }
}