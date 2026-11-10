import Foundation
import CoreFoundation

/// Settings and a single-board lease, independent of Minecraft and account data.
final class ChessSession {
    static let phases: Set<String> = ["lobby", "playing", "finished", "countdown", "selecting", "searching", "paused", "unsupported", "error"]
    private(set) var enabled = false
    private(set) var minutes = 3
    private(set) var increment = 0
    private(set) var delay = 3
    private(set) var revision = 0
    private(set) var starts = 0
    private var owner = ""
    private var clients: [String: Client] = [:]
    private let now: () -> Date
    var onSave: (([String: Any]) -> Void)?
    private struct Client {
        var at: Date
        var channel: String
        var phase: String
        var seconds: Int
        var code: String
    }
    init(saved: [String: Any] = [:], now: @escaping () -> Date = Date.init) {
        self.now = now
        enabled = saved["enabled"] as? Bool ?? false
        minutes = Self.integer(saved["minutes"], in: 1...180) ?? 3
        increment = Self.integer(saved["increment"], in: 0...180) ?? 0
        delay = Self.integer(saved["delaySeconds"], in: 1...15) ?? 3
    }
    static func integer(_ value: Any?, in range: ClosedRange<Int>) -> Int? {
        guard let n = value as? NSNumber, CFGetTypeID(n) != CFBooleanGetTypeID(), n.doubleValue.isFinite,
              n.doubleValue.rounded() == n.doubleValue, n.doubleValue >= Double(range.lowerBound),
              n.doubleValue <= Double(range.upperBound) else { return nil }
        return n.intValue
    }
    private func error(_ text: String) -> Error { NSError(domain: "TungChess", code: 1, userInfo: [NSLocalizedDescriptionKey: text]) }
    var settings: [String: Any] {
        ["enabled": enabled, "minutes": minutes, "increment": increment, "delaySeconds": delay, "revision": revision]
    }
    func update(_ patch: [String: Any]) throws {
        guard !patch.isEmpty, Set(patch.keys).isSubset(of: ["enabled", "minutes", "increment", "delaySeconds", "revision"]) else { throw error("Choose an Auto Next Game setting.") }
        if let raw = patch["revision"] {
            guard Self.integer(raw, in: 0...Int.max) == revision else { throw error("Chess settings changed. Try your selection again.") }
        }
        var nextEnabled = enabled, nextMinutes = minutes, nextIncrement = increment, nextDelay = delay
        for (key, value) in patch {
            switch key {
            case "enabled":
                guard let n = value as? NSNumber, CFGetTypeID(n) == CFBooleanGetTypeID() else { throw error("Choose On or Off.") }
                nextEnabled = n.boolValue
            case "minutes": guard let n = Self.integer(value, in: 1...180) else { throw error("Choose 1–180 minutes.") }; nextMinutes = n
            case "increment": guard let n = Self.integer(value, in: 0...180) else { throw error("Choose 0–180 increment seconds.") }; nextIncrement = n
            case "delaySeconds": guard let n = Self.integer(value, in: 1...15) else { throw error("Choose a pause of 1–15 seconds.") }; nextDelay = n
            default: break
            }
        }
        enabled = nextEnabled; minutes = nextMinutes; increment = nextIncrement; delay = nextDelay
        revision += 1; onSave?(settings)
    }
    func expire() {
        clients = clients.filter { now().timeIntervalSince($0.value.at) < 8 }
        if clients[owner] == nil { owner = "" }
    }
    func poll(_ object: [String: Any], channel: String) throws -> [String: Any] {
        expire()
        guard let id = object["clientId"] as? String, (8...110).contains(id.count),
              id.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-") }),
              let phase = object["phase"] as? String, Self.phases.contains(phase),
              ["Client window", "Chrome", "Chess app"].contains(channel), clients[id] != nil || clients.count < 32 else { throw error("Invalid Chess connection.") }
        let seconds = Self.integer(object["secondsLeft"], in: 0...15) ?? 0
        let codes = ["", "time-unavailable", "start-unavailable", "expired", "connection-lost", "select-timeout", "storage-unavailable"]
        let code = codes.contains(object["code"] as? String ?? "") ? object["code"] as? String ?? "" : ""
        if owner == id && clients[id]?.phase != "searching" && phase == "searching" { starts += 1 }
        clients[id] = Client(at: now(), channel: channel, phase: phase, seconds: seconds, code: code)
        let provisional = ["lobby", "unsupported"].contains(clients[owner]?.phase ?? "")
        if object["claim"] as? Bool == true || owner.isEmpty && phase != "unsupported"
            || provisional && ["playing", "finished"].contains(phase) { owner = id }
        var result = settings
        result["allowed"] = owner == id
        result["binding"] = id
        return result
    }
    func stop(clientId: String) throws {
        expire(); guard clientId == owner else { throw error("This board is not following Auto Next Game.") }
        try update(["enabled": false])
    }
    func close(clientId: String) {
        clients.removeValue(forKey: clientId)
        if owner == clientId { owner = ""; if enabled { try? update(["enabled": false]) } }
    }
    var state: [String: Any] {
        expire()
        var result = settings
        let current = clients[owner]
        result["available"] = true; result["connected"] = current != nil
        result["client"] = current?.channel ?? "No board connected"
        result["phase"] = current?.phase ?? "disconnected"
        result["secondsLeft"] = current?.seconds ?? 0
        result["code"] = current?.code ?? ""
        result["starts"] = starts; result["boards"] = clients.count
        return result
    }
}
