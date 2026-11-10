import Foundation

@main struct TransportChecks {
    static var checks: [String] = []
    static func check(_ condition: Bool, _ label: String) throws {
        guard condition else { throw NSError(domain: "ChessTransportChecks", code: 1, userInfo: [NSLocalizedDescriptionKey: label]) };checks.append(label)
    }
    static func request(_ path: String, _ object: Any = [:], key: String? = nil, origin: String? = nil) throws -> (Int, [String: Any], HTTPURLResponse) {
        let url = URL(string: "http://127.0.0.1:\(ChessBridge.port)" + path)!
        var request = URLRequest(url: url);request.httpMethod = "POST";request.timeoutInterval = 3
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let key { request.setValue(key, forHTTPHeaderField: "X-Tung-Chess-Key") }
        if let origin { request.setValue(origin, forHTTPHeaderField: "Origin") }
        request.httpBody = try JSONSerialization.data(withJSONObject: object)
        let lock = NSLock();var done = false;var result: (Data?, URLResponse?, Error?)?
        let config = URLSessionConfiguration.ephemeral;config.connectionProxyDictionary = [:]
        let session = URLSession(configuration: config)
        session.dataTask(with: request) { data, response, error in lock.lock();result = (data, response, error);done = true;lock.unlock() }.resume()
        let until = Date().addingTimeInterval(4)
        while Date() < until { lock.lock();let complete = done;lock.unlock();if complete { break };RunLoop.current.run(until: Date().addingTimeInterval(0.01)) }
        session.finishTasksAndInvalidate()
        lock.lock();let response = result;lock.unlock()
        guard let response else { throw NSError(domain: "ChessTransportChecks", code: 2) }
        if let error = response.2 { throw error }
        guard let http = response.1 as? HTTPURLResponse, let data = response.0 else { throw NSError(domain: "ChessTransportChecks", code: 3) }
        return (http.statusCode, (try JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:], http)
    }
    static func main() throws {
        let bridge = ChessBridge(defaults: nil);bridge.start();defer { bridge.stop() }
        let deadline = Date().addingTimeInterval(4)
        while bridge.pairingURL == nil && Date() < deadline { RunLoop.current.run(until: Date().addingTimeInterval(0.01)) }
        guard let fragment = bridge.pairingURL?.fragment, fragment.hasPrefix("pair=") else { throw NSError(domain: "ChessTransportChecks", code: 4) }
        let key = String(fragment.dropFirst(5)),origin = "chrome-extension://" + ChessBridge.extensionID
        try check(key.count == 64, "The private pairing key is created without saving user preferences")
        try check(try request("/chess/settings").0 == 403, "Unauthenticated settings requests are rejected")
        try check(try request("/chess/settings", key: String(repeating: "0", count: 64)).0 == 403, "An incorrect pairing key is rejected")
        try check(try request("/chess/settings", key: key, origin: "https://www.chess.com").0 == 403, "A web page cannot call the extension-only transport")
        let settings = try request("/chess/settings", key: key, origin: origin)
        try check(settings.0 == 200 && settings.1["available"] as? Bool == true, "The paired extension reads actual native settings")
        try check(settings.2.value(forHTTPHeaderField: "Access-Control-Allow-Origin") == origin, "CORS is limited to the pinned extension origin")
        let id = "chrome-test-board0001"
        let data = try request("/chess/poll", ["clientId": id, "phase": "playing"], key: key, origin: origin)
        try check(data.0 == 200 && data.1["allowed"] as? Bool == true, "A genuine authenticated poll receives one board lease")
        let other = try request("/chess/poll", ["clientId": "chrome-other-board0002", "phase": "playing"], key: key, origin: origin)
        try check(other.1["allowed"] as? Bool == false, "A second board does not receive simultaneous queue permission")
        try check(try request("/chess/poll", ["clientId": "broken/id", "phase": "playing"], key: key).0 == 400, "Invalid board reports are rejected")
        try check(try request("/chess/poll", ["clientId": id, "phase": "invented"], key: key).0 == 400, "Unknown phases cannot enter native state")
        _ = try bridge.update(["enabled": true, "minutes": 10])
        let changed = try request("/chess/poll", ["clientId": id, "phase": "countdown", "secondsLeft": 3], key: key)
        try check(changed.1["enabled"] as? Bool == true && changed.1["minutes"] as? Int == 10, "Desktop clock changes reach a real HTTP poll")
        try check(try request("/chess/stop", ["clientId": "chrome-other-board0002"], key: key).0 == 400, "A different board cannot pause the selected board")
        try check(try request("/chess/stop", ["clientId": id], key: key).0 == 200 && !bridge.session.enabled, "The selected board can stop future games immediately")
        let report: [String: Any] = ["ok": true, "checks": checks.count, "details": checks, "liveGamesStarted": false, "userProfileTouched": false]
        let output = URL(fileURLWithPath: "artifacts/chess-auto-next/transport-checks.json")
        let dataOut = try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]);try dataOut.write(to: output)
        print(String(data: dataOut, encoding: .utf8)!)
    }
}
