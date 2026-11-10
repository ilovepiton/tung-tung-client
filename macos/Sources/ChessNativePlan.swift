import Foundation
import CoreGraphics

/// A post-game plan over whitelisted accessibility labels. Never reads or selects squares or moves.
final class ChessNativePlan {
    struct Node {
        let index: Int
        let role: String
        let label: String
        let identifier: String
        let selected: Bool
        let rect: CGRect
    }
    struct Snapshot { let nodes: [Node]; let window: CGRect; let focused: Bool }
    struct Decision { let phase: String; let code: String; let seconds: Int; let press: Int?; let submits: Bool; let scroll: Bool }
    private var sawOwnGame = false, pending = false, submitted: TimeInterval?, deadline: TimeInterval?
    private var finishedHandled = false, revision = -1, actionAt: TimeInterval = -100
    private var applied: (Int, Int)?
    private var navigationAttempts = 0
    private var failed = ""
    private var selectingAt: TimeInterval?
    static func normalize(_ value: String) -> String {
        value.lowercased().replacingOccurrences(of: "ё", with: "е").split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
    }
    static func clock(_ value: String) -> (Int, Int)? {
        let value = normalize(value)
        let words = ["один":1,"одна":1,"два":2,"две":2,"три":3,"пять":5,"десять":10,"пятнадцать":15,"двадцать":20,"тридцать":30,"шестьдесят":60,"one":1,"two":2,"three":3,"five":5,"ten":10,"fifteen":15,"twenty":20,"thirty":30,"sixty":60]
        func number(_ part: Substring) -> Int? { Int(part) ?? words[String(part)] }
        let tokens = value.replacingOccurrences(of: "+", with: " плюс ").replacingOccurrences(of: "|", with: " плюс ").split(separator: " ")
        guard let first = tokens.first, let n = number(first), (1...180).contains(n) else { return nil }
        if tokens.count == 3 && tokens[1] == "плюс", let inc = number(tokens[2]) { return (n, inc) }
        guard tokens.count >= 2 else { return nil }
        let unit = String(tokens[1]); var minutes = n
        if unit.hasPrefix("час") || unit.hasPrefix("hour") { minutes *= 60 }
        else if !unit.hasPrefix("мин") && !unit.hasPrefix("min") { return nil }
        guard minutes <= 180 else { return nil }
        if let plus = tokens.firstIndex(where: { $0 == "плюс" || $0 == "plus" }), plus + 1 < tokens.count, let inc = number(tokens[plus+1]), (0...180).contains(inc) { return (minutes, inc) }
        return (minutes, 0)
    }
    private func matches(_ node: Node, _ names: [String]) -> Bool { names.contains(Self.normalize(node.label)) }
    private func isButton(_ node: Node) -> Bool { node.role == "AXButton" || node.role == "AXPopUpButton" }
    private func result(_ node: Node) -> Bool {
        guard node.role == "AXStaticText" || node.role == "AXHeading" else { return false }
        let text = Self.normalize(node.label)
        return ["вы выиграли", "вы проиграли", "вы победили", "победа", "поражение", "ничья", "партия прервана", "белые выиграли", "черные выиграли", "you won", "you lost", "draw", "game aborted", "white won", "black won"].contains { text == $0 || text.hasPrefix($0 + "!") || text.hasPrefix($0 + ",") || text.hasPrefix($0 + " ") }
    }
    private func review(_ s: Snapshot) -> Bool { s.nodes.contains { ($0.identifier + " " + $0.label).localizedCaseInsensitiveContains("GameReview") } }
    private func watch(_ s: Snapshot) -> Bool { s.nodes.contains { $0.selected && matches($0, ["смотреть", "watch"]) } }
    private func home(_ s: Snapshot) -> Bool {
        s.nodes.contains { ($0.identifier + " " + $0.label).contains("HomeScreenHosting") }
            && s.nodes.contains { isButton($0) && matches($0, ["начать партию", "start game", "play"]) }
    }
    private func picker(_ s: Snapshot) -> Bool { s.nodes.contains { matches($0, ["выбрать время", "choose time", "choose time control", "time control"]) } }
    private func newGame(_ s: Snapshot) -> Node? { s.nodes.first { isButton($0) && matches($0, ["новая партия", "новая игра", "играть снова", "new game", "play again"]) } }
    private func finish(_ s: Snapshot) -> Bool {
        let personal = s.nodes.contains { node in
            guard result(node) else { return false }; let t = Self.normalize(node.label)
            return ["вы ", "you ", "победа", "поражение"].contains { t.hasPrefix($0) }
        }
        let ownTab = s.nodes.contains { $0.selected && matches($0, ["главная", "home"]) }
        let neutral = sawOwnGame && s.nodes.contains { n in result(n) && ["ничья", "draw", "партия прервана", "game aborted"].contains { Self.normalize(n.label).hasPrefix($0) } }
        return !review(s) && !watch(s) && newGame(s) != nil && s.nodes.contains(where: result) && (personal || neutral || sawOwnGame && ownTab)
    }
    private func playing(_ s: Snapshot) -> Bool {
        let ownTab = s.nodes.contains { $0.selected && matches($0, ["главная", "home"]) }
        let clocks = s.nodes.filter { $0.role == "AXStaticText" && $0.label.range(of: #"^\d{1,2}:\d{2}$"#, options: .regularExpression) != nil }.count
        let quality = s.nodes.contains { matches($0, ["индикатор качества связи", "connection quality indicator"]) }
        return ownTab && clocks >= 2 && quality && !review(s) && !home(s) && !picker(s) && !finish(s)
    }
    private func blocked(_ s: Snapshot) -> Bool {
        s.nodes.contains { n in (n.identifier + " " + n.label).localizedCaseInsensitiveContains("Paywall")
            || isButton(n) && ["попробуй за", "start free trial", "try for", "оформить подписку"].contains(where: { Self.normalize(n.label).hasPrefix($0) })
        }
    }
    func classify(_ s: Snapshot) -> String {
        if review(s) || watch(s) || blocked(s) { return "unsupported" }
        if picker(s) { return "selecting" }
        if home(s) { return "lobby" }
        if finish(s) { return "finished" }
        if playing(s) { return "playing" }
        return "unsupported"
    }
    func actionFailed(submits: Bool) { failed = submits ? "start-unavailable" : "time-unavailable" }
    func step(_ s: Snapshot, enabled: Bool, allowed: Bool, minutes: Int, increment: Int, delay: Int, revision nextRevision: Int, now: TimeInterval) -> Decision {
        func state(_ phase: String, _ code: String = "", _ seconds: Int = 0) -> Decision { Decision(phase: phase, code: code, seconds: seconds, press: nil, submits: false, scroll: false) }
        func press(_ node: Node, _ phase: String, submits: Bool = false) -> Decision { actionAt = now; if selectingAt == nil && !submits { selectingAt = now }; return Decision(phase: phase, code: "", seconds: 0, press: node.index, submits: submits, scroll: !s.window.insetBy(dx: 4, dy: 4).contains(CGPoint(x: node.rect.midX, y: node.rect.midY))) }
        let phase = classify(s)
        if phase == "playing" { sawOwnGame = true; selectingAt = nil; navigationAttempts = 0; pending = false; submitted = nil; deadline = nil; finishedHandled = false; failed = ""; return state("playing") }
        if phase == "lobby", let current = s.nodes.first(where: { isButton($0) && Self.clock($0.label) != nil }), let clock = Self.clock(current.label) { applied = clock }
        if review(s) || watch(s) || blocked(s) { pending = false; deadline = nil; finishedHandled = true; sawOwnGame = false; return state("unsupported") }
        if !enabled { pending = false; submitted = nil; deadline = nil; failed = ""; return state(phase) }
        if !allowed || !s.focused { deadline = nil; return state("paused") }
        if nextRevision != revision { revision = nextRevision; deadline = nil; selectingAt = nil; failed = "" }
        if let at = selectingAt, now - at > 20 { return state("error", "select-timeout") }
        if !failed.isEmpty { return state("error", failed) }
        if let at = submitted { return now - at > 12 ? state("error", "start-unavailable") : state("searching") }
        let supported = [(1,0),(1,1),(2,1),(3,0),(3,2),(5,0),(5,2),(5,5),(10,0),(10,5),(15,10),(20,0),(30,0),(60,0)]
        guard supported.contains(where: { $0.0 == minutes && $0.1 == increment }) else { return state("error", "time-unavailable") }
        if phase == "finished" && !finishedHandled {
            if deadline == nil { deadline = now + Double(delay) }
            if now < deadline! { return state("countdown", "", Int(ceil(deadline! - now))) }
            pending = true
            if let applied, applied.0 == minutes, applied.1 == increment, let button = newGame(s) {
                submitted = now; finishedHandled = true; return press(button, "searching", submits: true)
            }
        }
        guard now - actionAt >= 0.65 else { return state(pending ? "selecting" : phase) }
        if picker(s) {
            if let option = s.nodes.first(where: { node in
                guard let t = Self.clock(node.label) else { return false }; return t.0 == minutes && t.1 == increment && (node.role == "AXStaticText" || isButton(node))
            }) { return press(option, "selecting") }
            if let more = s.nodes.first(where: { matches($0, ["другие контроли времени", "more time controls"]) }) { return press(more, "selecting") }
            return state("error", "time-unavailable")
        }
        if phase == "lobby" {
            if let current = s.nodes.first(where: { isButton($0) && Self.clock($0.label) != nil }), let clock = Self.clock(current.label) {
                if clock.0 != minutes || clock.1 != increment { return press(current, "selecting") }
                if pending, let start = s.nodes.first(where: { isButton($0) && matches($0, ["начать партию", "start game", "play"]) }) {
                    submitted = now; finishedHandled = true; return press(start, "searching", submits: true)
                }
            }
            selectingAt = nil; return state("lobby")
        }
        if pending && navigationAttempts < 3 {
            if let close = s.nodes.first(where: { isButton($0) && matches($0, ["generic-close", "закрыть", "close"]) }) { navigationAttempts += 1; return press(close, "selecting") }
            if let back = s.nodes.first(where: { isButton($0) && matches($0, ["назад", "back", "главная", "home"]) && $0.rect.minY < s.window.minY + 120 && $0.rect.minX < s.window.minX + 160 }) { navigationAttempts += 1; return press(back, "selecting") }
            if let tab = s.nodes.first(where: { $0.role == "AXRadioButton" && matches($0, ["главная", "home"]) }) { navigationAttempts += 1; return press(tab, "selecting") }
        }
        return state(pending ? "error" : phase, pending ? "time-unavailable" : "")
    }
}
