import Foundation

/// Controls the phone's playback engine and translates AVPlayer lifecycle
/// observations into the desktop protocol events. Pure and testable: the app
/// layer feeds it AVPlayer-derived events and gets back `EventMessage`s to
/// report over the WebSocket.
public struct RemotePlayerStateMachine: Sendable {
    public private(set) var state: PlayerState = .idle
    public private(set) var positionMs: Int64 = 0
    public private(set) var hasActiveSession = false

    public struct Session: Equatable, Sendable {
        public let streamPath: String
        public let title: String?
        public let durationMs: Int64

        public init(streamPath: String, title: String?, durationMs: Int64) {
            self.streamPath = streamPath
            self.title = title
            self.durationMs = durationMs
        }
    }

    private var session: Session?
    private var hasSentStarted = false

    public init() {}

    public enum PlayerState: Equatable, Sendable {
        case idle
        case buffering
        case playing
        case paused
        case ended
        case failed(message: String)
    }

    public enum PlayerEvent: Equatable, Sendable {
        /// The asset has resolved its duration (first ready / prepared).
        case ready(durationMs: Int64)
        /// AVPlayer started or stopped playing (timeControlStatus flips).
        case isPlaying(Bool)
        /// Periodic position tick from the AVPlayer time observer.
        case position(Int64)
        /// The item played to its natural end.
        case didPlayToEnd
        /// Playback failed.
        case failed(message: String)
    }

    /// Feeds a raw player event in and computes the events the phone should
    /// send back to the desktop, updating internal state as a side effect.
    public mutating func apply(_ event: PlayerEvent) -> [EventMessage] {
        switch event {
        case .ready(let durationMs):
            guard let session else { return [] }
            state = .buffering
            guard !hasSentStarted else { return [] }
            hasSentStarted = true
            return [.started(
                streamPath: session.streamPath,
                title: session.title,
                durationMs: durationMs
            )]

        case .isPlaying(true):
            guard hasActiveSession else { return [] }
            let wasPlaying = state == .playing
            state = .playing
            return wasPlaying ? [] : [.resumed(positionMs)]

        case .isPlaying(false):
            let wasPlaying = state == .playing
            state = .paused
            return wasPlaying ? [.paused(positionMs)] : []

        case .position(let ms):
            positionMs = ms
            return state == .playing ? [.position(ms)] : []

        case .didPlayToEnd:
            let endPosition = positionMs
            hasActiveSession = false
            session = nil
            hasSentStarted = false
            state = .ended
            positionMs = 0
            return [.ended(endPosition)]

        case .failed(let message):
            state = .failed(message: message)
            return [.error(message)]
        }
    }

    public mutating func beginPlayback(streamPath: String, title: String?) {
        session = Session(streamPath: streamPath, title: title, durationMs: 0)
        hasActiveSession = true
        hasSentStarted = false
        positionMs = 0
        state = .buffering
    }

    public mutating func stopPlayback() {
        session = nil
        hasActiveSession = false
        hasSentStarted = false
        state = .idle
        positionMs = 0
    }
}