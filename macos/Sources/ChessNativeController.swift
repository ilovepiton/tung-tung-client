import AppKit
import ApplicationServices

/// Explicitly connected iPhone Chess.com app; only visible post-game and time-control controls.
final class ChessNativeController {
    private let bridge: ChessBridge
    private let defaults: UserDefaults?
    private let queue = DispatchQueue(label: "dev.tungtung.chess.native", qos: .userInitiated)
    private var timer: Timer?, busy = false, selected = false, claim = false
    private var pid: pid_t = 0
    private var plan = ChessNativePlan()
    private var lastPhase = "lobby", lastCode = "", secondsLeft = 0
    var onState: (() -> Void)?
    init(bridge: ChessBridge, defaults: UserDefaults?) {
        self.bridge = bridge; self.defaults = defaults
        selected = defaults?.bool(forKey: "chessNativeConnected") ?? false
    }
    private var application: NSRunningApplication? {
        if let front = NSWorkspace.shared.frontmostApplication, front.bundleIdentifier == "com.chess.iphone" { return front }
        let candidates = NSRunningApplication.runningApplications(withBundleIdentifier: "com.chess.iphone").filter { !$0.isTerminated }
        return candidates.count == 1 ? candidates[0] : nil
    }
    var state: [String: Any] {
        ["nativeRequested": selected, "nativeTrusted": AXIsProcessTrusted(), "nativeRunning": application != nil,
         "nativePhase": lastPhase, "nativeCode": lastCode]
    }
    func connect() {
        selected = true; claim = true; defaults?.set(true, forKey: "chessNativeConnected")
        if !AXIsProcessTrusted() {
            let key = kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String
            _ = AXIsProcessTrustedWithOptions([key: true] as CFDictionary)
            NSWorkspace.shared.open(URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility")!)
        } else if let application { application.activate(options: [.activateIgnoringOtherApps]) }
        onState?()
    }
    func start() {
        guard timer == nil else { return }
        let next = Timer(timeInterval: 0.8, repeats: true) { [weak self] _ in self?.tick() }
        RunLoop.main.add(next, forMode: .common); timer = next
    }
    func stop() { timer?.invalidate(); timer = nil; if pid > 0 { bridge.session.close(clientId: "native-app-\(pid)") } }
    private func tick() {
        guard selected, !busy, AXIsProcessTrusted(), let app = application else { return }
        if pid != app.processIdentifier { pid = app.processIdentifier; plan = ChessNativePlan(); claim = true }
        let currentPid = pid
        let focused = NSWorkspace.shared.frontmostApplication?.processIdentifier == currentPid
        if !focused {
            if bridge.session.state["client"] as? String == "Chess app", let response = try? bridge.session.poll(["clientId": "native-app-\(pid)", "phase": "paused"], channel: "Chess app"), response["allowed"] as? Bool == true {
                lastPhase = "paused"; bridge.onState?()
            }
            return
        }
        busy = true
        queue.async { [weak self] in
            let read = Self.read(pid: currentPid)
            DispatchQueue.main.async {
                guard let self else { return }
                guard self.pid == currentPid, NSWorkspace.shared.frontmostApplication?.processIdentifier == currentPid else { self.busy = false; return }
                let phase = self.plan.classify(read.snapshot)
                let reported = phase == "playing" ? phase : ["countdown", "selecting", "searching", "error"].contains(self.lastPhase) ? self.lastPhase : phase
                let shouldClaim = self.claim && phase != "unsupported"
                guard let response = try? self.bridge.session.poll(["clientId": "native-app-\(currentPid)", "phase": reported,
                    "code": self.lastCode, "secondsLeft": self.secondsLeft, "claim": shouldClaim], channel: "Chess app") else { self.busy = false; return }
                if shouldClaim { self.claim = false }
                let s = self.bridge.session
                let decision = self.plan.step(read.snapshot, enabled: s.enabled, allowed: response["allowed"] as? Bool == true,
                    minutes: s.minutes, increment: s.increment, delay: s.delay, revision: s.revision, now: ProcessInfo.processInfo.systemUptime)
                self.lastPhase = decision.phase; self.lastCode = decision.code; self.secondsLeft = decision.seconds
                self.bridge.onState?()
                guard let index = decision.press, read.references.indices.contains(index) else { self.busy = false; return }
                let target = read.references[index], expected = read.snapshot.nodes[index].label
                self.queue.async {
                    let success = Self.perform(target, pid: currentPid, label: expected, scroll: decision.scroll, window: read.snapshot.window)
                    DispatchQueue.main.async {
                        if !success { self.plan.actionFailed(submits: decision.submits) }
                        self.busy = false
                    }
                }
            }
        }
    }
    private struct Read { let snapshot: ChessNativePlan.Snapshot; let references: [AXUIElement] }
    private static func attribute(_ element: AXUIElement, _ name: CFString) -> CFTypeRef? {
        var result: CFTypeRef?; return AXUIElementCopyAttributeValue(element, name, &result) == .success ? result : nil
    }
    private static func label(_ element: AXUIElement) -> String {
        for name in [kAXTitleAttribute, kAXDescriptionAttribute, kAXValueAttribute] {
            if let value = attribute(element, name as CFString) as? String, !value.isEmpty { return value }
        }
        return ""
    }
    private static func safeIdentifier(_ value: String) -> String {
        ["HomeScreenHosting", "GameReview", "Paywall"].contains(where: { value.contains($0) }) ? value : ""
    }
    private static func safeLabel(_ value: String) -> String {
        let label = ChessNativePlan.normalize(value)
        if ChessNativePlan.clock(value) != nil || value.range(of: #"^\d{1,2}:\d{2}$"#, options: .regularExpression) != nil { return value }
        if !safeIdentifier(value).isEmpty { return value }
        let exact = ["главная","home","смотреть","watch","начать партию","start game","play","новая партия","новая игра","играть снова","new game","play again","выбрать время","choose time","choose time control","time control","другие контроли времени","more time controls","generic-close","закрыть","close","назад","back","индикатор качества связи","connection quality indicator"]
        let prefixes = ["вы выиграли","вы проиграли","вы победили","победа","поражение","ничья","партия прервана","белые выиграли","черные выиграли","you won","you lost","draw","game aborted","white won","black won","попробуй за","start free trial","try for","оформить подписку"]
        return exact.contains(label) || prefixes.contains(where: { label.hasPrefix($0) }) ? value : ""
    }
    private static func rect(_ element: AXUIElement) -> CGRect {
        var point = CGPoint.zero, size = CGSize.zero
        if let raw = attribute(element, kAXPositionAttribute as CFString), CFGetTypeID(raw) == AXValueGetTypeID() { _ = AXValueGetValue(unsafeBitCast(raw, to: AXValue.self), .cgPoint, &point) }
        if let raw = attribute(element, kAXSizeAttribute as CFString), CFGetTypeID(raw) == AXValueGetTypeID() { _ = AXValueGetValue(unsafeBitCast(raw, to: AXValue.self), .cgSize, &size) }
        return CGRect(origin: point, size: size)
    }
    private static func read(pid: pid_t) -> Read {
        let app = AXUIElementCreateApplication(pid); AXUIElementSetMessagingTimeout(app, 0.25)
        var nodes: [ChessNativePlan.Node] = [], references: [AXUIElement] = []
        guard let raw = attribute(app, kAXFocusedWindowAttribute as CFString), CFGetTypeID(raw) == AXUIElementGetTypeID() else {
            return Read(snapshot: .init(nodes: [], window: .zero, focused: true), references: [])
        }
        let window = unsafeBitCast(raw, to: AXUIElement.self), windowRect = rect(window)
        var work: [(AXUIElement, Int)] = [(window, 0)]
        while let (element, depth) = work.popLast(), references.count < 1500 {
            guard depth < 24, attribute(element, "AXHidden" as CFString) as? Bool != true else { continue }
            let role = attribute(element, kAXRoleAttribute as CFString) as? String ?? ""
            let selected = (attribute(element, kAXSelectedAttribute as CFString) as? Bool) == true
                || (attribute(element, kAXValueAttribute as CFString) as? NSNumber)?.intValue == 1
            let identifier = attribute(element, kAXIdentifierAttribute as CFString) as? String ?? ""
            nodes.append(.init(index: references.count, role: role, label: safeLabel(label(element)), identifier: safeIdentifier(identifier), selected: selected, rect: rect(element)))
            references.append(element)
            if let children = attribute(element, kAXChildrenAttribute as CFString) as? [AXUIElement] { work.append(contentsOf: children.reversed().map { ($0, depth + 1) }) }
        }
        return Read(snapshot: .init(nodes: nodes, window: windowRect, focused: true), references: references)
    }
    private static func perform(_ element: AXUIElement, pid: pid_t, label expected: String, scroll: Bool, window: CGRect) -> Bool {
        guard NSWorkspace.shared.frontmostApplication?.processIdentifier == pid, label(element) == expected else { return false }
        let box = rect(element)
        if scroll {
            if AXUIElementPerformAction(element, "AXScrollToVisible" as CFString) == .success { return true }
            guard let event = CGEvent(scrollWheelEvent2Source: nil, units: .pixel, wheelCount: 1, wheel1: box.midY > window.midY ? -350 : 350, wheel2: 0, wheel3: 0) else { return false }
            event.location = CGPoint(x: window.midX, y: window.midY); event.postToPid(pid); return true
        }
        guard box.width > 0, box.height > 0 else { return AXUIElementPerformAction(element, kAXPressAction as CFString) == .success }
        let point = CGPoint(x: box.midX, y: box.midY), system = AXUIElementCreateSystemWide(); var hit: AXUIElement?
        guard AXUIElementCopyElementAtPosition(system, Float(point.x), Float(point.y), &hit) == .success, var current = hit else { return false }
        var targetPid: pid_t = 0; _ = AXUIElementGetPid(current, &targetPid); guard targetPid == pid else { return false }
        var matches = CFEqual(current, element)
        for _ in 0..<5 where !matches {
            guard let parent = attribute(current, kAXParentAttribute as CFString), CFGetTypeID(parent) == AXUIElementGetTypeID() else { break }
            current = unsafeBitCast(parent, to: AXUIElement.self); matches = CFEqual(current, element)
        }
        guard matches else { return false }
        if AXUIElementPerformAction(element, kAXPressAction as CFString) == .success { return true }
        guard let down = CGEvent(mouseEventSource: nil, mouseType: .leftMouseDown, mouseCursorPosition: point, mouseButton: .left),
              let up = CGEvent(mouseEventSource: nil, mouseType: .leftMouseUp, mouseCursorPosition: point, mouseButton: .left) else { return false }
        down.postToPid(pid); up.postToPid(pid); return true
    }
}
