import AppKit
import WebKit
import UniformTypeIdentifiers
import Foundation
import Darwin

private final class WeakMessageHandler: NSObject, WKScriptMessageHandler {
    weak var owner: MacApplication?
    init(_ owner: MacApplication) { self.owner = owner }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        owner?.handle(message)
    }
}

private final class ClientWindow: NSWindow {
    // Escape belongs to focused content; it never closes or hides the desktop app.
    override func cancelOperation(_ sender: Any?) {}
}

final class MacApplication: NSObject, NSApplicationDelegate, NSWindowDelegate, WKNavigationDelegate, WKUIDelegate {
    private let resources = Bundle.main.resourceURL!
    private let defaults = UserDefaults.standard
    private var window: NSWindow!
    private var web: WKWebView!
    private var status: NSStatusItem?
    private var bridge: GameBridge!
    private let robloxBridge = RobloxBridge()
    private var chessBridge: ChessBridge!
    private var chessWindow: ChessWindow!
    private var nativeChess: ChessNativeController!
    private var previousApplication: NSRunningApplication?
    private var ready = false
    private var installing = false
    private var installProgress = 0
    private var installStatus = "Close Minecraft and its Launcher, then install Fabric Core 1.31.0."
    private var lastError = ""
    private var bridgeError = ""
    private var pendingReplies: [(String, Any)] = []
    private var soundsEnabled = true
    private var activeSounds: [NSSound] = []
    private var directory: URL!
    private var selfTesting = false
    private var selfTestStarted = false
    private var selfTestResult: [String: Any] = [:]
    private var selfTestWatchdog: Timer?
    private var qaOutput: URL?
    private var explicitBridgeRoot = false
    private var gameOpenRequests = 0
    private var checkingGameResume = false
    private var checkedGameResume = false
    private var takingSnapshots = false
    private var snapshotsFinished = false
    private var simpleVoiceChat: Bool { Bundle.main.object(forInfoDictionaryKey: "TungTungSimpleVoiceChat") as? Bool == true }

    func applicationDidFinishLaunching(_ notification: Notification) {
        if simpleVoiceChat {
            installStatus = "Open Microphone to apply the Mac voice fix. Keep using your Tung Tung Client 26.3 profile in Minecraft Launcher."
        }
        let arguments = CommandLine.arguments
        selfTesting = arguments.contains("--self-test")
        chessBridge = ChessBridge(defaults: selfTesting ? nil : defaults)
        chessWindow = ChessWindow(bridge: chessBridge, resources: resources)
        nativeChess = ChessNativeController(bridge: chessBridge, defaults: selfTesting ? nil : defaults)
        chessBridge.nativeStatus = { [weak self] in self?.nativeChess.state ?? [:] }
        nativeChess.onState = { [weak self] in self?.publish() }
        chessBridge.onState = { [weak self] in self?.publish() }
        if let path = Self.argument("--qa-output") { qaOutput = URL(fileURLWithPath: path) }
        let selected = Self.argument("--bridge-root")
        explicitBridgeRoot = selected != nil
        let saved = selfTesting ? nil : defaults.string(forKey: "minecraftFolder")
        directory = URL(fileURLWithPath: selected ?? saved ?? FabricInstaller.defaultMinecraftDirectory.path, isDirectory: true)
            .standardizedFileURL
        soundsEnabled = selfTesting ? false : (defaults.object(forKey: "soundsEnabled") as? Bool ?? true)
        bridge = GameBridge(minecraftDirectory: directory)
        bridge.onState = { [weak self] _, error in self?.bridgeError = error ?? ""; self?.publish() }
        robloxBridge.onState = { [weak self] _ in self?.publishRoblox() }
        if selfTesting && arguments.contains("--qa-check-open") {
            // A legacy game core's P event must not control the ordinary desktop window.
            bridge.onOpenClient = { [weak self] in
                guard let self else { return }
                self.gameOpenRequests += 1
                self.showClient()
            }
        }
        if let image = NSImage(contentsOf: resources.appendingPathComponent("ui/assets/logo.png")) { NSApp.applicationIconImage = image }
        buildMenu(); buildWindow(); buildStatusItem()
        if !selfTesting || explicitBridgeRoot { bridge.start() }
        if !selfTesting { robloxBridge.start() }
        if !selfTesting { chessBridge.start() }
        if !selfTesting { nativeChess.start() }
        if selfTesting {
            selfTestWatchdog = Timer.scheduledTimer(withTimeInterval: 20, repeats: false) { [weak self] _ in
                self?.finishSelfTest(error: "The local application page did not finish its self-test within 20 seconds.")
            }
        }
        showClient()
    }
    private static func argument(_ name: String) -> String? {
        let args = CommandLine.arguments
        guard let index = args.firstIndex(of: name), index + 1 < args.count else { return nil }
        return args[index + 1]
    }
    private func buildWindow() {
        let configuration = WKWebViewConfiguration()
        let handler = WeakMessageHandler(self)
        configuration.userContentController.add(handler, name: "native")
        configuration.userContentController.add(handler, name: "tungNative")
        configuration.websiteDataStore = .nonPersistent()
        web = WKWebView(frame: .zero, configuration: configuration)
        web.navigationDelegate = self; web.uiDelegate = self
        web.setValue(false, forKey: "drawsBackground")
        if #available(macOS 13.3, *) { web.isInspectable = selfTesting }
        window = ClientWindow(contentRect: NSRect(x: 0, y: 0, width: 1180, height: 760),
                          styleMask: [.titled, .closable, .miniaturizable, .resizable], backing: .buffered, defer: false)
        window.title = "Tung Tung Client"; window.minSize = NSSize(width: 1000, height: 660)
        window.backgroundColor = NSColor(calibratedRed: 0.035, green: 0.035, blue: 0.045, alpha: 1)
        window.isReleasedWhenClosed = false; window.delegate = self; window.contentView = web
        window.center()
        let index = resources.appendingPathComponent("ui/index.html")
        guard FileManager.default.fileExists(atPath: index.path) else {
            lastError = "Bundled ui/index.html is missing. Download and extract a complete copy of the app."
            if selfTesting { finishSelfTest(error: lastError) }
            else { alert(lastError) }
            return
        }
        web.loadFileURL(index, allowingReadAccessTo: resources)
    }
    private func buildMenu() {
        let menu = NSMenu()
        let appItem = NSMenuItem(); menu.addItem(appItem)
        let appMenu = NSMenu(); appItem.submenu = appMenu
        appMenu.addItem(withTitle: "About Tung Tung Client", action: #selector(about), keyEquivalent: "")
        appMenu.addItem(.separator())
        appMenu.addItem(withTitle: "Show Client", action: #selector(showClient), keyEquivalent: "1")
        appMenu.addItem(withTitle: "Launch Minecraft", action: #selector(menuLaunchMinecraft), keyEquivalent: "m")
        appMenu.addItem(withTitle: "Install Fabric Core", action: #selector(menuInstallCore), keyEquivalent: "")
        appMenu.addItem(withTitle: "Choose Minecraft Folder…", action: #selector(menuChooseFolder), keyEquivalent: "")
        appMenu.addItem(.separator())
        appMenu.addItem(withTitle: "Hide Tung Tung Client", action: #selector(NSApplication.hide(_:)), keyEquivalent: "h")
        appMenu.addItem(withTitle: "Quit Tung Tung Client", action: #selector(quit), keyEquivalent: "q")
        for item in appMenu.items where item.action != #selector(NSApplication.hide(_:)) { item.target = self }
        let editItem = NSMenuItem(); editItem.title = "Edit"; menu.addItem(editItem)
        let edit = NSMenu(title: "Edit"); editItem.submenu = edit
        for (title, selector, key) in [("Cut", "cut:", "x"), ("Copy", "copy:", "c"), ("Paste", "paste:", "v"), ("Select All", "selectAll:", "a")] {
            edit.addItem(withTitle: title, action: Selector(selector), keyEquivalent: key)
        }
        NSApp.mainMenu = menu
    }
    private func buildStatusItem() {
        status = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        status?.button?.title = "TT"; status?.button?.toolTip = "Tung Tung Client"
        let menu = NSMenu()
        menu.addItem(withTitle: "Open Tung Tung Client", action: #selector(showClient), keyEquivalent: "")
        menu.addItem(withTitle: "Launch Minecraft", action: #selector(menuLaunchMinecraft), keyEquivalent: "")
        menu.addItem(.separator()); menu.addItem(withTitle: "Quit", action: #selector(quit), keyEquivalent: "")
        for item in menu.items { item.target = self }; status?.menu = menu
    }
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { false }
    func windowShouldClose(_ sender: NSWindow) -> Bool { hideClient(); return false }
    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool { showClient(); return true }
    func applicationWillTerminate(_ notification: Notification) {
        nativeChess?.stop(); chessWindow?.close(); chessBridge?.stop(); bridge?.stop(); robloxBridge.stop()
    }

    @objc func showClient() {
        guard window != nil else { return }
        if let foreground = NSWorkspace.shared.frontmostApplication, foreground.processIdentifier != ProcessInfo.processInfo.processIdentifier {
            previousApplication = foreground
        }
        let wasHidden = !window.isVisible || NSApp.isHidden
        window.makeKeyAndOrderFront(nil); NSApp.activate(ignoringOtherApps: true)
        if wasHidden {
            window.alphaValue = 0
            NSAnimationContext.runAnimationGroup { context in context.duration = 0.18; window.animator().alphaValue = 1 }
        }
    }
    private func hideClient() {
        window.orderOut(nil); NSApp.hide(nil)
    }
    private func focusMinecraft() {
        if let number = bridge.latestState["gameProcessId"] as? NSNumber,
           CFGetTypeID(number) != CFBooleanGetTypeID(),
           number.int64Value > 0, number.int64Value <= Int64(Int32.max),
           let game = NSRunningApplication(processIdentifier: pid_t(number.int64Value)), !game.isTerminated {
            game.activate(options: [.activateIgnoringOtherApps])
        }
    }
    private func resumeAndHide(id: String?, generation: Int? = nil) {
        bridge.send(path: "/action", payload: ["action": "resume"], expectedGeneration: generation) { [weak self] result in
            guard let self else { return }
            if case .success = result { self.hideClient(); self.focusMinecraft() }
            if let id { self.reply(id, result: result) }
        }
    }
    @objc func quit() { NSApp.terminate(nil) }
    @objc func about() {
        NSApp.orderFrontStandardAboutPanel(options: [.applicationName: "Tung Tung Client",
            .applicationVersion: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.31.0",
            .credits: NSAttributedString(string: "Native macOS client · Minecraft Java 26.3 / Fabric\nGame functions use the authenticated localhost bridge.")])
    }
    private func launcherURL() -> URL? {
        if let registered = NSWorkspace.shared.urlForApplication(withBundleIdentifier: "com.mojang.minecraftlauncher") { return registered }
        for path in ["/Applications/Minecraft.app", "\(NSHomeDirectory())/Applications/Minecraft.app"] {
            if FileManager.default.fileExists(atPath: path) { return URL(fileURLWithPath: path) }
        }
        return nil
    }
    private func prismURL() -> URL? {
        if let registered = NSWorkspace.shared.urlForApplication(withBundleIdentifier: "org.prismlauncher.PrismLauncher") { return registered }
        for path in ["/Applications/PrismLauncher.app", "\(NSHomeDirectory())/Applications/PrismLauncher.app", "/Applications/Prism Launcher.app", "\(NSHomeDirectory())/Applications/Prism Launcher.app"] {
            if FileManager.default.fileExists(atPath: path) { return URL(fileURLWithPath: path) }
        }
        return nil
    }
    private func voiceChatSetup(id: String?) {
        do {
            let message = try VoiceRuntime.apply()
            lastError = ""; installStatus = message; publish()
            if let id { reply(id, data: ["message": message]) } else { alert(message) }
        } catch {
            lastError = error.localizedDescription; publish()
            if let id { reply(id, error: lastError) } else { alert(lastError) }
        }
    }
    private func openPrism(_ launcher: URL, configuration: NSWorkspace.OpenConfiguration, id: String?, message: String) {
        NSWorkspace.shared.openApplication(at: launcher, configuration: configuration) { [weak self] _, error in
            DispatchQueue.main.async {
                guard let self else { return }
                if let error {
                    self.lastError = error.localizedDescription
                    if let id { self.reply(id, error: error.localizedDescription) } else { self.alert(error.localizedDescription) }
                } else {
                    self.lastError = ""
                    if let id { self.reply(id, data: ["message": message]) }
                }
                self.publish()
            }
        }
    }
    @objc func menuLaunchMinecraft() { launchMinecraft(id: nil) }
    private func launchMinecraft(id: String?) {
        guard let launcher = launcherURL() else {
            let message = "The official Minecraft Launcher is not installed. Download it from minecraft.net, open it once and sign in yourself."
            lastError = message; publish()
            if let id { reply(id, error: message) } else { alert(message) }
            return
        }
        NSWorkspace.shared.openApplication(at: launcher, configuration: NSWorkspace.OpenConfiguration()) { [weak self] _, error in
            DispatchQueue.main.async {
                guard let self else { return }
                if let error { self.lastError = error.localizedDescription; if let id { self.reply(id, error: error.localizedDescription) } }
                else if let id { self.reply(id, data: ["message": "Minecraft Launcher opened. Select Tung Tung Client 26.3 and Play."]) }
                self.publish()
            }
        }
    }
    @objc func menuChooseFolder() { chooseFolder(id: nil) }
    private func chooseFolder(id: String?) {
        let panel = NSOpenPanel()
        panel.title = "Choose the Minecraft folder"
        panel.message = "Default: ~/Library/Application Support/minecraft. For Prism, choose the instance Minecraft folder."
        panel.canChooseDirectories = true; panel.canChooseFiles = false; panel.allowsMultipleSelection = false
        panel.directoryURL = directory
        panel.beginSheetModal(for: window) { [weak self] response in
            guard let self else { return }
            guard response == .OK, var chosen = panel.url else { if let id { self.reply(id, data: ["cancelled": true]) }; return }
            if chosen.lastPathComponent == "tun-tun-26.3" { chosen.deleteLastPathComponent() }
            self.directory = chosen.standardizedFileURL
            if !self.selfTesting { self.defaults.set(self.directory.path, forKey: "minecraftFolder") }
            self.bridge.directoryChanged(self.directory); self.lastError = ""; self.publish()
            if let id { self.reply(id, data: self.preferences()) }
        }
    }
    @objc func menuInstallCore() { installCore(id: nil) }
    private func installCore(id: String?) {
        guard !installing else { if let id { reply(id, error: "A core installation is already running.") }; return }
        installing = true; installProgress = 0; installStatus = "Installing the bundled Fabric core…"; lastError = ""; publish()
        let target = directory!, assets = resources
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let outcome = Result { try FabricInstaller.install(minecraftDirectory: target, resources: assets) }
            DispatchQueue.main.async {
                guard let self else { return }; self.installing = false
                switch outcome {
                case .success(let result):
                    self.installProgress = 100; self.installStatus = result.summary; self.lastError = ""
                    if let id { self.reply(id, data: ["message": result.summary, "gameDirectory": result.gameDirectory.path,
                                                     "profileId": result.profileId, "versionId": result.versionId,
                                                     "backupsDirectory": result.backupsDirectory.path]) }
                case .failure(let error):
                    self.installProgress = 0; self.installStatus = "Installation did not complete."; self.lastError = error.localizedDescription
                    if let id { self.reply(id, error: error.localizedDescription) } else { self.alert(error.localizedDescription) }
                }
                self.publish()
            }
        }
    }
    private func preferences() -> [String: Any] {
        ["minecraftFolder": directory.path, "soundsEnabled": soundsEnabled, "installing": installing,
         "installProgress": installProgress, "installStatus": currentCoreStatus(), "launcherAvailable": launcherURL() != nil, "voiceRuntime": VoiceRuntime.status(),
         "simpleVoiceChat": simpleVoiceChat,
         "lastError": lastError.isEmpty ? bridgeError : lastError,
         "version": Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.31.0"]
    }
    private func currentCoreStatus() -> String {
        guard !installing, bridge.latestState["transportConnected"] as? Bool == true else { return installStatus }
        guard let version = bridge.latestState["gameVersion"] as? String,
              version.compare("1.31.0", options: .numeric) != .orderedAscending else {
            return "Core update required. Close Minecraft and its Launcher, install Fabric Core 1.31.0, then restart Tung Tung Client 26.3."
        }
        return "Game core \(version) is connected. Enable a module, then click Return to Minecraft."
    }
    private func presentedState() -> [String: Any] {
        var state = bridge.latestState
        for (key, value) in preferences() { state[key] = value }
        state["chess"] = chessBridge.state
        return state
    }
    private func publish() { if ready { emit("update", object: presentedState()) } }
    private func publishRoblox() {
        guard ready, let data = try? JSONSerialization.data(withJSONObject: robloxBridge.state),
              let json = String(data: data, encoding: .utf8),
              let literal = try? JSONSerialization.data(withJSONObject: json, options: .fragmentsAllowed),
              let escaped = String(data: literal, encoding: .utf8) else { return }
        web.evaluateJavaScript("window.TungRoblox && window.TungRoblox.update(JSON.parse(\(escaped)))", completionHandler: nil)
    }
    private func reply(_ id: String, data: Any = [:]) { emit("reply", object: ["id": id, "ok": true, "data": data]) }
    private func reply(_ id: String, error: String) { emit("reply", object: ["id": id, "ok": false, "error": error]) }
    private func reply(_ id: String, result: Result<[String: Any], Error>) {
        switch result { case .success(let state): reply(id, data: state); case .failure(let error): reply(id, error: error.localizedDescription) }
    }
    private func emit(_ method: String, object: Any) {
        guard ["update", "reply"].contains(method), JSONSerialization.isValidJSONObject(object) else { return }
        if !ready { if pendingReplies.count < 64 { pendingReplies.append((method, object)) }; return }
        guard
              let json = try? JSONSerialization.data(withJSONObject: object), let text = String(data: json, encoding: .utf8),
              let literal = try? JSONSerialization.data(withJSONObject: text, options: .fragmentsAllowed),
              let escaped = String(data: literal, encoding: .utf8) else { return }
        web.evaluateJavaScript("window.TungNative && window.TungNative.\(method)(JSON.parse(\(escaped)))", completionHandler: nil)
    }
    private func alert(_ message: String) {
        let alert = NSAlert(); alert.messageText = "Tung Tung Client"; alert.informativeText = message
        alert.beginSheetModal(for: window, completionHandler: nil)
    }

    func handle(_ message: WKScriptMessage) {
        guard message.frameInfo.isMainFrame, Self.allowed(message.frameInfo.request.url, resources: resources),
              let object = message.body as? [String: Any], let id = object["id"] as? String, id.count <= 128,
              let action = object["action"] as? String, action.count <= 64 else { return }
        var payload = object["payload"] as? [String: Any] ?? [:]
        let maximum = action == "saveJSON" ? 2 * 1024 * 1024 : 65536
        guard let encoded = try? JSONSerialization.data(withJSONObject: payload), encoded.count <= maximum else {
            reply(id, error: "The application request exceeds its size limit."); return
        }
        let providedGeneration = payload.removeValue(forKey: "bridgeGeneration") as? NSNumber
        let actionGeneration: Int? = providedGeneration.flatMap {
            guard CFGetTypeID($0) != CFBooleanGetTypeID(), $0.doubleValue.isFinite,
                  $0.doubleValue.rounded() == $0.doubleValue, $0.int64Value >= 0 else { return nil }
            return $0.intValue
        }
        if ["module", "settings", "action"].contains(action), payload["action"] as? String != "resume", actionGeneration == nil {
            reply(id, error: "The game connection has not been confirmed. Wait for the current state and try again."); return
        }
        switch action {
        case "chessState": reply(id, data: ["chess": chessBridge.state])
        case "chessSettings":
            do { reply(id, data: ["chess": try chessBridge.update(payload)]) }
            catch { reply(id, error: error.localizedDescription) }
        case "chessWindow":
            do { if !selfTesting { try chessWindow.show() }; reply(id, data: ["chess": chessBridge.state]) }
            catch { reply(id, error: error.localizedDescription) }
        case "chessNative":
            if !selfTesting { nativeChess.connect() }
            reply(id, data: ["chess": chessBridge.state])
        case "chessSetup", "chessPair":
            if selfTesting { reply(id, data: ["chess": chessBridge.state]); return }
            guard let chrome = NSWorkspace.shared.urlForApplication(withBundleIdentifier: "com.google.Chrome") else {
                reply(id, error: "Install Google Chrome for the browser connection, or use Open Chess Window."); return
            }
            let target: URL
            if action == "chessPair" {
                guard let pair = chessBridge.pairingURL else { reply(id, error: "The Chrome connection is not ready. Reopen the client and try again."); return }
                target = pair
            } else {
                NSWorkspace.shared.open(resources.appendingPathComponent("chess-extension", isDirectory: true))
                target = URL(string: "chrome://extensions/")!
            }
            NSWorkspace.shared.open([target], withApplicationAt: chrome, configuration: NSWorkspace.OpenConfiguration()) { [weak self] _, error in
                DispatchQueue.main.async {
                    if let error { self?.reply(id, error: error.localizedDescription) }
                    else if let self { self.reply(id, data: ["chess": self.chessBridge.state]) }
                }
            }
        case "robloxState": reply(id, data: robloxBridge.state)
        case "robloxCommand": robloxBridge.send(payload) { [weak self] result in self?.reply(id, result: result) }
        case "robloxSetup":
            if !selfTesting { NSWorkspace.shared.open(resources.appendingPathComponent("roblox", isDirectory: true)) }
            reply(id, data: robloxBridge.state)
        case "robloxPair":
            if !selfTesting {
                robloxBridge.start()
                if FileManager.default.fileExists(atPath: robloxBridge.directory.path) { NSWorkspace.shared.open(robloxBridge.directory) }
            }
            reply(id, data: robloxBridge.state)
        case "openRoblox":
            if selfTesting { reply(id, data: ["opened": false]); return }
            guard let application = NSWorkspace.shared.urlForApplication(withBundleIdentifier: "com.roblox.RobloxPlayer") else {
                reply(id, error: "Roblox Player is not installed. Install it from roblox.com, then reopen the client."); return
            }
            NSWorkspace.shared.openApplication(at: application, configuration: NSWorkspace.OpenConfiguration()) { [weak self] _, error in
                DispatchQueue.main.async {
                    if let error { self?.reply(id, error: error.localizedDescription) }
                    else { self?.reply(id, data: ["opened": true]) }
                }
            }
        case "getPreferences": reply(id, data: preferences())
        case "getState": reply(id, data: presentedState())
        case "module", "settings": bridge.send(path: "/" + action, payload: payload, expectedGeneration: actionGeneration) { [weak self] result in self?.reply(id, result: result) }
        case "action":
            if payload["action"] as? String == "resume" { resumeAndHide(id: id, generation: actionGeneration) }
            else { bridge.send(path: "/action", payload: payload, expectedGeneration: actionGeneration) { [weak self] result in
                self?.reply(id, result: result)
                if payload["action"] as? String == "voice_mix_test", case .success = result { self?.hideClient(); self?.focusMinecraft() }
            } }
        case "resume": resumeAndHide(id: id)
        case "hide", "windowClose": hideClient(); reply(id)
        case "install", "installCore": installCore(id: id)
        case "chooseFolder", "chooseMinecraftFolder": chooseFolder(id: id)
        case "importSky":
            guard let actionGeneration, actionGeneration == bridge.connectionGeneration else { reply(id, error: "Connect Minecraft before choosing a sky image."); return }
            importSky(id: id, generation: actionGeneration)
        case "launchMinecraft": launchMinecraft(id: id)
        case "openChess":
            let pages=["play":"https://www.chess.com/play/online","bots":"https://www.chess.com/play/computer","puzzles":"https://www.chess.com/puzzles","analysis":"https://www.chess.com/analysis","learn":"https://www.chess.com/lessons"]
            guard let key=payload["section"] as? String,let address=pages[key],let url=URL(string:address) else {reply(id,error:"Choose a Chess.com tool.");return}
            if selfTesting {reply(id,data:["opened":key]);return}
            guard let browser=NSWorkspace.shared.urlForApplication(toOpen:URL(string:"https://example.com")!) else {reply(id,error:"Choose a default web browser in macOS settings.");return}
            NSWorkspace.shared.open([url],withApplicationAt:browser,configuration:NSWorkspace.OpenConfiguration()){[weak self] _,error in
                DispatchQueue.main.async{if let error {self?.reply(id,error:error.localizedDescription)}else{self?.reply(id,data:["opened":key])}}
            }
        case "voiceChatSetup": voiceChatSetup(id: id)
        case "microphoneSettings":
            NSWorkspace.shared.open(URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone")!); reply(id)
        case "showFolder", "showMinecraftFolder": NSWorkspace.shared.open(directory); reply(id)
        case "copy":
            let text = payload["text"] as? String ?? ""; NSPasteboard.general.clearContents(); NSPasteboard.general.setString(text, forType: .string); reply(id)
        case "saveJSON": saveJSON(id: id, payload: payload)
        case "sounds":
            if let enabled = payload["enabled"] as? Bool {
                soundsEnabled = enabled; if !selfTesting { defaults.set(enabled, forKey: "soundsEnabled") }
                if !enabled { activeSounds.forEach { $0.stop() }; activeSounds.removeAll() }; publish()
            }
            reply(id, data: ["soundsEnabled": soundsEnabled])
        case "playSound": playSound(payload["name"] as? String ?? "click"); reply(id)
        case "about": about(); reply(id)
        case "quit": reply(id); quit()
        default: reply(id, error: "Unknown native operation: \(action)")
        }
    }
    private func importSky(id: String, generation: Int) {
        let panel = NSOpenPanel()
        panel.title = "Choose your sky image"
        panel.message = "PNG or JPG. A wide panorama gives the best result. Large images are resized for smooth gameplay."
        panel.allowedContentTypes = [.png, .jpeg]
        panel.canChooseFiles = true; panel.canChooseDirectories = false; panel.allowsMultipleSelection = false
        let target = directory!
        panel.beginSheetModal(for: window) { [weak self] result in
            guard let self else { return }
            guard result == .OK, let source = panel.url else { self.reply(id, data: ["cancelled": true]); return }
            guard self.bridge.connectionGeneration == generation, self.directory == target else {
                self.reply(id, error: "The Minecraft connection changed. Choose the image again."); return
            }
            do {
                let data = try MediaFiles.skyPNG(source)
                try MediaFiles.saveSky(data, directory: target)
                self.bridge.send(path: "/action", payload: ["action": "media_setting", "key": "sky", "enabled": true], expectedGeneration: generation) { [weak self] result in self?.reply(id, result: result) }
            } catch { self.reply(id, error: error.localizedDescription) }
        }
    }
    private func saveJSON(id: String, payload: [String: Any]) {
        let value = payload["data"] ?? bridge.latestState["findings"] ?? []
        guard let data = try? JSONSerialization.data(withJSONObject: value, options: [.prettyPrinted, .fragmentsAllowed]), data.count <= 2 * 1024 * 1024 else {
            reply(id, error: "The export is invalid or exceeds 2 MB."); return
        }
        let panel = NSSavePanel(); panel.title = "Export observed findings"
        panel.nameFieldStringValue = "tung-tung-findings.json"; panel.allowedContentTypes = [.json]
        panel.beginSheetModal(for: window) { [weak self] response in
            guard let self else { return }
            guard response == .OK, let url = panel.url else { self.reply(id, data: ["cancelled": true]); return }
            do { try data.write(to: url, options: .atomic); self.reply(id, data: ["path": url.path]) }
            catch { self.reply(id, error: error.localizedDescription) }
        }
    }
    private func playSound(_ name: String) {
        guard soundsEnabled, !selfTesting, let file = ["click": "ui-click.wav", "startup": "startup-meow.wav", "meow": "startup-meow.wav"][name] else { return }
        activeSounds.removeAll { !$0.isPlaying }
        guard activeSounds.count < 4 else { return }
        for directory in ["assets", "ui/assets"] {
            if let sound = NSSound(contentsOf: resources.appendingPathComponent(directory + "/" + file), byReference: true) {
                activeSounds.append(sound); sound.play(); break
            }
        }
    }
    static func allowed(_ url: URL?, resources: URL) -> Bool {
        guard let url, url.isFileURL else { return false }
        let base = resources.standardizedFileURL.resolvingSymlinksInPath().path + "/"
        return url.standardizedFileURL.resolvingSymlinksInPath().path.hasPrefix(base)
    }
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        decisionHandler(Self.allowed(navigationAction.request.url, resources: resources) ? .allow : .cancel)
    }
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? { nil }
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        ready = true; publish(); publishRoblox()
        let queued = pendingReplies; pendingReplies.removeAll()
        for (method, object) in queued { emit(method, object: object) }
        if CommandLine.arguments.contains("--qa-no-intro") {
            web.evaluateJavaScript("document.querySelector('#continue-intro')?.click()", completionHandler: nil)
        }
        if selfTesting { runSelfTest() }
    }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        lastError = error.localizedDescription
        if selfTesting { finishSelfTest(error: lastError) }
    }
    private func runSelfTest() {
        guard !selfTestStarted else { return }; selfTestStarted = true
        let fileManager = FileManager.default
        var checks = GameBridge.contractChecks()
        checks["bundledPagePresent"] = fileManager.fileExists(atPath: resources.appendingPathComponent("ui/index.html").path)
        checks["remoteNavigationRejected"] = !Self.allowed(URL(string: "https://example.com/"), resources: resources)
        checks["outsideFileNavigationRejected"] = !Self.allowed(URL(fileURLWithPath: "/etc/passwd"), resources: resources)
        checks["bundledFileNavigationAllowed"] = Self.allowed(resources.appendingPathComponent("ui/index.html"), resources: resources)
        selfTestResult["checks"] = checks
        let uiCheck = """
        (()=>{const native=window.TungNative;document.querySelector('#continue-intro')?.click();
        window.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));
        window.dispatchEvent(new KeyboardEvent('keydown',{key:'p',bubbles:true}));
        const diagnostics=native?.selfTest?.()||null;const pages={};
        for(const name of diagnostics?.pages||[]){native.navigate(name);pages[name]=document.getElementById('app')?.childElementCount>0;}
        native?.navigate?.('home');return{reply:typeof native?.reply==='function',update:typeof native?.update==='function',title:document.title,diagnostics,pages};})()
        """
        web.evaluateJavaScript(uiCheck) { [weak self] value, error in
            guard let self else { return }
            if let error { self.finishSelfTest(error: error.localizedDescription); return }
            self.selfTestResult["webView"] = value ?? [:]
            guard let object = value as? [String: Any], object["reply"] as? Bool == true, object["update"] as? Bool == true else {
                self.finishSelfTest(error: "The bundled JavaScript bridge is unavailable."); return
            }
            // Allow any accidental asynchronous keyboard command to run before checking visibility.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                self.window.cancelOperation(nil)
                self.selfTestResult["formerShortcutKeysKeepWindowVisible"] = self.window.isVisible && !NSApp.isHidden
                if !self.window.isVisible || NSApp.isHidden { self.finishSelfTest(error: "A former keyboard shortcut hid the native app."); return }
                if self.explicitBridgeRoot { self.testAuthenticatedBridge() }
                else { self.finishSelfTest(error: nil) }
            }
        }
    }
    private func testAuthenticatedBridge() {
        bridge.fetchState { [weak self] result in
            guard let self else { return }
            guard case .success(let initial) = result,
                  let original = (initial["settings"] as? [String: Any])?["sensitivity"] as? Int else {
                if case .failure(let error) = result { self.finishSelfTest(error: error.localizedDescription) }
                else { self.finishSelfTest(error: "The authenticated bridge did not return sensitivity settings.") }
                return
            }
            self.selfTestResult["authenticatedStateGet"] = true
            self.selfTestResult["genuineGameConnected"] = initial["connected"] as? Bool == true
            if CommandLine.arguments.contains("--qa-check-resume") {
                self.selfTestResult["startedWithActualPauseScreen"] = initial["gameScreenType"] as? String == "PauseScreen"
                guard initial["gameScreenType"] as? String == "PauseScreen" else {
                    self.finishSelfTest(error: "Resume verification requires the own game to start with its actual PauseScreen open.")
                    return
                }
            }
            let changed = original == 65 ? 66 : 65
            self.bridge.send(path: "/settings", payload: ["sensitivity": changed]) { roundTrip in
                let matches: Bool
                if case .success(let state) = roundTrip { matches = (state["settings"] as? [String: Any])?["sensitivity"] as? Int == changed }
                else { matches = false }
                self.selfTestResult["authenticatedSettingsRoundTrip"] = matches
                self.bridge.send(path: "/settings", payload: ["sensitivity": original]) { restored in
                    if case .success(let state) = restored {
                        self.selfTestResult["originalSettingsRestored"] = (state["settings"] as? [String: Any])?["sensitivity"] as? Int == original
                        self.testNativeModule(initial: initial)
                    } else { self.finishSelfTest(error: "The self-test could not restore the original sensitivity.") }
                }
            }
        }
    }
    private func testNativeModule(initial: [String: Any]) {
        guard initial["connected"] as? Bool == true,
              let modules = initial["modules"] as? [[String: Any]],
              let module = modules.first(where: { $0["id"] as? String == "chunk_finder" }),
              module["available"] as? Bool == true, let original = module["enabled"] as? Bool else {
            self.selfTestResult["nativeModuleToggle"] = "Unavailable: no connected, supported Chunk Finder module"
            self.testGameOpenSequence(); return
        }
        let script = "(()=>{window.TungNative.navigate('world-tools');document.querySelector('#continue-intro')?.click();const b=document.querySelector('[data-toggle-module=\"chunk_finder\"]');if(!b||b.disabled)return false;b.click();return true;})()"
        web.evaluateJavaScript(script) { [weak self] clicked, error in
            guard let self else { return }
            guard error == nil, clicked as? Bool == true else { self.finishSelfTest(error: "The actual native page could not click its Chunk Finder switch."); return }
            self.waitForModule(expected: !original, attempts: 15) { matched in
                self.selfTestResult["nativeModuleToggle"] = matched
                self.bridge.send(path: "/module", payload: ["id": "chunk_finder", "enabled": original]) { restored in
                    if case .success(let state) = restored {
                        let found = (state["modules"] as? [[String: Any]])?.first(where: { $0["id"] as? String == "chunk_finder" })
                        self.selfTestResult["originalModuleRestored"] = found?["enabled"] as? Bool == original
                    } else { self.selfTestResult["originalModuleRestored"] = false }
                    self.testGameOpenSequence()
                }
            }
        }
    }
    private func waitForModule(expected: Bool, attempts: Int, completion: @escaping (Bool) -> Void) {
        bridge.fetchState { [weak self] result in
            guard let self else { return }
            if case .success(let state) = result,
               let module = (state["modules"] as? [[String: Any]])?.first(where: { $0["id"] as? String == "chunk_finder" }),
               module["enabled"] as? Bool == expected { completion(true); return }
            guard attempts > 0 else { completion(false); return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { self.waitForModule(expected: expected, attempts: attempts - 1, completion: completion) }
        }
    }
    private func testGameOpenSequence() {
        guard CommandLine.arguments.contains("--qa-check-open") else { finishSelfTest(error: nil); return }
        let starting = gameOpenRequests
        window.orderOut(nil); NSApp.hide(nil)
        if let qaOutput {
            let marker = qaOutput.deletingLastPathComponent().appendingPathComponent("qa-awaiting-open.marker")
            try? Data("waiting for the genuine game openClientSequence\n".utf8).write(to: marker, options: .atomic)
        }
        waitForGameOpen(starting: starting, attempts: 40)
    }
    private func waitForGameOpen(starting: Int, attempts: Int) {
        if gameOpenRequests > starting {
            selfTestResult["gameOpenSequenceHandled"] = window.isVisible && !NSApp.isHidden && NSApp.isActive
            selfTestResult["nativeWindowVisible"] = window.isVisible
            selfTestResult["nativeApplicationActive"] = NSApp.isActive
            if let qaOutput { try? FileManager.default.removeItem(at: qaOutput.deletingLastPathComponent().appendingPathComponent("qa-awaiting-open.marker")) }
            finishSelfTest(error: nil); return
        }
        guard attempts > 0 else { finishSelfTest(error: "No explicit game openClientSequence request arrived during the check."); return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { [weak self] in self?.waitForGameOpen(starting: starting, attempts: attempts - 1) }
    }
    private func finishSelfTest(error: String?) {
        if error == nil, explicitBridgeRoot, CommandLine.arguments.contains("--qa-check-resume"),
           !checkedGameResume, !checkingGameResume {
            checkingGameResume = true
            testNativeResume()
            return
        }
        if qaOutput != nil, !takingSnapshots, !snapshotsFinished, ready {
            takingSnapshots = true
            selfTestWatchdog?.invalidate()
            selfTestWatchdog = Timer.scheduledTimer(withTimeInterval: 30, repeats: false) { [weak self] _ in
                self?.finishSelfTest(error: "The own-webview snapshot sequence timed out.")
            }
            capturePages(index: 0, result: [:]) { [weak self] results in
                guard let self else { return }
                self.takingSnapshots = false; self.snapshotsFinished = true
                self.selfTestResult["nativeScreenshots"] = results
                let succeeded = results.values.allSatisfy { ($0 as? [String: Any])?["ok"] as? Bool == true }
                self.finishSelfTest(error: error ?? (succeeded ? nil : "One or more own-webview page snapshots failed."))
            }
            return
        }
        selfTestWatchdog?.invalidate(); selfTestWatchdog = nil
        bridge?.stop()
        var report = selfTestResult
        let checks = report["checks"] as? [String: Bool] ?? [:]
        let http = !explicitBridgeRoot || (report["authenticatedSettingsRoundTrip"] as? Bool == true
            && report["originalSettingsRestored"] as? Bool == true
            && (report["nativeModuleToggle"] as? Bool != false)
            && (report["originalModuleRestored"] as? Bool != false))
        let open = !CommandLine.arguments.contains("--qa-check-open") || report["gameOpenSequenceHandled"] as? Bool == true
        let resume = !CommandLine.arguments.contains("--qa-check-resume") || report["gameResumeHandled"] as? Bool == true
        report["ok"] = error == nil && !checks.isEmpty && checks.values.allSatisfy { $0 } && http && open && resume
        report["mode"] = "Own native app / local bundled WKWebView / diagnostic HTTP only; no simulated game findings"
        if let error { report["error"] = error }
        guard let data = try? JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]) else { exit(1) }
        if let qaOutput { try? data.write(to: qaOutput, options: .atomic) }
        FileHandle.standardOutput.write(data); FileHandle.standardOutput.write(Data("\n".utf8))
        exit(report["ok"] as? Bool == true ? 0 : 1)
    }
    private func testNativeResume() {
        web.evaluateJavaScript("document.querySelector('#continue-intro')?.click()") { [weak self] _, _ in
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                self?.web.evaluateJavaScript("document.querySelector('[data-native=\"resume\"]')?.click()") { _, error in
                    guard let self else { return }
                    if let error { self.finishSelfTest(error: error.localizedDescription); return }
                    self.waitForGameResume(attempts: 30)
                }
            }
        }
    }
    private func waitForGameResume(attempts: Int) {
        bridge.fetchState { [weak self] result in
            guard let self else { return }
            if case .success(let state) = result,
               state["gameScreenOpen"] as? Bool == false, state["mouseGrabbed"] as? Bool == true,
               let pid = state["gameProcessId"] as? NSNumber,
               NSWorkspace.shared.frontmostApplication?.processIdentifier == pid_t(pid.int32Value),
               !self.window.isVisible {
                self.selfTestResult["gameResumeHandled"] = true
                self.selfTestResult["gameFocusedAfterReturnButton"] = true
                self.checkedGameResume = true; self.checkingGameResume = false
                self.showClient()
                self.finishSelfTest(error: nil)
                return
            }
            guard attempts > 0 else {
                self.checkedGameResume = true; self.checkingGameResume = false
                self.selfTestResult["gameResumeHandled"] = false
                self.finishSelfTest(error: "Return to Minecraft did not close the own game pause screen and focus Minecraft.")
                return
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { self.waitForGameResume(attempts: attempts - 1) }
        }
    }
    private func capturePages(index: Int, result: [String: Any], completion: @escaping ([String: Any]) -> Void) {
        let pages = ["home", "world-tools", "findings", "finder-settings", "pvp", "pvp-settings", "player-settings", "tutorials", "settings", "camera-settings", "assist-settings", "find-player", "resource-highlights", "microphone", "texture-packs", "media-settings", "module-settings", "keyboard-customize"]
        guard index < pages.count, let qaOutput else { completion(result); return }
        let page = pages[index]
        web.evaluateJavaScript("window.TungNative.navigate('\(page)');document.querySelector('#continue-intro')?.click()") { [weak self] _, navigationError in
            guard let self else { return }
            if let navigationError {
                var updated = result; updated[page] = ["ok": false, "error": navigationError.localizedDescription]
                self.capturePages(index: index + 1, result: updated, completion: completion); return
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.22) {
                let configuration = WKSnapshotConfiguration(); configuration.snapshotWidth = NSNumber(value: 1180)
                self.web.takeSnapshot(with: configuration) { image, error in
                    var updated = result
                    do {
                        if let error { throw error }
                        guard let image, let tiff = image.tiffRepresentation,
                              let bitmap = NSBitmapImageRep(data: tiff),
                              let png = bitmap.representation(using: .png, properties: [:]) else {
                            throw NativeBridgeError.unavailable("WebKit did not produce a page image.")
                        }
                        let folder = qaOutput.deletingLastPathComponent().appendingPathComponent("screenshots", isDirectory: true)
                        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
                        let path = folder.appendingPathComponent(page + ".png")
                        try png.write(to: path, options: .atomic)
                        updated[page] = ["ok": true, "path": path.path, "width": bitmap.pixelsWide, "height": bitmap.pixelsHigh]
                    } catch { updated[page] = ["ok": false, "error": error.localizedDescription] }
                    self.capturePages(index: index + 1, result: updated, completion: completion)
                }
            }
        }
    }
}

@main struct TungTungMain {
    static func main() {
        let app = NSApplication.shared
        if CommandLine.arguments.contains("--apply-microphone-fix") {
            do { print(try VoiceRuntime.apply()); return }
            catch { FileHandle.standardError.write(Data((error.localizedDescription + "\n").utf8)); exit(1) }
        }
        if CommandLine.arguments.contains("--voice-runtime-status") {
            let data = try! JSONSerialization.data(withJSONObject: VoiceRuntime.status(), options: [.sortedKeys])
            FileHandle.standardOutput.write(data); print(""); return
        }
        if CommandLine.arguments.contains("--installer-self-test") {
            do {
                let checks = try FabricInstaller.runSelfTests(resources: Bundle.main.resourceURL!)
                let data = try JSONSerialization.data(withJSONObject: ["ok": true, "checks": checks], options: [.prettyPrinted, .sortedKeys])
                FileHandle.standardOutput.write(data); FileHandle.standardOutput.write(Data("\n".utf8)); return
            } catch {
                let data = try! JSONSerialization.data(withJSONObject: ["ok": false, "error": error.localizedDescription], options: [.sortedKeys])
                FileHandle.standardOutput.write(data); FileHandle.standardOutput.write(Data("\n".utf8)); exit(1)
            }
        }
        app.setActivationPolicy(.regular)
        let delegate = MacApplication(); app.delegate = delegate
        withExtendedLifetime(delegate) { app.run() }
    }
}
