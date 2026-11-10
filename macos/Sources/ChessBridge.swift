import Foundation
import Network
import Security

/// Private loopback settings transport for the Chess.com extension. No account or board moves.
final class ChessBridge {
    static let port: UInt16 = 17463
    // Set to the public-key-derived ID by the extension build script.
    static let extensionID = "kbaaahmkgknhojdafppilohndobfpdci"
    let session: ChessSession
    var onState: (() -> Void)?
    var nativeStatus: (() -> [String: Any])?
    private let defaults: UserDefaults?
    private let io = DispatchQueue(label: "dev.tungtung.chess.transport")
    private var listener: NWListener?
    private var timer: Timer?
    private var key = ""
    private var clients = 0
    private var ready = false
    private var transportError = ""
    init(defaults: UserDefaults?) {
        self.defaults = defaults
        session = ChessSession(saved: defaults?.dictionary(forKey: "chessAutoNext") ?? [:])
        session.onSave = { [weak defaults] value in defaults?.set(value, forKey: "chessAutoNext") }
    }
    var state: [String: Any] {
        var value = session.state
        value["browserReady"] = ready; value["error"] = transportError
        for (name, field) in nativeStatus?() ?? [:] { value[name] = field }
        return value
    }
    func update(_ patch: [String: Any]) throws -> [String: Any] {
        try session.update(patch); onState?(); return state
    }
    func nativePoll(_ object: [String: Any]) throws -> [String: Any] {
        let result = try session.poll(object, channel: "Client window"); onState?(); return result
    }
    func nativeStop(_ object: [String: Any]) throws {
        try session.stop(clientId: object["clientId"] as? String ?? ""); onState?()
    }
    var pairingURL: URL? {
        guard ready, !key.isEmpty else { return nil }
        return URL(string: "chrome-extension://\(Self.extensionID)/options.html#pair=\(key)")
    }
    func start() {
        guard listener == nil else { return }
        do {
            if let saved = defaults?.string(forKey: "chessLocalKey"), saved.count == 64,
               saved.allSatisfy({ $0.isHexDigit && $0.isASCII }) { key = saved }
            else {
                var bytes = [UInt8](repeating: 0, count: 32)
                guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else { throw failure("Cannot prepare the Chess connection.") }
                key = bytes.map { String(format: "%02x", $0) }.joined()
                defaults?.set(key, forKey: "chessLocalKey")
            }
            let parameters = NWParameters.tcp
            parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: Self.port)!)
            let next = try NWListener(using: parameters); listener = next
            next.newConnectionHandler = { [weak self] in self?.accept($0) }
            next.stateUpdateHandler = { [weak self] status in
                DispatchQueue.main.async {
                    guard let self else { return }
                    if case .ready = status { self.ready = true; self.transportError = ""; self.onState?() }
                    else if case .failed = status { self.ready = false; self.transportError = "The Chrome connection is unavailable. The client window still works."; self.onState?() }
                }
            }
            next.start(queue: io)
            let tick = Timer(timeInterval: 1, repeats: true) { [weak self] _ in self?.session.expire(); self?.onState?() }
            RunLoop.main.add(tick, forMode: .common); timer = tick
        } catch { transportError = error.localizedDescription; onState?() }
    }
    func stop() { timer?.invalidate(); timer = nil; listener?.cancel(); listener = nil; ready = false }
    private func failure(_ text: String) -> Error { NSError(domain: "TungChess", code: 2, userInfo: [NSLocalizedDescriptionKey: text]) }
    private func accept(_ connection: NWConnection) {
        guard clients < 16 else { connection.cancel(); return }; clients += 1
        let peer = Peer(connection: connection, queue: io, handler: { [weak self] method, path, headers, body, respond in
            DispatchQueue.main.async {
                guard let self else { respond(503, ["error": "Client closed"], false); return }
                let expected = "chrome-extension://\(Self.extensionID)"
                let origin = headers["origin"]
                let cors = origin == expected
                guard headers["host"] == "127.0.0.1:\(Self.port)", origin == nil || cors else { respond(403, ["error": "Extension origin required"], false); return }
                if method == "OPTIONS" {
                    guard cors, headers["access-control-request-method"] == "POST" else { respond(403, [:], false); return }
                    respond(200, [:], true); return
                }
                guard headers["x-tung-chess-key"] == self.key, !self.key.isEmpty else { respond(403, ["error": "Connect the extension from Tung Tung Client"], cors); return }
                do {
                    guard let object = try JSONSerialization.jsonObject(with: body) as? [String: Any] else { throw self.failure("JSON object required") }
                    if path == "/chess/settings" { respond(200, self.state, cors) }
                    else if path == "/chess/poll" {
                        let value = try self.session.poll(object, channel: "Chrome"); self.onState?(); respond(200, value, cors)
                    } else if path == "/chess/stop" {
                        try self.session.stop(clientId: object["clientId"] as? String ?? ""); self.onState?(); respond(200, ["ok": true], cors)
                    } else { respond(404, [:], cors) }
                } catch { respond(400, ["error": error.localizedDescription], cors) }
            }
        }, closed: { [weak self] in self?.clients -= 1 })
        peer.start()
    }
    private final class Peer {
        typealias Respond = (Int, [String: Any], Bool) -> Void
        let connection: NWConnection
        let queue: DispatchQueue
        let handler: (String, String, [String: String], Data, @escaping Respond) -> Void
        let closed: () -> Void
        var buffer = Data(), finished = false, responded = false
        init(connection: NWConnection, queue: DispatchQueue,
             handler: @escaping (String, String, [String: String], Data, @escaping Respond) -> Void, closed: @escaping () -> Void) {
            self.connection = connection; self.queue = queue; self.handler = handler; self.closed = closed
        }
        func start() { connection.start(queue: queue); queue.asyncAfter(deadline: .now() + 4) { [self] in finish() }; receive() }
        func finish() { guard !finished else { return }; finished = true; connection.cancel(); closed() }
        func respond(_ status: Int, _ object: [String: Any], _ cors: Bool) {
            queue.async { [self] in
                guard !finished, !responded else { return }; responded = true
                let body = (try? JSONSerialization.data(withJSONObject: object)) ?? Data("{}".utf8)
                var header = "HTTP/1.1 \(status) \(status == 200 ? "OK" : "Error")\r\nContent-Type: application/json\r\nContent-Length: \(body.count)\r\nCache-Control: no-store\r\nConnection: close\r\n"
                if cors { header += "Access-Control-Allow-Origin: chrome-extension://\(ChessBridge.extensionID)\r\nVary: Origin\r\nAccess-Control-Allow-Methods: POST, OPTIONS\r\nAccess-Control-Allow-Headers: content-type, x-tung-chess-key\r\nAccess-Control-Allow-Private-Network: true\r\n" }
                connection.send(content: Data((header + "\r\n").utf8) + body, completion: .contentProcessed { [self] _ in finish() })
            }
        }
        func receive() {
            connection.receive(minimumIncompleteLength: 1, maximumLength: 16384) { [self] data, _, complete, error in
                guard !finished else { return }; if let data { buffer.append(data) }
                guard buffer.count <= 12288 else { respond(413, [:], false); return }
                if let boundary = buffer.range(of: Data("\r\n\r\n".utf8)) {
                    guard boundary.lowerBound <= 8192, let head = String(data: buffer[..<boundary.lowerBound], encoding: .utf8) else { respond(400, [:], false); return }
                    let lines = head.components(separatedBy: "\r\n"), request = lines[0].split(separator: " ")
                    guard request.count == 3, ["POST", "OPTIONS"].contains(String(request[0])), request[2] == "HTTP/1.1",
                          ["/chess/poll", "/chess/stop", "/chess/settings"].contains(String(request[1])) else { respond(400, [:], false); return }
                    var headers: [String: String] = [:]
                    for line in lines.dropFirst() {
                        guard let colon = line.firstIndex(of: ":") else { respond(400, [:], false); return }
                        let name = line[..<colon].lowercased()
                        guard headers[name] == nil else { respond(400, [:], false); return }
                        headers[name] = String(line[line.index(after: colon)...]).trimmingCharacters(in: .whitespaces)
                    }
                    guard headers["transfer-encoding"] == nil else { respond(400, [:], false); return }
                    if request[0] == "OPTIONS" { handler("OPTIONS", String(request[1]), headers, Data(), respond); return }
                    guard headers["content-type"]?.split(separator: ";").first == "application/json",
                          let raw = headers["content-length"], raw.allSatisfy(\.isNumber), let length = Int(raw), (2...4096).contains(length) else { respond(400, [:], false); return }
                    let end = boundary.upperBound + length
                    if buffer.count >= end {
                        guard buffer.count == end else { respond(400, [:], false); return }
                        handler("POST", String(request[1]), headers, buffer.subdata(in: boundary.upperBound..<end), respond); return
                    }
                } else if buffer.count > 8192 { respond(400, [:], false); return }
                if complete || error != nil { finish() } else { receive() }
            }
        }
    }
}
