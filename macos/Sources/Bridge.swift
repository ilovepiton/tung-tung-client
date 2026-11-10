import Foundation
import CoreFoundation

enum NativeBridgeError: LocalizedError {
    case unavailable(String)
    var errorDescription: String? {
        if case .unavailable(let message) = self { return message }
        return nil
    }
}

struct ConnectionDescriptor {
    let port: Int
    private let token: String
    let identity: String

    init(data: Data) throws {
        guard data.count <= 8192,
              let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let portNumber = object["port"] as? NSNumber,
              CFGetTypeID(portNumber) != CFBooleanGetTypeID(),
              portNumber.doubleValue.isFinite,
              portNumber.doubleValue.rounded() == portNumber.doubleValue,
              (1...65535).contains(portNumber.intValue),
              let value = object["token"] as? String,
              value.range(of: "^[0-9a-fA-F]{64}$", options: .regularExpression) != nil else {
            throw NativeBridgeError.unavailable("The game connection file is invalid. Restart the Fabric profile.")
        }
        port = portNumber.intValue
        token = value
        // Never expose this identity to JavaScript, logs, self-test output or preferences.
        identity = "\(port):\(value)"
    }
    func authenticate(_ request: inout URLRequest) { request.setValue(token, forHTTPHeaderField: "X-Tun-Token") }
}

/** A single bounded loopback HTTP operation. Redirects never receive the auth header. */
private final class LimitedRequest: NSObject, URLSessionDataDelegate {
    private static let maximumResponse = 2 * 1024 * 1024
    private var session: URLSession?
    private var response: HTTPURLResponse?
    private var received = Data()
    private var finished = false
    private var forcedError: Error?
    private let completion: (Result<[String: Any], Error>) -> Void

    init(completion: @escaping (Result<[String: Any], Error>) -> Void) { self.completion = completion }
    func start(_ request: URLRequest) {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 3
        configuration.timeoutIntervalForResource = 3
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.urlCredentialStorage = nil
        configuration.urlCache = nil
        configuration.connectionProxyDictionary = [:]
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        let queue = OperationQueue(); queue.maxConcurrentOperationCount = 1
        session = URLSession(configuration: configuration, delegate: self, delegateQueue: queue)
        session?.dataTask(with: request).resume()
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        forcedError = NativeBridgeError.unavailable("The game bridge redirected unexpectedly. Restart Minecraft.")
        completionHandler(nil)
    }
    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        guard let http = response as? HTTPURLResponse, response.url?.host == "127.0.0.1",
              response.url?.scheme == "http", response.expectedContentLength <= Int64(Self.maximumResponse) else {
            forcedError = NativeBridgeError.unavailable("The game bridge returned an invalid or oversized response.")
            completionHandler(.cancel); return
        }
        self.response = http; completionHandler(.allow)
    }
    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        guard received.count <= Self.maximumResponse - data.count else {
            forcedError = NativeBridgeError.unavailable("The game bridge response exceeds 2 MB.")
            dataTask.cancel(); return
        }
        received.append(data)
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard !finished else { return }; finished = true
        defer { session.finishTasksAndInvalidate(); self.session = nil }
        if let error = forcedError ?? error { completion(.failure(error)); return }
        guard let response else { completion(.failure(NativeBridgeError.unavailable("The game bridge did not respond."))); return }
        do {
            guard let object = try JSONSerialization.jsonObject(with: received) as? [String: Any] else {
                throw NativeBridgeError.unavailable("The game bridge returned invalid JSON.")
            }
            guard (200...299).contains(response.statusCode) else {
                let message = object["message"] as? String ?? "The game bridge rejected the request (\(response.statusCode))."
                throw NativeBridgeError.unavailable(message)
            }
            completion(.success(object))
        } catch { completion(.failure(error)) }
    }
}

final class GameBridge {
    var minecraftDirectory: URL
    var onState: (([String: Any], String?) -> Void)?
    var onOpenClient: (() -> Void)?
    private var timer: Timer?
    private var backgroundActivity: NSObjectProtocol?
    private var polling = false
    private var commandActive = false
    private var mutationEpoch = 0
    private var generation = 0
    private struct Command {
        let path: String
        let payload: [String: Any]
        let connectionIdentity: String
        let generation: Int
        let completion: (Result<[String: Any], Error>) -> Void
    }
    private var commands: [Command] = []
    private var descriptorIdentity: String?
    private var openSequence: Int64?
    private var observedConnected: Bool?
    private var observedServer: String?
    private var observedWorldSession: Int64?
    private(set) var latestState: [String: Any] = ["connected": false, "transportConnected": false, "connectionGeneration": 0]
    var connectionGeneration: Int { generation }

    init(minecraftDirectory: URL) { self.minecraftDirectory = minecraftDirectory }
    func start() {
        stop()
        backgroundActivity = ProcessInfo.processInfo.beginActivity(options: .userInitiatedAllowingIdleSystemSleep,
            reason: "Respond to the Minecraft client shortcut through localhost while the client is hidden")
        let next = Timer(timeInterval: 0.5, repeats: true) { [weak self] _ in self?.poll() }
        RunLoop.main.add(next, forMode: .common); timer = next; poll()
    }
    func stop() {
        timer?.invalidate(); timer = nil
        if let backgroundActivity { ProcessInfo.processInfo.endActivity(backgroundActivity); self.backgroundActivity = nil }
    }
    func fetchState(completion: @escaping (Result<[String: Any], Error>) -> Void) {
        request(path: "/state", payload: nil) { [weak self] result in
            if case .success(let state) = result, let self { self.adopt(state); completion(.success(self.latestState)) }
            else { completion(result) }
        }
    }
    func directoryChanged(_ directory: URL) {
        generation += 1
        let abandoned = commands; commands.removeAll()
        for command in abandoned { command.completion(.failure(NativeBridgeError.unavailable("The selected Minecraft folder changed."))) }
        minecraftDirectory = directory; descriptorIdentity = nil; openSequence = nil
        observedConnected = nil; observedServer = nil; observedWorldSession = nil
        latestState = ["connected": false, "transportConnected": false, "connectionGeneration": generation]
        poll()
    }
    private func descriptor() throws -> ConnectionDescriptor {
        let candidates = [minecraftDirectory.appendingPathComponent("tun-tun-26.3/config/tun-tun-connection.json"),
                          minecraftDirectory.appendingPathComponent("config/tun-tun-connection.json")]
        for file in candidates where FileManager.default.fileExists(atPath: file.path) {
            let handle = try FileHandle(forReadingFrom: file); defer { try? handle.close() }
            return try ConnectionDescriptor(data: handle.readData(ofLength: 8193))
        }
        throw NativeBridgeError.unavailable("Game bridge offline. Install the Fabric core, then launch Tung Tung Client 26.3 and join a world.")
    }
    private func poll() {
        guard !polling, !commandActive, commands.isEmpty else { return }; polling = true
        let startedGeneration = generation
        let startedMutation = mutationEpoch
        request(path: "/state", payload: nil) { [weak self] result in
            guard let self else { return }; self.polling = false
            guard !self.commandActive, startedMutation == self.mutationEpoch,
                  startedGeneration == self.generation || startedGeneration == 0 else { return }
            switch result {
            case .success(let state): self.adopt(state)
            case .failure(let error):
                self.latestState = ["connected": false, "transportConnected": false, "connectionGeneration": self.generation,
                                    "message": error.localizedDescription]
                self.onState?(self.latestState, error.localizedDescription)
            }
        }
    }
    func send(path: String, payload: [String: Any], expectedGeneration: Int? = nil,
              completion: @escaping (Result<[String: Any], Error>) -> Void) {
        guard commands.count < 64 else { completion(.failure(NativeBridgeError.unavailable("Too many game actions are pending."))); return }
        let identity: String
        do { let connection = try descriptor(); observeDescriptor(connection); identity = connection.identity }
        catch { DispatchQueue.main.async { completion(.failure(error)) }; return }
        guard expectedGeneration == nil || expectedGeneration == generation else {
            completion(.failure(NativeBridgeError.unavailable("The game connection changed. Apply this action again to the current game."))); return
        }
        mutationEpoch += 1
        commands.append(Command(path: path, payload: payload, connectionIdentity: identity, generation: generation, completion: completion)); nextCommand()
    }
    private func nextCommand() {
        guard !commandActive, !commands.isEmpty else { return }
        commandActive = true
        let command = commands.removeFirst()
        guard command.generation == generation else {
            commandActive = false
            command.completion(.failure(NativeBridgeError.unavailable("The queued game action belongs to an earlier connection.")))
            nextCommand(); return
        }
        // Confirm the current game state before applying a queued intent; ordinary polling may have been suspended.
        request(path: "/state", payload: nil, expectedIdentity: command.connectionIdentity) { [weak self] confirmation in
            guard let self else { return }
            guard case .success(let confirmed) = confirmation else {
                self.commandActive = false; command.completion(confirmation); self.nextCommand(); return
            }
            self.adopt(confirmed)
            guard command.generation == self.generation else {
                self.commandActive = false
                command.completion(.failure(NativeBridgeError.unavailable("The game changed before the queued action could run.")))
                self.nextCommand(); return
            }
            self.request(path: command.path, payload: command.payload, expectedIdentity: command.connectionIdentity) { [weak self] result in
                guard let self else { return }; self.commandActive = false
                if case .success(let state) = result { self.adopt(state); command.completion(.success(self.latestState)) }
                else { command.completion(result) }
                self.nextCommand()
            }
        }
    }
    private func request(path: String, payload: [String: Any]?, expectedIdentity: String? = nil,
                         completion: @escaping (Result<[String: Any], Error>) -> Void) {
        do {
            guard ["/state", "/module", "/settings", "/action"].contains(path), (path == "/state") == (payload == nil) else {
                throw NativeBridgeError.unavailable("Unknown game bridge operation.")
            }
            let connection = try descriptor()
            observeDescriptor(connection)
            guard expectedIdentity == nil || expectedIdentity == connection.identity else {
                throw NativeBridgeError.unavailable("Minecraft restarted before the queued action could run. Apply it again to the current game.")
            }
            let requestGeneration = generation
            var request = URLRequest(url: URL(string: "http://127.0.0.1:\(connection.port)\(path)")!, timeoutInterval: 3)
            request.httpMethod = payload == nil ? "GET" : "POST"
            connection.authenticate(&request)
            if let payload {
                var guarded = payload
                if let session = latestState["worldSession"] as? NSNumber {
                    guarded["expectedWorldSession"] = session
                }
                let data = try JSONSerialization.data(withJSONObject: guarded)
                guard data.count <= 65536 else { throw NativeBridgeError.unavailable("The game request exceeds 64 KB.") }
                request.httpBody = data; request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            }
            LimitedRequest { [weak self] result in
                DispatchQueue.main.async {
                    guard let self else { return }
                    if case .success = result {
                        do {
                            let current = try self.descriptor()
                            self.observeDescriptor(current)
                            guard current.identity == connection.identity else {
                                completion(.failure(NativeBridgeError.unavailable("The completed request belongs to an earlier Minecraft instance."))); return
                            }
                        } catch { completion(.failure(error)); return }
                    }
                    guard self.generation == requestGeneration else {
                        completion(.failure(NativeBridgeError.unavailable("The game connection changed while the request was running."))); return
                    }
                    completion(result)
                }
            }.start(request)
        } catch { DispatchQueue.main.async { completion(.failure(error)) } }
    }
    private func adopt(_ incoming: [String: Any]) {
        let connected = incoming["connected"] as? Bool
        let server = incoming["server"] as? String
        let worldSession = (incoming["worldSession"] as? NSNumber)?.int64Value
        if let before = observedConnected, let connected,
           before != connected || (connected && observedServer != nil && server != observedServer)
               || (connected && observedWorldSession != nil && worldSession != observedWorldSession) {
            generation += 1
            let abandoned = commands; commands.removeAll()
            for command in abandoned { command.completion(.failure(NativeBridgeError.unavailable("The current Minecraft connection changed."))) }
        }
        observedConnected = connected; observedServer = server; observedWorldSession = worldSession
        var state = incoming; state["transportConnected"] = true
        state["connectionGeneration"] = generation
        latestState = state
        let sequence = (state["openClientSequence"] as? NSNumber)?.int64Value
        if let sequence {
            if let previous = openSequence, sequence > previous { onOpenClient?() }
            openSequence = sequence
        }
        onState?(state, nil)
    }
    private func observeDescriptor(_ connection: ConnectionDescriptor) {
        guard descriptorIdentity != connection.identity else { return }
        let abandoned = commands; commands.removeAll()
        descriptorIdentity = connection.identity; openSequence = nil; generation += 1
        observedConnected = nil; observedServer = nil; observedWorldSession = nil
        latestState = ["connected": false, "transportConnected": false, "connectionGeneration": generation,
                       "message": "Connecting to the current Minecraft instance…"]
        for command in abandoned { command.completion(.failure(NativeBridgeError.unavailable("Minecraft restarted before the queued action could run."))) }
        onState?(latestState, nil)
    }
    static func contractChecks() -> [String: Bool] {
        let valid = Data("{\"port\":25565,\"token\":\"\(String(repeating: "a", count: 64))\"}".utf8)
        let invalid = ["{\"port\":0,\"token\":\"bad\"}", "{\"port\":65536,\"token\":\"\(String(repeating: "a", count: 64))\"}",
                       "{\"port\":true,\"token\":\"\(String(repeating: "a", count: 64))\"}",
                       "{\"port\":1.5,\"token\":\"\(String(repeating: "a", count: 64))\"}",
                       "{\"port\":25565,\"token\":\"not-a-token\"}"]
        return ["validDescriptor": (try? ConnectionDescriptor(data: valid)) != nil,
                "invalidDescriptorsRejected": invalid.allSatisfy { (try? ConnectionDescriptor(data: Data($0.utf8))) == nil },
                "oversizedDescriptorRejected": (try? ConnectionDescriptor(data: Data(repeating: 32, count: 8193))) == nil,
                "loopbackHostPinned": URL(string: "http://127.0.0.1:25565/state")?.host == "127.0.0.1"]
    }
}
