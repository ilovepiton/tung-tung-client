import Foundation
import CoreFoundation

@main struct SessionChecks {
    static var checks: [String] = []
    static func check(_ value: Bool, _ label: String) throws {
        guard value else { throw NSError(domain: "ChessChecks", code: 1, userInfo: [NSLocalizedDescriptionKey: label]) }
        checks.append(label)
    }
    static func poll(_ session: ChessSession, _ id: String, _ phase: String = "playing", _ claim: Bool = false) throws -> [String: Any] {
        try session.poll(["clientId": id, "phase": phase, "claim": claim, "secondsLeft": 3], channel: "Chrome")
    }
    static func main() throws {
        var now = Date(timeIntervalSince1970: 100)
        let s = ChessSession(now: { now })
        try check(!s.enabled && s.minutes == 3 && s.delay == 3, "Fresh settings start off at three minutes")
        try s.update(["enabled": true, "minutes": 5, "increment": 5, "delaySeconds": 1])
        try check(s.enabled && s.minutes == 5 && s.increment == 5 && s.delay == 1, "Atomic settings include clock and post-game pause")
        for bad: [String: Any] in [["minutes": true], ["minutes": 0], ["minutes": 181], ["minutes": 1.5], ["increment": -1], ["increment": 181], ["enabled": "true"], ["delaySeconds": 0], ["unexpected": true]] {
            let revision = s.revision
            do { try s.update(bad); throw NSError(domain: "ChessChecks", code: 2) }
            catch let e as NSError { try check(e.domain != "ChessChecks" && s.revision == revision, "Invalid setting rejected atomically: \(bad.keys.sorted())") }
        }
        do { try s.update(["revision": 0, "minutes": 10]); throw NSError(domain: "ChessChecks", code: 2) }
        catch let e as NSError { try check(e.domain != "ChessChecks" && s.minutes == 5, "Stale settings cannot overwrite a newer selection") }
        let lobby = try poll(s, "chrome-lobby-0001", "lobby")
        try check(lobby["allowed"] as? Bool == true, "The first board can connect without a Minecraft session")
        let active = try poll(s, "chrome-playing-0002")
        try check(active["allowed"] as? Bool == true, "An actual game takes priority over an idle lobby")
        let other = try poll(s, "chrome-other-0003")
        try check(other["allowed"] as? Bool == false, "Only one playing board receives the automatic queue lease")
        _ = try poll(s, "chrome-other-0003", "searching")
        try check(s.starts == 0, "An unleased board cannot increment automatic starts")
        _ = try poll(s, "chrome-playing-0002", "searching")
        _ = try poll(s, "chrome-playing-0002", "searching")
        try check(s.starts == 1, "Repeated heartbeats count one queue request")
        let claimed = try poll(s, "chrome-other-0003", "playing", true)
        try check(claimed["allowed"] as? Bool == true, "Use This Board explicitly transfers the lease")
        do { try s.stop(clientId: "chrome-playing-0002"); throw NSError(domain: "ChessChecks", code: 2) }
        catch let e as NSError { try check(e.domain != "ChessChecks" && s.enabled, "An inactive board cannot pause the selected board") }
        s.close(clientId: "chrome-playing-0002")
        try check(s.enabled, "Closing an unrelated board preserves the selected session")
        s.close(clientId: "chrome-other-0003")
        try check(!s.enabled, "Closing the owned client board stops future automatic games")
        try s.update(["enabled": true]);_ = try poll(s, "chrome-playing-0004")
        now = now.addingTimeInterval(9);s.expire()
        try check(s.state["connected"] as? Bool == false, "Lost connections expire")
        try check(try poll(s, "chrome-playing-0005")["allowed"] as? Bool == true, "A fresh board can connect after expiry")
        let r = ChessSession(saved: ["minutes": 7, "increment": 12, "delaySeconds": 5, "enabled": true])
        try check(r.minutes == 7 && r.increment == 12 && r.enabled, "Custom clocks persist independently of game configuration")
        do { _ = try r.poll(["clientId": "bad/path", "phase": "playing"], channel: "Chrome"); throw NSError(domain: "ChessChecks", code: 2) }
        catch let e as NSError { try check(e.domain != "ChessChecks", "Malformed connection identifiers are rejected") }
        let report: [String: Any] = ["ok": true, "checks": checks.count, "details": checks, "liveGamesStarted": false, "userProfileTouched": false]
        let data = try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys])
        let out = URL(fileURLWithPath: "artifacts/chess-auto-next/session-checks.json")
        try FileManager.default.createDirectory(at: out.deletingLastPathComponent(), withIntermediateDirectories: true)
        try data.write(to: out); print(String(data: data, encoding: .utf8)!)
    }
}
