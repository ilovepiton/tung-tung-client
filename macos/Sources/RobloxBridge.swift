import Foundation
import CoreFoundation
import Network
import Security

/// A transport for a compatible runtime. This does not inject code into Roblox Player.
/// All state is session-scoped, acknowledged by the runtime, and expires on disconnect.
final class RobloxBridge {
    static let moduleIDs = ["fly", "noclip", "speed", "super_jump", "low_gravity", "dash", "double_jump", "air_jump", "self_fling", "friend_fling", "slide", "spin", "freeze", "respawn", "return_spawn", "spectate", "freecam", "fov", "fullbright", "sunset", "bloom", "depth_of_field", "color_grade", "trails"]
    static let settingRanges: [String: ClosedRange<Double>] = ["flySpeed": 1...300, "walkSpeed": 1...100, "freecamSpeed": 1...300, "fov": 30...120]
    var onState: (([String: Any]) -> Void)?
    let directory: URL
    private let io = DispatchQueue(label: "dev.tungtung.roblox.transport")
    private var listener: NWListener?
    private var timer: Timer?
    private var key = ""
    private var session = ""
    private var heartbeat = Date.distantPast
    private var revision = -1
    private var snapshot: [String: Any] = [:]
    private var queue: [[String: Any]] = []
    private var requests: [String: Pending] = [:]
    private var transportError = ""
    private var clients = 0 // Accessed only by io.
    private struct Pending {
        let session: String
        let deadline: Date
        let completion: (Result<[String: Any], Error>) -> Void
    }

    init(directory: URL? = nil) {
        self.directory = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("TungTungClient/Roblox", isDirectory: true)
    }
    var state: [String: Any] {
        let live = !session.isEmpty && Date().timeIntervalSince(heartbeat) < 5
        var result: [String: Any] = ["connected": live, "verified": live, "installed": false,
            "sessionId": live ? session : "", "revision": revision, "protocolVersion": 1,
            "connectionScope": "local-runtime", "serverConfirmed": false,
            "requiresRuntime": true, "settings": snapshot["settings"] ?? [:],
            "players": live ? (snapshot["players"] ?? []) : [],
            "placeName": live ? (snapshot["placeName"] ?? "Roblox") : "No runtime connected",
            "serverType": live ? (snapshot["serverType"] ?? "unknown") : "unknown",
            "message": live ? "Runtime connected. Local effects may be corrected by the game server." :
                "A compatible in-game runtime is required. This app does not inject code into Roblox Player."]
        result["modules"] = live ? (snapshot["modules"] ?? []) : Self.moduleIDs.map {
            ["id": $0, "enabled": false, "available": false, "status": "Runtime required", "scope": "unavailable"] as [String: Any]
        }
        if !transportError.isEmpty { result["error"] = transportError }
        return result
    }
    func start() {
        guard listener == nil else { return }
        do {
            var bytes = [UInt8](repeating: 0, count: 32)
            guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else { throw failure("Could not create the local runtime key.") }
            key = bytes.map { String(format: "%02x", $0) }.joined()
            let parameters = NWParameters.tcp
            parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: .any)
            let next = try NWListener(using: parameters)
            listener = next
            next.newConnectionHandler = { [weak self] connection in self?.accept(connection) }
            next.stateUpdateHandler = { [weak self, weak next] status in
                guard let self else { return }
                switch status {
                case .ready:
                    guard let port = next?.port else { return }
                    DispatchQueue.main.async { self.writeDescriptor(port: Int(port.rawValue)) }
                case .failed:
                    DispatchQueue.main.async { self.transportError = "The local Roblox transport could not start."; self.disconnect(); self.listener?.cancel(); self.listener = nil }
                default: break
                }
            }
            next.start(queue: io)
            let tick = Timer(timeInterval: 0.5, repeats: true) { [weak self] _ in self?.expire() }
            RunLoop.main.add(tick, forMode: .common); timer = tick
        } catch { transportError = error.localizedDescription; onState?(state) }
    }
    func stop() {
        timer?.invalidate(); timer = nil; listener?.cancel(); listener = nil
        disconnect(); key = ""
        try? FileManager.default.removeItem(at: directory.appendingPathComponent("connection.json"))
    }
    private func writeDescriptor(port: Int) {
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true,
                                                   attributes: [.posixPermissions: 0o700])
            try FileManager.default.setAttributes([.posixPermissions: 0o700], ofItemAtPath: directory.path)
            let descriptor: [String: Any] = ["protocolVersion": 1, "endpoint": "http://127.0.0.1:\(port)/v1/", "key": key,
                "processId": ProcessInfo.processInfo.processIdentifier, "requiresCompatibleRuntime": true]
            let data = try JSONSerialization.data(withJSONObject: descriptor, options: .prettyPrinted)
            let file = directory.appendingPathComponent("connection.json")
            try data.write(to: file, options: [.atomic, .completeFileProtectionUnlessOpen])
            try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: file.path)
            transportError = ""; onState?(state)
        } catch { transportError = "Could not write the private runtime connection file."; listener?.cancel(); listener = nil; onState?(state) }
    }
    private func failure(_ message: String) -> Error { NativeBridgeError.unavailable(message) }
    private func disconnect() {
        session = ""; revision = -1; snapshot = [:]; queue.removeAll()
        let abandoned = requests.values; requests.removeAll()
        for request in abandoned { request.completion(.failure(failure("The Roblox runtime disconnected. Reconnect before applying an action."))) }
        onState?(state)
    }
    private func expire() {
        if !session.isEmpty && Date().timeIntervalSince(heartbeat) >= 5 { disconnect(); return }
        let expired = requests.filter { $0.value.deadline < Date() }.map(\.key)
        for id in expired {
            let pending = requests.removeValue(forKey: id)
            queue.removeAll { $0["requestId"] as? String == id }
            pending?.completion(.failure(failure("The runtime did not acknowledge this action. Its state has not been changed in the app.")))
        }
    }
    func send(_ payload: [String: Any], completion: @escaping (Result<[String: Any], Error>) -> Void) {
        expire()
        do {
            guard state["connected"] as? Bool == true, payload["sessionId"] as? String == session else {
                throw failure("Connect a compatible Roblox runtime before using this control.")
            }
            guard requests.count < 32 else { throw failure("Wait for pending Roblox actions to finish.") }
            var command: [String: Any] = [:]
            switch payload["command"] as? String {
            case "setModule":
                guard let id = payload["moduleId"] as? String, Self.moduleIDs.contains(id),
                      let enabled = boolean(payload["enabled"]), moduleAvailable(id) else { throw failure("This module is unavailable in the connected runtime.") }
                guard !enabled || !["friend_fling", "spectate"].contains(id) else { throw failure("Choose a player before using this action.") }
                command = ["command": "setModule", "moduleId": id, "enabled": enabled]
            case "setSettings":
                guard let settings = payload["settings"] as? [String: Any], !settings.isEmpty else { throw failure("Choose a Roblox setting.") }
                command = ["command": "setSettings", "settings": try validateSettings(settings)]
            case "playerAction":
                guard let action = payload["action"] as? String, ["friend_fling", "spectate"].contains(action), moduleAvailable(action),
                      let target = integer(payload["targetUserId"]), target > 0,
                      (snapshot["players"] as? [[String: Any]] ?? []).contains(where: { integer($0["userId"]) == target && (action != "friend_fling" || $0["flingAllowed"] as? Bool == true) }) else {
                    throw failure("This player action is unavailable or the player has left.")
                }
                command = ["command": "playerAction", "action": action, "targetUserId": target]
            default: throw failure("Unknown Roblox command.")
            }
            let id = UUID().uuidString
            command["requestId"] = id; command["sessionId"] = session
            requests[id] = Pending(session: session, deadline: Date().addingTimeInterval(7), completion: completion)
            queue.append(command)
        } catch { completion(.failure(error)) }
    }
    private func moduleAvailable(_ id: String) -> Bool {
        (snapshot["modules"] as? [[String: Any]] ?? []).contains { $0["id"] as? String == id && $0["available"] as? Bool == true }
    }
    private func boolean(_ value: Any?) -> Bool? {
        guard let number = value as? NSNumber, CFGetTypeID(number) == CFBooleanGetTypeID() else { return nil }; return number.boolValue
    }
    private func integer(_ value: Any?) -> Int? {
        guard let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID(), number.doubleValue.isFinite,
              number.doubleValue.rounded() == number.doubleValue, abs(number.doubleValue) <= 9_007_199_254_740_991 else { return nil }
        return number.intValue
    }
    private func validateSettings(_ value: [String: Any]) throws -> [String: Any] {
        var result: [String: Any] = [:]
        for (key, raw) in value {
            if let range = Self.settingRanges[key], let number = raw as? NSNumber,
               CFGetTypeID(number) != CFBooleanGetTypeID(), number.doubleValue.isFinite, range.contains(number.doubleValue) { result[key] = number.doubleValue }
            else if key == "shaderPreset", let preset = raw as? String, ["warm", "cinematic", "vibrant", "clean"].contains(preset) { result[key] = preset }
            else { throw failure("Invalid Roblox setting: \(key.prefix(40)).") }
        }
        return result
    }
    private func validateSnapshot(_ raw: [String: Any]) throws -> (Int, [String: Any]) {
        guard let next = integer(raw["revision"]), next >= 0,
              let modules = raw["modules"] as? [[String: Any]], modules.count == Self.moduleIDs.count else { throw failure("Incomplete runtime state.") }
        var cleanModules: [[String: Any]] = []; var seen = Set<String>()
        for module in modules {
            guard let id = module["id"] as? String, Self.moduleIDs.contains(id), seen.insert(id).inserted,
                  let available = boolean(module["available"]), let enabled = boolean(module["enabled"]), !enabled || available else { throw failure("Invalid runtime module state.") }
            cleanModules.append(["id": id, "available": available, "enabled": enabled,
                "status": String((module["status"] as? String ?? "Local runtime").prefix(160)),
                "scope": ["local", "server", "unavailable"].contains(module["scope"] as? String ?? "") ? module["scope"]! : "local"])
        }
        let players = raw["players"] as? [[String: Any]] ?? []
        guard players.count <= 200 else { throw failure("Too many players in runtime state.") }
        var cleanPlayers: [[String: Any]] = []; var playerIDs = Set<Int>()
        for player in players {
            guard let id = integer(player["userId"]), id > 0, playerIDs.insert(id).inserted,
                  let name = player["name"] as? String, !name.isEmpty else { throw failure("Invalid runtime player state.") }
            cleanPlayers.append(["userId": id, "name": String(name.prefix(64)), "displayName": String((player["displayName"] as? String ?? name).prefix(64)),
                                 "flingAllowed": boolean(player["flingAllowed"]) == true])
        }
        let settings = try validateSettings(raw["settings"] as? [String: Any] ?? [:])
        let type = raw["serverType"] as? String ?? "unknown"
        return (next, ["revision": next, "modules": cleanModules, "players": cleanPlayers, "settings": settings,
            "placeName": String((raw["placeName"] as? String ?? "Roblox").prefix(128)),
            "serverType": ["private", "public", "studio", "unknown"].contains(type) ? type : "unknown"])
    }
    /// Main-thread protocol handler. No session/key is accepted from the WebView.
    private func handle(path: String, object: [String: Any]) throws -> [String: Any] {
        expire()
        if path == "/v1/connect" {
            guard integer(object["protocolVersion"]) == 1, let raw = object["snapshot"] as? [String: Any] else { throw failure("Unsupported runtime protocol.") }
            let (next, clean) = try validateSnapshot(raw)
            disconnect(); session = UUID().uuidString; heartbeat = Date(); revision = next; snapshot = clean; onState?(state)
            return ["ok": true, "sessionId": session, "heartbeatSeconds": 1, "expiresAfterSeconds": 5]
        }
        guard !session.isEmpty, object["sessionId"] as? String == session else { throw failure("The runtime session has expired.") }
        switch path {
        case "/v1/poll":
            heartbeat = Date(); let commands = queue; queue.removeAll()
            return ["ok": true, "commands": commands]
        case "/v1/state", "/v1/ack":
            guard let raw = object["snapshot"] as? [String: Any] else { throw failure("Runtime state is required.") }
            let (next, clean) = try validateSnapshot(raw)
            guard next > revision else { throw failure("Stale runtime state was rejected.") }
            var request: Pending?
            if path == "/v1/ack" {
                guard let id = object["requestId"] as? String, let pending = requests[id], pending.session == session,
                      boolean(object["ok"]) != nil else { throw failure("The acknowledgement does not belong to a pending action.") }
                request = requests.removeValue(forKey: id)
                queue.removeAll { $0["requestId"] as? String == id }
            }
            revision = next; snapshot = clean; heartbeat = Date(); onState?(state)
            if let request {
                if object["ok"] as? Bool == true { request.completion(.success(state)) }
                else { request.completion(.failure(failure(String((object["error"] as? String ?? "The runtime could not apply the action.").prefix(300))))) }
            }
            return ["ok": true]
        case "/v1/disconnect": disconnect(); return ["ok": true]
        default: throw failure("Unknown runtime endpoint.")
        }
    }

    private func accept(_ connection: NWConnection) {
        guard clients < 16 else { connection.cancel(); return }; clients += 1
        let peer = Peer(connection: connection, queue: io) { [weak self] path, headers, body, respond in
            guard let self else { respond(503, ["error": "Transport unavailable"]); return }
            DispatchQueue.main.async {
                guard headers["x-tung-roblox-key"] == self.key, !self.key.isEmpty, headers["origin"] == nil else {
                    respond(403, ["error": "Local runtime authentication required"]); return
                }
                do {
                    guard let object = try JSONSerialization.jsonObject(with: body) as? [String: Any] else { throw self.failure("JSON object required.") }
                    respond(200, try self.handle(path: path, object: object))
                } catch { respond(400, ["error": error.localizedDescription]) }
            }
        } closed: { [weak self] in self?.clients -= 1 }
        peer.start()
    }
    private final class Peer {
        let connection: NWConnection
        let queue: DispatchQueue
        let handler: (String, [String: String], Data, @escaping (Int, [String: Any]) -> Void) -> Void
        let closed: () -> Void
        var buffer = Data(); var finished = false; var responded = false
        init(connection: NWConnection, queue: DispatchQueue,
             handler: @escaping (String, [String: String], Data, @escaping (Int, [String: Any]) -> Void) -> Void,
             closed: @escaping () -> Void) {
            self.connection = connection; self.queue = queue; self.handler = handler; self.closed = closed
        }
        func start() {
            connection.start(queue: queue)
            // Retain the bounded peer until completion, including clients that never send a header.
            queue.asyncAfter(deadline: .now() + 4) { [self] in finish() }
            receive()
        }
        func finish() { guard !finished else { return }; finished = true; connection.cancel(); closed() }
        func respond(_ status: Int, _ object: [String: Any]) {
            queue.async { [self] in
                guard !finished, !responded else { return }; responded = true
                let body = (try? JSONSerialization.data(withJSONObject: object)) ?? Data("{}".utf8)
                let header = "HTTP/1.1 \(status) \(status == 200 ? "OK" : "Error")\r\nContent-Type: application/json\r\nContent-Length: \(body.count)\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
                connection.send(content: Data(header.utf8) + body, completion: .contentProcessed { [self] _ in finish() })
            }
        }
        func receive() {
            connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [self] data, _, complete, error in
                guard !finished else { return }
                if let data { buffer.append(data) }
                guard buffer.count <= 73728 else { respond(413, ["error": "Request too large"]); return }
                if let boundary = buffer.range(of: Data("\r\n\r\n".utf8)) {
                    guard boundary.lowerBound <= 8192, let head = String(data: buffer[..<boundary.lowerBound], encoding: .utf8) else { respond(400, ["error": "Invalid header"]); return }
                    let lines = head.components(separatedBy: "\r\n"); let request = lines[0].split(separator: " ")
                    guard request.count == 3, request[0] == "POST", request[2] == "HTTP/1.1", ["/v1/connect", "/v1/poll", "/v1/state", "/v1/ack", "/v1/disconnect"].contains(String(request[1])) else { respond(400, ["error": "Unknown request"]); return }
                    var headers: [String: String] = [:]
                    for line in lines.dropFirst() {
                        guard let colon = line.firstIndex(of: ":") else { respond(400, ["error": "Invalid header"]); return }
                        let name = line[..<colon].lowercased()
                        guard headers[name] == nil else { respond(400, ["error": "Duplicate header"]); return }
                        headers[name] = String(line[line.index(after: colon)...]).trimmingCharacters(in: .whitespaces)
                    }
                    guard headers["transfer-encoding"] == nil, headers["content-type"]?.split(separator: ";").first == "application/json",
                          let rawLength = headers["content-length"], !rawLength.isEmpty, rawLength.allSatisfy(\.isNumber),
                          let length = Int(rawLength), (2...65536).contains(length) else { respond(400, ["error": "Bounded JSON body required"]); return }
                    let offset = boundary.upperBound
                    if buffer.count >= offset + length {
                        guard buffer.count == offset + length else { respond(400, ["error": "Unexpected trailing data"]); return }
                        handler(String(request[1]), headers, buffer.subdata(in: offset..<(offset + length)), respond); return
                    }
                } else if buffer.count > 8192 { respond(400, ["error": "Header too large"]); return }
                if complete || error != nil { finish() } else { receive() }
            }
        }
    }
}
