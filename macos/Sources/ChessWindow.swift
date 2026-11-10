import AppKit
import WebKit

private final class ChessMessageHandler: NSObject, WKScriptMessageHandler {
    weak var owner: ChessWindow?
    init(_ owner: ChessWindow) { self.owner = owner }
    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) { owner?.handle(message) }
}

/// A normal signed-in Chess.com web view with the same post-game adapter as Chrome.
final class ChessWindow: NSObject, NSWindowDelegate, WKNavigationDelegate, WKUIDelegate {
    private let bridge: ChessBridge
    private let resources: URL
    private let clientId = "native-" + UUID().uuidString
    private var window: NSWindow?
    private var web: WKWebView?
    init(bridge: ChessBridge, resources: URL) { self.bridge = bridge; self.resources = resources }
    func show() throws {
        if let window { window.makeKeyAndOrderFront(nil); NSApp.activate(ignoringOtherApps: true); return }
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.userContentController.add(ChessMessageHandler(self), name: "tungChess")
        for name in ["engine.js", "native.js"] {
            let source = try String(contentsOf: resources.appendingPathComponent("chess/" + name), encoding: .utf8)
            config.userContentController.addUserScript(WKUserScript(source: source, injectionTime: .atDocumentEnd, forMainFrameOnly: true))
        }
        let browser = WKWebView(frame: .zero, configuration: config)
        browser.navigationDelegate = self; browser.uiDelegate = self
        let next = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 1180, height: 820), styleMask: [.titled, .closable, .miniaturizable, .resizable], backing: .buffered, defer: false)
        next.title = "Tung · Chess.com"; next.minSize = NSSize(width: 900, height: 650)
        next.isReleasedWhenClosed = false; next.delegate = self; next.contentView = browser; next.center()
        window = next; web = browser
        browser.load(URLRequest(url: URL(string: "https://www.chess.com/play/online")!))
        next.makeKeyAndOrderFront(nil); NSApp.activate(ignoringOtherApps: true)
    }
    func close() {
        bridge.session.close(clientId: clientId); bridge.onState?()
        web?.stopLoading(); web?.configuration.userContentController.removeScriptMessageHandler(forName: "tungChess")
        web?.navigationDelegate = nil; web?.uiDelegate = nil; window?.orderOut(nil)
        web = nil; window = nil
    }
    func windowWillClose(_ notification: Notification) { close() }
    func handle(_ message: WKScriptMessage) {
        guard message.frameInfo.isMainFrame, let url = message.frameInfo.request.url,
              url.scheme == "https", ["www.chess.com", "chess.com"].contains(url.host ?? ""),
              let object = message.body as? [String: Any], let id = object["id"] as? String, id.count <= 80,
              let action = object["action"] as? String,
              let raw = try? JSONSerialization.data(withJSONObject: object), raw.count <= 4096 else { return }
        var payload = object["payload"] as? [String: Any] ?? [:]; payload["clientId"] = clientId
        var reply: [String: Any] = ["id": id, "ok": true]
        do {
            if action == "poll" { reply["data"] = try bridge.nativePoll(payload) }
            else if action == "stop" { try bridge.nativeStop(payload); reply["data"] = [:] }
            else { throw NSError(domain: "TungChess", code: 3, userInfo: [NSLocalizedDescriptionKey: "Unknown Chess action."]) }
        } catch { reply["ok"] = false; reply["error"] = error.localizedDescription }
        guard let data = try? JSONSerialization.data(withJSONObject: reply), let json = String(data: data, encoding: .utf8) else { return }
        web?.evaluateJavaScript("window.TungChessNativeReply && window.TungChessNativeReply(\(json))", completionHandler: nil)
    }
    func webView(_ view: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url else { decisionHandler(.cancel); return }
        if url.scheme == "https", let host = url.host, host == "chess.com" || host.hasSuffix(".chess.com") { decisionHandler(.allow) }
        else if navigationAction.navigationType == .linkActivated && ["https", "http"].contains(url.scheme ?? "") { NSWorkspace.shared.open(url); decisionHandler(.cancel) }
        else { decisionHandler(.cancel) }
    }
    func webView(_ view: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for action: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if let url = action.request.url, url.scheme == "https" { NSWorkspace.shared.open(url) }; return nil
    }
}
