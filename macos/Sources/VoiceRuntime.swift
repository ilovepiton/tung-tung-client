import AppKit
import Foundation
import Darwin

enum VoiceRuntime {
    static let support = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Library/Application Support/Tung Tung Client")
    static var configURL: URL { support.appendingPathComponent("voice-runtime.json") }
    static var shim: URL { Bundle.main.bundleURL.appendingPathComponent("Contents/MacOS/TungTungJava") }
    private static let fm = FileManager.default
    private static func error(_ message: String) -> Error { NativeBridgeError.unavailable(message) }
    static func status(root: URL = FabricInstaller.defaultMinecraftDirectory) -> [String: Any] {
        var result: [String: Any] = ["installed": false, "permission": "notDetermined"]
        if let bytes = try? Data(contentsOf: root.appendingPathComponent("launcher_profiles.json")),
           let range = try? JSONProfilesRange.find(in: bytes),
           let profiles = try? JSONSerialization.jsonObject(with: bytes.subdata(in: range)) as? [String: Any] {
            result["installed"] = profiles.values.contains { value in
                guard let profile = value as? [String: Any] else { return false }
                return profile["javaDir"] as? String == shim.path && profile["lastVersionId"] as? String == FabricInstaller.versionId
            } && fm.fileExists(atPath: configURL.path)
        }
        if let bytes = try? Data(contentsOf: support.appendingPathComponent("voice-last-launch.json")),
           let last = try? JSONSerialization.jsonObject(with: bytes) as? [String: Any] {
            result["permission"] = last["permission"]
            if let pid = last["gamePID"] as? Int32 { result["runtimeActive"] = kill(pid, 0) == 0 }
        }
        return result
    }
    static func apply(root: URL = FabricInstaller.defaultMinecraftDirectory) throws -> String {
        guard NSRunningApplication.runningApplications(withBundleIdentifier: "com.mojang.minecraftlauncher").isEmpty else {
            throw error("Quit Minecraft Launcher, then click Apply microphone fix again. You can leave the current game open until you are ready to restart it.")
        }
        guard fm.isExecutableFile(atPath: shim.path),
              fm.fileExists(atPath: Bundle.main.resourceURL!.appendingPathComponent("Tung Tung Minecraft.app/Contents/MacOS/TungTungVoiceHost").path) else {
            throw error("The bundled Mac microphone runtime is missing. Reinstall the complete Tung Tung Client app.")
        }
        let target = root.appendingPathComponent("launcher_profiles.json")
        try FabricInstaller.safePath(target)
        let original = try Data(contentsOf: target)
        let range = try JSONProfilesRange.find(in: original)
        guard var profiles = try JSONSerialization.jsonObject(with: original.subdata(in: range)) as? [String: Any] else { throw error("Minecraft Launcher profiles could not be read.") }
        let keys = profiles.keys.filter { key in
            guard let entry = profiles[key] as? [String: Any] else { return false }
            return entry["name"] as? String == FabricInstaller.profileName
                && entry["lastVersionId"] as? String == FabricInstaller.versionId
                && entry["gameDir"] as? String == root.appendingPathComponent("tun-tun-26.3").path
        }
        guard !keys.isEmpty else { throw error("Install the Tung Tung Fabric core in Control center first, then apply the microphone fix.") }
        let oldJava = keys.compactMap { (profiles[$0] as? [String: Any])?["javaDir"] as? String }.first { $0 != shim.path }
        let saved = (try? JSONSerialization.jsonObject(with: Data(contentsOf: configURL))) as? [String: Any]
        let candidates = [oldJava, saved?["java"] as? String].compactMap { $0 }.map { URL(fileURLWithPath: $0) }
        var java = candidates.first { $0.path.hasSuffix("/bin/java") && fm.isExecutableFile(atPath: $0.path) }
        if java == nil {
            let runtime = root.appendingPathComponent("runtime/java-runtime-epsilon")
            java = (fm.enumerator(at: runtime, includingPropertiesForKeys: [.isRegularFileKey])?.allObjects as? [URL])?.first {
                $0.path.hasSuffix("/jre.bundle/Contents/Home/bin/java") && fm.isExecutableFile(atPath: $0.path)
            }
        }
        guard let java else { throw error("Launch Minecraft 26.3 once so its Java runtime downloads, then apply the microphone fix.") }
        for key in keys {
            var entry = profiles[key] as! [String: Any]
            entry["javaDir"] = shim.path; profiles[key] = entry
        }
        var updated = original
        updated.replaceSubrange(range, with: try JSONSerialization.data(withJSONObject: profiles, options: [.sortedKeys]))
        _ = try JSONProfilesRange.find(in: updated)
        try fm.createDirectory(at: support, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        try FabricInstaller.safePath(support)
        try fm.setAttributes([.posixPermissions: 0o700], ofItemAtPath: support.path)
        let backup = support.appendingPathComponent("voice-backups/\(UUID().uuidString)")
        try fm.createDirectory(at: backup, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        let oldConfig = try? Data(contentsOf: configURL)
        try FabricInstaller.atomicWrite(original, to: backup.appendingPathComponent("launcher_profiles.original.json"), permissions: 0o600)
        if let oldConfig { try FabricInstaller.atomicWrite(oldConfig, to: backup.appendingPathComponent("voice-runtime.original.json"), permissions: 0o600) }
        let config = try JSONSerialization.data(withJSONObject: ["java": java.path, "version": 1], options: [.sortedKeys])
        guard try Data(contentsOf: target) == original else { throw error("Minecraft Launcher changed its profiles. Quit it and try again.") }
        do {
            try FabricInstaller.atomicWrite(config, to: configURL, permissions: 0o600)
            try FabricInstaller.atomicWrite(updated, to: target, permissions: 0o600)
        } catch {
            if let oldConfig { try? FabricInstaller.atomicWrite(oldConfig, to: configURL, permissions: 0o600) }
            else { try? fm.removeItem(at: configURL) }
            throw error
        }
        return "Microphone fix applied. Restart Minecraft Launcher, select Tung Tung Client 26.3 and Play. Allow Tung Tung Minecraft to use the microphone when macOS asks, then press V in-game."
    }
}
