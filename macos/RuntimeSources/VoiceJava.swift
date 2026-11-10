// The official Launcher keeps authentication. Only its Java invocation is handed
// to a LaunchServices app so macOS can attribute microphone access correctly.
import AppKit
import AVFoundation
import Foundation
import Darwin

private let fm = FileManager.default
private let support = fm.homeDirectoryForCurrentUser.appendingPathComponent("Library/Application Support/Tung Tung Client")
private let launchRoot = support.appendingPathComponent("voice-launches")
private var interrupted: Int32 = 0
private var cleanupFolder: URL?
private func cleanup() { if let cleanupFolder { try? fm.removeItem(at: cleanupFolder) } }
private func interrupt(_ value: Int32) { interrupted = value }
private func fail(_ message: String) -> Never {
    FileHandle.standardError.write(Data(("[Tung Tung] " + message + "\n").utf8)); exit(1)
}
private func privateDirectory(_ url: URL) throws {
    try fm.createDirectory(at: url, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
    var info = stat()
    guard lstat(url.path, &info) == 0, info.st_uid == getuid(),
          info.st_mode & S_IFMT == S_IFDIR, info.st_mode & 0o077 == 0 else {
        throw NSError(domain: "TungTungVoice", code: 1, userInfo: [NSLocalizedDescriptionKey: "The private voice launch folder is unsafe."])
    }
}
private func privateWrite(_ data: Data, _ url: URL) throws {
    let fd = open(url.path, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0o600)
    guard fd >= 0 else { throw NSError(domain: NSPOSIXErrorDomain, code: Int(errno)) }
    let handle = FileHandle(fileDescriptor: fd, closeOnDealloc: true)
    try handle.write(contentsOf: data); try handle.close()
}
private func json(_ object: Any) throws -> Data { try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys]) }
private func readObject(_ url: URL) throws -> [String: Any] {
    var info = stat()
    guard lstat(url.path, &info) == 0, info.st_uid == getuid(), info.st_mode & S_IFMT == S_IFREG,
          info.st_mode & 0o077 == 0, info.st_size <= 2 * 1024 * 1024 else {
        throw NSError(domain: "TungTungVoice", code: 2, userInfo: [NSLocalizedDescriptionKey: "The private launch request is invalid."])
    }
    guard let result = try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any] else {
        throw NSError(domain: "TungTungVoice", code: 3)
    }
    return result
}
private func permissionName() -> String {
    switch AVCaptureDevice.authorizationStatus(for: .audio) {
    case .authorized: return "authorized"
    case .denied: return "denied"
    case .restricted: return "restricted"
    case .notDetermined: return "notDetermined"
    @unknown default: return "unknown"
    }
}

#if VOICE_HOST
private final class VoiceGame: NSObject, NSApplicationDelegate {
    private var request: [String: Any] = [:]
    private var folder: URL!
    private var game: Process?
    private var watchdog: Timer?
    private var stopping = false
    func applicationDidFinishLaunching(_ notification: Notification) {
        do {
            guard CommandLine.arguments.count == 3, CommandLine.arguments[1] == "--handoff" else { throw NSError(domain: "TungTungVoice", code: 4) }
            let file = URL(fileURLWithPath: CommandLine.arguments[2]).standardizedFileURL
            folder = file.deletingLastPathComponent()
            guard folder.deletingLastPathComponent().path == launchRoot.path,
                  UUID(uuidString: folder.lastPathComponent) != nil, file.lastPathComponent == "request.json" else {
                throw NSError(domain: "TungTungVoice", code: 5)
            }
            try privateDirectory(launchRoot); try privateDirectory(folder)
            request = try readObject(file)
            // Arguments can contain the Launcher's credentials: remove immediately,
            // never put them in status output, logs, defaults or a process argument.
            try fm.removeItem(at: file)
            try privateWrite(try json(["started": true, "hostPID": getpid()]), folder.appendingPathComponent("started.json"))
            watchdog = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] _ in
                guard let self, let pid = self.request["parentPID"] as? Int32 else { return }
                if kill(pid, 0) != 0 || fm.fileExists(atPath: self.folder.appendingPathComponent("cancel").path) {
                    self.stopping = true
                    if let game = self.game, game.isRunning { game.terminate() }
                    else { self.finish(143) }
                }
            }
            if request["requestPermission"] as? Bool != false && AVCaptureDevice.authorizationStatus(for: .audio) == .notDetermined {
                AVCaptureDevice.requestAccess(for: .audio) { [weak self] _ in DispatchQueue.main.async { self?.launch() } }
            } else { launch() }
        } catch { finish(1, error: "Could not read the private Java handoff. Reapply the microphone fix in Tung Tung Client.") }
    }
    private func launch() {
        guard !stopping else { return }
        do {
            guard let java = request["java"] as? String, java.hasSuffix("/bin/java"),
                  fm.isExecutableFile(atPath: java), let args = request["arguments"] as? [String],
                  let cwd = request["cwd"] as? String, let env = request["environment"] as? [String: String] else {
                throw NSError(domain: "TungTungVoice", code: 6)
            }
            let child = Process(); game = child
            child.executableURL = URL(fileURLWithPath: java); child.arguments = args
            child.currentDirectoryURL = URL(fileURLWithPath: cwd); child.environment = env
            child.standardInput = FileHandle.nullDevice
            for name in ["stdout", "stderr"] {
                let path = folder.appendingPathComponent(name).path
                var info = stat()
                guard lstat(path, &info) == 0, info.st_uid == getuid(), info.st_mode & S_IFMT == S_IFIFO,
                      info.st_mode & 0o077 == 0 else { throw NSError(domain: "TungTungVoice", code: 7) }
                let handle = try FileHandle(forWritingTo: URL(fileURLWithPath: path))
                if name == "stdout" { child.standardOutput = handle } else { child.standardError = handle }
            }
            child.terminationHandler = { [weak self] process in
                DispatchQueue.main.async { self?.finish(process.terminationStatus) }
            }
            try child.run()
            request.removeValue(forKey: "arguments"); request.removeValue(forKey: "environment")
            let state: [String: Any] = ["permission": permissionName(), "gamePID": child.processIdentifier,
                                        "startedAt": ISO8601DateFormatter().string(from: Date())]
            try json(state).write(to: support.appendingPathComponent("voice-last-launch.json"), options: .atomic)
            try fm.setAttributes([.posixPermissions: 0o600], ofItemAtPath: support.appendingPathComponent("voice-last-launch.json").path)
        } catch { finish(1, error: "The Java handoff could not start Minecraft. Reapply the microphone fix in Tung Tung Client.") }
    }
    private func finish(_ code: Int32, error: String? = nil) {
        watchdog?.invalidate()
        if let folder {
            var result: [String: Any] = ["exitCode": code, "permission": permissionName()]
            if let error { result["error"] = error }
            try? privateWrite((try? json(result)) ?? Data(), folder.appendingPathComponent("result.json"))
        }
        NSApp.terminate(nil)
    }
    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        if let game, game.isRunning { game.terminate(); return .terminateCancel }
        return .terminateNow
    }
}
@main struct VoiceHostMain {
    static func main() {
        let app = NSApplication.shared; app.setActivationPolicy(.accessory)
        let delegate = VoiceGame(); app.delegate = delegate
        withExtendedLifetime(delegate) { app.run() }
    }
}
#else
@main struct VoiceJavaMain {
    static func main() {
        do {
            let config = try readObject(support.appendingPathComponent("voice-runtime.json"))
            guard let java = config["java"] as? String, java.hasSuffix("/bin/java"), fm.isExecutableFile(atPath: java) else {
                fail("Java moved. Open Tung Tung Client → Microphone → Apply microphone fix.")
            }
            var args = Array(CommandLine.arguments.dropFirst())
            let qa = args.first == "--tung-tung-runtime-test"
            if qa { args.removeFirst() }
            let game = args.contains("net.fabricmc.loader.impl.launch.knot.KnotClient") || args.contains("net.minecraft.client.main.Main")
            if !game && !qa {
                // Launcher Java-version probes keep their exact stdin/exit behavior.
                let argv = ([java] + args).map { strdup($0) } + [nil]
                argv.withUnsafeBufferPointer { _ = execv(java, $0.baseAddress!) }
                fail("The configured Java executable could not run.")
            }
            let own = URL(fileURLWithPath: CommandLine.arguments[0]).standardizedFileURL
            let host = own.deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("Resources/Tung Tung Minecraft.app")
            guard fm.fileExists(atPath: host.appendingPathComponent("Contents/MacOS/TungTungVoiceHost").path) else { fail("The bundled microphone helper is missing. Reinstall Tung Tung Client.") }
            try privateDirectory(support); try privateDirectory(launchRoot)
            let folder = launchRoot.appendingPathComponent(UUID().uuidString)
            try privateDirectory(folder)
            cleanupFolder = folder; atexit(cleanup)
            defer { try? fm.removeItem(at: folder) }
            var channels: [(Int32, FileHandle)] = []
            for (name, output) in [("stdout", FileHandle.standardOutput), ("stderr", FileHandle.standardError)] {
                let path = folder.appendingPathComponent(name).path
                guard mkfifo(path, 0o600) == 0 else { fail("Could not create a private Java output channel.") }
                let fd = open(path, O_RDWR | O_NONBLOCK | O_CLOEXEC)
                guard fd >= 0 else { fail("Could not open the private Java output channel.") }
                channels.append((fd, output))
            }
            defer { for (fd, _) in channels { close(fd) } }
            let request: [String: Any] = ["java": java, "arguments": args, "cwd": fm.currentDirectoryPath,
                "environment": ProcessInfo.processInfo.environment, "parentPID": getpid(), "requestPermission": !qa]
            let requestURL = folder.appendingPathComponent("request.json")
            try privateWrite(try json(request), requestURL)
            signal(SIGTERM, interrupt); signal(SIGINT, interrupt); signal(SIGHUP, interrupt); signal(SIGPIPE, SIG_IGN)
            let opener = Process(); opener.executableURL = URL(fileURLWithPath: "/usr/bin/open")
            opener.arguments = ["-n", "-W", "-a", host.path, "--args", "--handoff", requestURL.path]
            opener.standardOutput = FileHandle.nullDevice; opener.standardError = FileHandle.nullDevice
            try opener.run()
            let startedAt = Date(); var cancelled = false; var hostPID: Int32?; var lostSince: Date?
            var buffer = [UInt8](repeating: 0, count: 65536)
            func drain() {
                for (fd, output) in channels {
                    while true {
                        let size = read(fd, &buffer, buffer.count)
                        if size <= 0 { break }
                        output.write(Data(buffer.prefix(size)))
                    }
                }
            }
            while true {
                drain()
                let resultURL = folder.appendingPathComponent("result.json")
                if fm.fileExists(atPath: resultURL.path), let result = try? readObject(resultURL), let code = result["exitCode"] as? Int32 {
                    drain()
                    if let error = result["error"] as? String { FileHandle.standardError.write(Data(("[Tung Tung] " + error + "\n").utf8)) }
                    exit(code)
                }
                if interrupted != 0 && !cancelled {
                    try privateWrite(Data(), folder.appendingPathComponent("cancel")); cancelled = true
                }
                if hostPID == nil, let started = try? readObject(folder.appendingPathComponent("started.json")) {
                    hostPID = started["hostPID"] as? Int32
                }
                if let hostPID, kill(hostPID, 0) != 0 {
                    if lostSince == nil { lostSince = Date() }
                    if Date().timeIntervalSince(lostSince!) > 0.5 {
                        fail("The microphone helper exited before Java completed. Reapply the microphone fix in the client.")
                    }
                } else if hostPID == nil && !opener.isRunning && Date().timeIntervalSince(startedAt) > 2 {
                    fail("macOS could not open the microphone helper. Reapply the microphone fix in the client.")
                }
                if !fm.fileExists(atPath: folder.appendingPathComponent("started.json").path) && Date().timeIntervalSince(startedAt) > 120 {
                    fail("macOS did not open the microphone helper. Reapply the microphone fix in the client.")
                }
                Thread.sleep(forTimeInterval: 0.02)
            }
        } catch { fail("Could not start the microphone-enabled runtime. Open Tung Tung Client → Microphone → Apply microphone fix.") }
    }
}
#endif
