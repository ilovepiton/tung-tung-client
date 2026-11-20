import Foundation
import CryptoKit

/// Selects an installed, independent engine. Never merges mods or changes their bytes.
enum EngineModes {
    static let anubisSHA256 = "e60968d360d57e8cfcd68bb127f6f56c7801ddebcff331827102596a209b15f1"
    struct Selection {
        let id: String
        let name: String
        let gameDirectory: URL
    }
    static func error(_ message: String) -> NSError {
        NSError(domain: "TungTungEngine", code: 1, userInfo: [NSLocalizedDescriptionKey: message])
    }
    static func selection(_ mode: String, root: URL) throws -> Selection {
        guard mode == "anubis" || mode == "tung" else { throw error("Choose Tung Tung or Anubis.") }
        let file = root.appendingPathComponent("launcher_profiles.json")
        try FabricInstaller.safePath(file)
        let bytes = try Data(contentsOf: file)
        let range = try JSONProfilesRange.find(in: bytes)
        let profiles = try JSONSerialization.jsonObject(with: bytes.subdata(in: range)) as? [String: [String: Any]] ?? [:]
        let version = mode == "anubis" ? "fabric-loader-0.20.1-1.21.11" : FabricInstaller.versionId
        let directory = root.appendingPathComponent(mode == "anubis" ? "anubis-1.21.11" : "tun-tun-26.3").standardizedFileURL
        guard let match = profiles.sorted(by: { $0.key < $1.key }).first(where: {
            $0.value["lastVersionId"] as? String == version && $0.value["gameDir"] as? String == directory.path
        }) else { throw error("The \(mode == "anubis" ? "Anubis 1.21.11" : "Tung Tung 26.3") profile is not installed in this Minecraft folder.") }
        if mode == "anubis" {
            let jar = directory.appendingPathComponent("mods/Anubis_Client_1.0.jar")
            try FabricInstaller.safePath(jar)
            let digest = SHA256.hash(data: try Data(contentsOf: jar)).map { String(format: "%02x", $0) }.joined()
            guard digest == anubisSHA256 else { throw error("The installed Anubis file differs from the original download. No files were changed.") }
        }
        return Selection(id: match.key, name: match.value["name"] as? String ?? mode, gameDirectory: directory)
    }
    static func select(_ mode: String, root: URL, backupRoot: URL) throws -> Selection {
        let selected = try selection(mode, root: root)
        let file = root.appendingPathComponent("launcher_profiles.json")
        let original = try Data(contentsOf: file)
        let range = try JSONProfilesRange.find(in: original)
        guard var profiles = try JSONSerialization.jsonObject(with: original.subdata(in: range)) as? [String: [String: Any]],
              var profile = profiles[selected.id] else { throw error("Launcher profiles changed. Try again.") }
        // Record recent use. The Launcher may retain a separate cached selection; UI instructs the user to choose this profile.
        profile["lastUsed"] = ISO8601DateFormatter().string(from: Date())
        profiles[selected.id] = profile
        var updated = original
        updated.replaceSubrange(range, with: try JSONSerialization.data(withJSONObject: profiles, options: [.sortedKeys]))
        let backup = backupRoot.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: backup, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        try FabricInstaller.atomicWrite(original, to: backup.appendingPathComponent("launcher_profiles.json"), permissions: 0o600)
        guard try Data(contentsOf: file) == original else { throw error("Minecraft Launcher changed its profiles. Try again after it closes.") }
        try FabricInstaller.atomicWrite(updated, to: file, permissions: 0o600)
        return selected
    }
    static func runSelfTests() throws -> [String] {
        let fm = FileManager.default
        let root = fm.homeDirectoryForCurrentUser.appendingPathComponent("Library/Caches/tung-engine-test-" + UUID().uuidString)
        try fm.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: root) }
        let file = root.appendingPathComponent("launcher_profiles.json")
        let fixture: [String: Any] = ["profiles": ["keep": ["name": "Keep", "javaArgs": "unchanged"], "tung": ["name": "Tung Tung", "lastVersionId": FabricInstaller.versionId, "gameDir": root.appendingPathComponent("tun-tun-26.3").path]], "settings": ["keep": true], "selectedUser": ["profile": "unchanged"]]
        let original = try JSONSerialization.data(withJSONObject: fixture, options: [.sortedKeys])
        try original.write(to: file)
        var checks: [String] = []
        var rejected = false
        do { _ = try selection("invalid", root: root) } catch { rejected = true }
        guard rejected, try Data(contentsOf: file) == original else { throw error("Invalid mode was accepted or changed files") }
        checks.append("Invalid mode leaves files unchanged")
        rejected = false
        do { _ = try selection("anubis", root: root) } catch { rejected = true }
        guard rejected, try Data(contentsOf: file) == original else { throw error("Missing Anubis was accepted or changed files") }
        checks.append("Missing original Anubis leaves files unchanged")
        let selected = try select("tung", root: root, backupRoot: root.appendingPathComponent("backups"))
        guard selected.id == "tung" else { throw error("Wrong profile selected") }
        let updated = try JSONSerialization.jsonObject(with: Data(contentsOf: file)) as! [String: Any]
        let profiles = updated["profiles"] as! [String: [String: Any]]
        guard NSDictionary(dictionary: profiles["keep"]!) == NSDictionary(dictionary: (fixture["profiles"] as! [String: [String: Any]])["keep"]!),
              NSDictionary(dictionary: updated["settings"] as! [String: Any]) == NSDictionary(dictionary: fixture["settings"] as! [String: Any]),
              NSDictionary(dictionary: updated["selectedUser"] as! [String: Any]) == NSDictionary(dictionary: fixture["selectedUser"] as! [String: Any]),
              profiles["tung"]?["lastUsed"] is String else { throw error("Unrelated settings changed") }
        checks.append("Only target lastUsed changes; other profiles and opaque account settings preserved")
        let backups = try fm.contentsOfDirectory(at: root.appendingPathComponent("backups"), includingPropertiesForKeys: nil)
        guard backups.count == 1, try Data(contentsOf: backups[0].appendingPathComponent("launcher_profiles.json")) == original else { throw error("Backup mismatch") }
        checks.append("Exact original profile backup retained")
        return checks
    }

}
