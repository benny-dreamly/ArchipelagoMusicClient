import Foundation

/// Observes the companion service. All callbacks are delivered on the main
/// actor, matching the Android client's main-thread UI contract.
@MainActor
public protocol CompanionServiceDelegate: AnyObject {
    func companionServiceDidOpen(_ service: CompanionService)
    func companionServiceDidClose(_ service: CompanionService, reason: String)
    func companionService(_ service: CompanionService, didReceive state: CompanionState)
    func companionService(_ service: CompanionService, didReceive command: RemoteCommand)
}

/// Ports used by the desktop companion server.
public enum CompanionPorts {
    public static let http = 8311
    public static let ws = 8312
}

/// Talks to the desktop's companion server: HTTP /health to verify the host,
/// then a WebSocket on port 8312 for control and state. Owns the receive loop
/// and re-arms it after every message so drops surface as `didClose`.
@MainActor
public final class CompanionService {
    public let httpPort: Int
    public let wsPort: Int
    public private(set) var host: String?

    private weak var delegate: (any CompanionServiceDelegate)?
    private var webSocketTask: URLSessionWebSocketTask?
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    public init(
        delegate: any CompanionServiceDelegate,
        httpPort: Int = CompanionPorts.http,
        wsPort: Int = CompanionPorts.ws
    ) {
        self.delegate = delegate
        self.httpPort = httpPort
        self.wsPort = wsPort
    }

    /// GET /health on the HTTP port. The phone pings this before opening the
    /// WebSocket so a wrong IP fails fast.
    public func checkHealth(_ host: String) async -> Bool {
        guard let url = URL(string: "http://\(host):\(httpPort)/health") else { return false }
        var request = URLRequest(url: url)
        request.timeoutInterval = 3
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
                return false
            }
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                return false
            }
            return (json["ok"] as? Bool) ?? false
        } catch {
            return false
        }
    }

    /// URL for Range-capable streaming of a stream path, e.g.
    /// `http://host:8311/Library/song.mp3`.
    public func streamURL(_ streamPath: String) -> URL? {
        guard let host else { return nil }
        return URL(string: "http://\(host):\(httpPort)\(streamPath)")
    }

    public func connect(host: String) {
        disconnect()
        self.host = host
        guard let url = URL(string: "ws://\(host):\(wsPort)") else {
            delegate?.companionServiceDidClose(self, reason: "bad address")
            return
        }
        let task = URLSession.shared.webSocketTask(with: url)
        webSocketTask = task
        task.resume()
        receiveLoop(for: task)
    }

    public func disconnect() {
        webSocketTask?.cancel(with: .goingAway, reason: nil)
        webSocketTask = nil
        host = nil
    }

    private func receiveLoop(for task: URLSessionWebSocketTask) {
        task.receive { [weak self] result in
            Task { @MainActor in
                guard let self, self.webSocketTask === task else { return }
                switch result {
                case .success(let message):
                    if case .string(let text) = message {
                        self.handle(text)
                    }
                    self.receiveLoop(for: task)
                case .failure(let error):
                    self.webSocketTask = nil
                    self.delegate?.companionServiceDidClose(
                        self, reason: error.localizedDescription)
                }
            }
        }
    }

    private func handle(_ text: String) {
        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return
        }
        if let type = json["type"] as? String, type == "command",
           let command = try? decoder.decode(RemoteCommand.self, from: data) {
            delegate?.companionService(self, didReceive: command)
            return
        }
        if let state = try? decoder.decode(CompanionState.self, from: data) {
            delegate?.companionService(self, didReceive: state)
        }
    }

    // MARK: - Outbound

    public func sendToggle() { send(CommandMessage.toggle) }
    public func sendPlay() { send(CommandMessage.play) }
    public func sendPause() { send(CommandMessage.pause) }
    public func sendNext() { send(CommandMessage.next) }
    public func sendSeek(_ positionMs: Int64) { send(CommandMessage.seek(positionMs)) }
    public func sendVolume(_ value: Int) { send(CommandMessage.volume(value)) }

    public func sendEvent(_ message: EventMessage) {
        send(message)
    }

    private func send(_ message: some Encodable & Sendable) {
        guard let webSocketTask,
              let data = try? encoder.encode(message),
              let text = String(data: data, encoding: .utf8) else {
            return
        }
        webSocketTask.send(.string(text)) { _ in }
    }
}