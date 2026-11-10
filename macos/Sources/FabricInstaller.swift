import Foundation
import AppKit
import CryptoKit
import CoreFoundation
import Darwin

struct InstallResult {
    let profileId: String
    let versionId: String
    let gameDirectory: URL
    let backupsDirectory: URL
    let summary: String
}

enum FabricInstaller {
    static let versionId = "fabric-loader-0.19.5-26.3"
    static let profileName = "Tung Tung Client 26.3"
    static let defaultMinecraftDirectory = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Library/Application Support/minecraft", isDirectory: true)
    static let officialLauncherDownloadURL = URL(string: "https://www.minecraft.net/en-us/download")!
    private static let fm = FileManager.default
    private static let baseMods = Set(["tung-tung-bridge-1.30.3.jar", "fabric-api-0.162.0+26.3.jar", "client_maps-1.3.4.jar", "map-in-slot-3.5.0.jar"])
    private static let voiceChatMod = "voicechat-fabric-2.6.24+26.3.jar"
    private static let voiceChatSHA512 = "414eb51967305fe7740d34016bc8e3e4e2fa1590e1278ad06bcdfa0687f65007b825d345dc09f79a992704a51ec93baf01fe727e0bbc32fdf506828b5a65d85a"

    struct InstallerError: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }
    private static func problem(_ text: String) -> InstallerError { InstallerError(message: text) }
    private struct FilePlan { let target: URL; let data: Data; let original: Data? }
    private struct Undo { let target: URL; let original: Data?; let expected: Data?; let backup: URL? }
    private struct Payload { let profile: Data; let mods: [String: Data] }

    static func install(minecraftDirectory: URL, resources: URL) throws -> InstallResult {
        try perform(minecraftDirectory: minecraftDirectory, resources: resources,
                    processCheck: { try refuseRunningMinecraft(in: minecraftDirectory) }, failAfter: nil, downloadExtras:true)
    }

    private static func perform(minecraftDirectory: URL, resources: URL, processCheck: () throws -> Void, failAfter: Int?, downloadExtras:Bool=false) throws -> InstallResult {
        try processCheck()
        let root = try checkedRoot(minecraftDirectory)
        let payload = try loadPayload(resources: resources)
        var extraMods:[String:Data]=[:]
        if downloadExtras {
            let manifest=resources.appendingPathComponent("optional-mods.json")
            if fm.fileExists(atPath:manifest.path) {
                guard let entries=try JSONSerialization.jsonObject(with:fileData(manifest,maximum:32768)) as? [[String:String]],entries.count<=16 else {throw problem("Invalid optional download manifest")}
                for entry in entries {
                    let isPack=entry["kind"]=="resourcepack"
                    guard entry["kind"]==nil||entry["kind"]=="mod"||isPack,let name=entry["file"],name.hasSuffix(isPack ? ".zip" : ".jar"),!name.contains("/"),!name.contains(".."),let source=entry["url"],let url=URL(string:source),url.scheme=="https",url.host=="cdn.modrinth.com"||(isPack&&url.host=="packsmc.com"&&url.path.hasPrefix("/api/packs/")&&url.path.hasSuffix("/download-file")),let hash=entry["sha512"] else {throw problem("Invalid official download")}
                    let relative=(isPack ? "resourcepacks/" : "mods/")+name
                    let existing=root.appendingPathComponent("tun-tun-26.3/"+relative)
                    var bytes:Data
                    if let current=try existingData(existing),SHA512.hash(data:current).map({String(format:"%02x",$0)}).joined()==hash {bytes=current}
                    else {bytes=try Data(contentsOf:url)}
                    guard bytes.count<(isPack ? 80 : 32)*1024*1024,SHA512.hash(data:bytes).map({String(format:"%02x",$0)}).joined()==hash else {throw problem("Downloaded file checksum mismatch")}
                    extraMods[relative]=bytes
                }
                try processCheck()
            }
        }
        let profilesURL = root.appendingPathComponent("launcher_profiles.json")
        try safePath(profilesURL)
        guard fm.fileExists(atPath: profilesURL.path) else {
            throw problem("Minecraft Launcher profiles were not found. Open the official Launcher once, close it, and choose its Minecraft folder.")
        }
        let originalProfiles = try fileData(profilesURL, maximum: 8 * 1024 * 1024)
        let profilesRange = try JSONProfilesRange.find(in: originalProfiles)
        guard let decoded = try JSONSerialization.jsonObject(with: originalProfiles.subdata(in: profilesRange)) as? [String: Any] else {
            throw problem("Launcher profiles are invalid. Installation stopped without changing them.")
        }
        let gameDirectory = root.appendingPathComponent("tun-tun-26.3", isDirectory: true)
        var profiles = decoded
        let matching = profiles.keys.filter { key in
            guard let entry = profiles[key] as? [String: Any], let directory = entry["gameDir"] as? String,
                  let name = entry["name"] as? String, let version = entry["lastVersionId"] as? String else { return false }
            return URL(fileURLWithPath: directory).path == gameDirectory.path
                && version == versionId && name.filter { !$0.isWhitespace }.lowercased() == profileName.filter { !$0.isWhitespace }.lowercased()
        }.sorted()
        let profileID: String
        if matching.contains("tun-tun-26.3") { profileID = "tun-tun-26.3" }
        else if matching.contains("tun-tun-live-26.3") { profileID = "tun-tun-live-26.3" }
        else if let old = matching.first { profileID = old }
        else {
            profileID = "tun-tun-26.3"
            guard profiles[profileID] == nil else { throw problem("The reserved Tung Tung profile ID is already used by another profile. No profiles were changed.") }
        }
        var entry = profiles[profileID] as? [String: Any] ?? [:]
        if entry["created"] == nil { entry["created"] = ISO8601DateFormatter().string(from: Date()) }
        entry["name"] = profileName; entry["type"] = "custom"; entry["lastVersionId"] = versionId
        entry["gameDir"] = gameDirectory.path; entry["icon"] = "Furnace"
        profiles[profileID] = entry
        let profileBytes = try JSONSerialization.data(withJSONObject: profiles, options: [.sortedKeys])
        var merged = originalProfiles
        merged.replaceSubrange(profilesRange, with: profileBytes)
        // Only the profiles value is decoded. Account/settings values stay untouched opaque bytes.
        _ = try JSONProfilesRange.find(in: merged)

        let versionURL = root.appendingPathComponent("versions/\(versionId)/\(versionId).json")
        let modsDirectory = gameDirectory.appendingPathComponent("mods", isDirectory: true)
        var plans = [FilePlan(target: versionURL, data: payload.profile, original: try existingData(versionURL))]
        for name in payload.mods.keys.sorted() {
            let target = modsDirectory.appendingPathComponent(name)
            plans.append(FilePlan(target: target, data: payload.mods[name]!, original: try existingData(target)))
        }
        for (relative,bytes) in extraMods {let target=gameDirectory.appendingPathComponent(relative);plans.append(FilePlan(target:target,data:bytes,original:try existingData(target)))}
        let library=resources.appendingPathComponent("library",isDirectory:true)
        let manifestURL=library.appendingPathComponent("manifest.json")
        if fm.fileExists(atPath:manifestURL.path) {
            let document=try JSONSerialization.jsonObject(with:fileData(manifestURL,maximum:1024*1024)) as? [String:Any]
            guard let entries=document?["files"] as? [[String:Any]],entries.count<=64 else {throw problem("Invalid cosmetic library manifest.")}
            var seen=Set<String>()
            for entry in entries {
                guard let relative=entry["path"] as? String,let hash=entry["sha256"] as? String,
                      relative.hasPrefix("resourcepacks/"),relative.split(separator:"/").count==2,
                      relative.hasSuffix(".zip"),!relative.contains(".."),seen.insert(relative).inserted else {throw problem("Invalid cosmetic library path.")}
                let source=library.appendingPathComponent(relative);try safePath(source)
                let bytes=try fileData(source,maximum:128*1024*1024)
                guard SHA256.hash(data:bytes).map({String(format:"%02x",$0)}).joined()==hash else {throw problem("Cosmetic library checksum mismatch.")}
                let target=gameDirectory.appendingPathComponent(relative)
                plans.append(FilePlan(target:target,data:bytes,original:try existingData(target)))
            }
            let notices=library.appendingPathComponent("THIRD-PARTY-NOTICES.txt")
            if fm.fileExists(atPath:notices.path){let target=gameDirectory.appendingPathComponent("config/Tunk-Library-Credits.txt");plans.append(FilePlan(target:target,data:try fileData(notices,maximum:1024*1024),original:try existingData(target)))}
        }
        plans.append(FilePlan(target: profilesURL, data: merged, original: originalProfiles))
        var removals: [(URL, Data)] = []
        let retiredPack = gameDirectory.appendingPathComponent("resourcepacks/TUNG-White-Glint.zip")
        if let original = try existingData(retiredPack) { removals.append((retiredPack, original)) }
        let optionsURL = gameDirectory.appendingPathComponent("options.txt")
        if let original = try existingData(optionsURL), let cleaned = try retiringGlintOptions(original) {
            plans.append(FilePlan(target: optionsURL, data: cleaned, original: original))
        }
        try safePath(modsDirectory)
        if fm.fileExists(atPath: modsDirectory.path) {
            for file in try fm.contentsOfDirectory(at: modsDirectory, includingPropertiesForKeys: nil) {
                let name = file.lastPathComponent
                let managed = isOldClientMod(name) || isOldCompatibilityMod(name) || (payload.mods[voiceChatMod] != nil && isOldVoiceChatMod(name))
                let plannedExtra = extraMods.keys.contains("mods/\(name)")
                if managed && payload.mods[name] == nil && !plannedExtra {
                    removals.append((file, try fileData(file, maximum: 128 * 1024 * 1024)))
                }
            }
        }
        // Validate every future path before the first mutation.
        for plan in plans { try safePath(plan.target) }
        for removal in removals { try safePath(removal.0) }
        let stamp = ISO8601DateFormatter().string(from: Date()).replacingOccurrences(of: ":", with: "-")
        let backupDirectory = root.appendingPathComponent("tun-tun-install-backups/\(stamp)-\(UUID().uuidString)", isDirectory: true)
        try safePath(backupDirectory)
        var undo: [Undo] = [], createdDirectories: [URL] = [], count = 0
        func ensureDirectory(_ directory: URL) throws {
            try safePath(directory)
            if fm.fileExists(atPath: directory.path) {
                let attributes = try fm.attributesOfItem(atPath: directory.path)
                guard attributes[.type] as? FileAttributeType == .typeDirectory else { throw problem("A file occupies a required installation directory.") }
                return
            }
            if directory.path != "/" { try ensureDirectory(directory.deletingLastPathComponent()) }
            try fm.createDirectory(at: directory, withIntermediateDirectories: false, attributes: [.posixPermissions: 0o700])
            createdDirectories.append(directory)
        }
        func record(_ target: URL, original: Data?, expected: Data?) throws {
            var backup: URL? = nil
            if let original {
                try ensureDirectory(backupDirectory)
                backup = backupDirectory.appendingPathComponent("\(undo.count)-\(target.lastPathComponent).original")
                try atomicWrite(original, to: backup!, permissions: 0o600)
            }
            undo.append(Undo(target: target, original: original, expected: expected, backup: backup))
        }
        func injectedFailure() throws {
            count += 1
            if let failAfter, count == failAfter { throw problem("Installer self-test injected a transaction failure.") }
        }
        do {
            for removal in removals {
                guard try existingData(removal.0) == removal.1 else { throw problem("An old client mod changed during installation. Close Minecraft and try again.") }
                try record(removal.0, original: removal.1, expected: nil)
                try fm.removeItem(at: removal.0); try injectedFailure()
            }
            for plan in plans where plan.original != plan.data {
                if plan.target == profilesURL { try processCheck() }
                guard try existingData(plan.target) == plan.original else { throw problem("An installation file changed during installation. Close the Launcher and try again.") }
                try record(plan.target, original: plan.original, expected: plan.data)
                try ensureDirectory(plan.target.deletingLastPathComponent())
                try atomicWrite(plan.data, to: plan.target, permissions: plan.target == profilesURL ? 0o600 : 0o644)
                try injectedFailure()
            }
            try processCheck()
            try ensureDirectory(backupDirectory)
            let map = undo.map { item -> [String: Any] in
                ["target": String(item.target.path.dropFirst(root.path.count + 1)), "existed": item.original != nil,
                 "backup": item.backup?.lastPathComponent ?? ""]
            }
            try atomicWrite(try JSONSerialization.data(withJSONObject: map, options: [.prettyPrinted, .sortedKeys]),
                            to: backupDirectory.appendingPathComponent("restore-map.json"), permissions: 0o600)
        } catch {
            var rollbackFailed = false
            for item in undo.reversed() {
                do {
                    let current = try existingData(item.target)
                    if current == item.original { continue }
                    guard current == item.expected else { throw problem("A changed file could not be restored safely.") }
                    if let original = item.original { try atomicWrite(original, to: item.target, permissions: 0o600) }
                    else if fm.fileExists(atPath: item.target.path) { try fm.removeItem(at: item.target) }
                } catch { rollbackFailed = true }
            }
            for directory in createdDirectories.reversed() where !directory.path.hasPrefix(backupDirectory.path) {
                if (try? fm.contentsOfDirectory(atPath: directory.path).isEmpty) == true { try? fm.removeItem(at: directory) }
            }
            if rollbackFailed { throw problem("Installation stopped; a concurrently changed file could not be restored. Original files are in \(backupDirectory.path).") }
            throw error
        }
        return InstallResult(profileId: profileID, versionId: versionId, gameDirectory: gameDirectory,
                             backupsDirectory: backupDirectory,
                             summary: "Installed Minecraft 26.3 + Fabric 0.19.5. Open Minecraft Launcher, select \(profileName), and press Play. The Launcher downloads Minecraft and Java if needed.")
    }

    private static func retiringGlintOptions(_ data: Data) throws -> Data? {
        guard let text = String(data: data, encoding: .utf8) else { throw problem("Minecraft options are not valid UTF-8.") }
        var changed = false
        let lines = try text.components(separatedBy: "\n").map { line -> String in
            guard let colon = line.firstIndex(of: ":"), ["resourcePacks", "incompatibleResourcePacks"].contains(String(line[..<colon])) else { return line }
            let raw = String(line[line.index(after: colon)...])
            guard let bytes = raw.data(using: .utf8), let ids = try JSONSerialization.jsonObject(with: bytes) as? [String] else { throw problem("Minecraft resource-pack options are invalid.") }
            let next = ids.filter { $0 != "file/TUNG-White-Glint.zip" }
            guard next != ids else { return line }
            changed = true
            let encoded = try JSONSerialization.data(withJSONObject: next, options: [.fragmentsAllowed, .withoutEscapingSlashes])
            return String(line[...colon]) + String(decoding: encoded, as: UTF8.self) + (line.hasSuffix("\r") ? "\r" : "")
        }
        return changed ? Data(lines.joined(separator: "\n").utf8) : nil
    }

    private static func checkedRoot(_ root: URL) throws -> URL {
        guard root.isFileURL, !root.pathComponents.contains(".."), !root.pathComponents.contains(".") else { throw problem("Choose a normal local Minecraft folder without path traversal.") }
        try safePath(root)
        // URL.standardizedFileURL rewrites /private/var to the system /var symlink on macOS.
        // Keep the already-validated physical path instead of reintroducing a symlink.
        let normalized = URL(fileURLWithPath: root.path, isDirectory: true)
        guard let type = try? fm.attributesOfItem(atPath: normalized.path)[.type] as? FileAttributeType,
              type == .typeDirectory else { throw problem("Choose the existing Minecraft Launcher folder.") }
        return normalized
    }
    static func safePath(_ url: URL) throws {
        guard url.isFileURL, !url.pathComponents.contains(".."), !url.pathComponents.contains(".") else { throw problem("Unsafe installation path.") }
        var path = URL(fileURLWithPath: "/", isDirectory: true)
        for component in url.pathComponents.dropFirst() {
            path.appendPathComponent(component)
            var info = stat()
            if lstat(path.path, &info) == 0 {
                if (info.st_mode & S_IFMT) == S_IFLNK { throw problem("Symbolic links are not allowed in installation or payload paths.") }
                if path.path != url.path && (info.st_mode & S_IFMT) != S_IFDIR { throw problem("An installation parent path is not a directory.") }
            } else if errno != ENOENT { throw problem("An installation path could not be checked.") }
        }
    }
    private static func existingData(_ url: URL) throws -> Data? {
        try safePath(url)
        if !fm.fileExists(atPath: url.path) { return nil }
        return try fileData(url, maximum: 128 * 1024 * 1024)
    }
    private static func fileData(_ url: URL, maximum: Int) throws -> Data {
        try safePath(url)
        let a = try fm.attributesOfItem(atPath: url.path)
        guard a[.type] as? FileAttributeType == .typeRegular, let bytes = a[.size] as? NSNumber, bytes.intValue <= maximum else { throw problem("A required installer file is invalid or too large.") }
        return try Data(contentsOf: url)
    }
    static func atomicWrite(_ bytes: Data, to target: URL, permissions: Int) throws {
        try safePath(target)
        let temp = target.deletingLastPathComponent().appendingPathComponent(".tuntun-\(UUID().uuidString).tmp")
        defer { try? fm.removeItem(at: temp) }
        try bytes.write(to: temp, options: [.withoutOverwriting])
        try fm.setAttributes([.posixPermissions: permissions], ofItemAtPath: temp.path)
        try safePath(target)
        guard Darwin.rename(temp.path, target.path) == 0 else { throw problem("A file could not be replaced atomically.") }
    }
    private static func isOldClientMod(_ name: String) -> Bool {
        name.hasSuffix(".jar") && ["tung-tung-bridge-", "tun-tun-bridge-", "amethyst-chunk-finder-", "fabric-api-", "client_maps-", "map-in-slot-"].contains(where: name.hasPrefix)
    }
    private static func isOldVoiceChatMod(_ name: String) -> Bool { name.hasPrefix("voicechat-fabric-") && name.hasSuffix(".jar") }
    private static func isOldCompatibilityMod(_ name: String) -> Bool { name.hasPrefix("ViaFabricPlus-") && name.hasSuffix(".jar") }
    private static func loadPayload(resources: URL) throws -> Payload {
        try safePath(resources)
        let base = resources.appendingPathComponent("payload", isDirectory: true)
        let manifest = try fileData(base.appendingPathComponent("manifest.json"), maximum: 1024 * 1024)
        guard let document = try JSONSerialization.jsonObject(with: manifest) as? [String: Any],
              document["minecraft"] as? String == "26.3", document["loader"] as? String == "0.19.5",
              document["version"] as? String == "1.30.3", let files = document["files"] as? [[String: Any]] else {
            throw problem("The bundled Minecraft/Fabric manifest is invalid. Download a fresh complete Mac ZIP.")
        }
        var withVoiceChat = false
        if let flag = document["simpleVoiceChat"] {
            guard let value = flag as? Bool, CFGetTypeID(flag as CFTypeRef) == CFBooleanGetTypeID() else {
                throw problem("The bundled voice chat manifest flag is invalid.")
            }
            withVoiceChat = value
        }
        let expectedMods = withVoiceChat ? baseMods.union([voiceChatMod]) : baseMods
        guard files.count == expectedMods.count + 1 else { throw problem("The bundled payload contains an unexpected file count.") }
        var values: [String: Data] = [:]
        let allowed = Set(["fabric-profile.json"] + expectedMods.map { "mods/\($0)" })
        for file in files {
            guard let path = file["path"] as? String, allowed.contains(path), values[path] == nil,
                  let digest = file["sha256"] as? String, digest.count == 64,
                  digest.allSatisfy({ $0.isHexDigit }), file["kind"] as? String == (path == "fabric-profile.json" ? "profile" : "mod") else {
                throw problem("The bundled payload contains an unexpected or duplicate path.")
            }
            let bytes = try fileData(base.appendingPathComponent(path), maximum: 128 * 1024 * 1024)
            let actual = SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined()
            guard actual == digest.lowercased() else { throw problem("A bundled payload checksum does not match. Download a fresh complete Mac ZIP.") }
            if path == "mods/\(voiceChatMod)" {
                let pinned = SHA512.hash(data: bytes).map { String(format: "%02x", $0) }.joined()
                guard pinned == voiceChatSHA512 else { throw problem("The bundled Simple Voice Chat file is not the supported pinned version.") }
            }
            values[path] = bytes
        }
        guard Set(values.keys) == allowed, let profileBytes = values["fabric-profile.json"],
              let profile = try JSONSerialization.jsonObject(with: profileBytes) as? [String: Any],
              profile["id"] as? String == versionId, profile["inheritsFrom"] as? String == "26.3",
              profile["mainClass"] as? String == "net.fabricmc.loader.impl.launch.knot.KnotClient",
              let libraries = profile["libraries"] as? [[String: Any]],
              libraries.contains(where: { $0["name"] as? String == "net.fabricmc:fabric-loader:0.19.5" }) else {
            throw problem("The bundled loader profile does not match Minecraft 26.3 / Fabric 0.19.5.")
        }
        return Payload(profile: profileBytes, mods: Dictionary(uniqueKeysWithValues: expectedMods.map { ($0, values["mods/\($0)"]!) }))
    }

    private static func openedMinecraftFiles(_ paths: String, root: URL) -> Bool {
        let prefix = root.path.hasSuffix("/") ? root.path : root.path + "/"
        return paths.split(separator: "\n").contains { entry in
            guard entry.hasPrefix("n") else { return false }
            let path = String(entry.dropFirst())
            return path.lowercased().contains("minecraft") || path.hasPrefix(prefix)
        }
    }
    private static func refuseRunningMinecraft(in root: URL) throws {
        if NSWorkspace.shared.runningApplications.contains(where: {
            ($0.bundleIdentifier ?? "").lowercased().contains("minecraft") || ($0.localizedName ?? "").lowercased().contains("minecraft")
        }) { throw problem("Close Minecraft and Minecraft Launcher before installing, then try again.") }
        // Read executable names only, never process arguments or access tokens.
        let names = try processOutput("/bin/ps", ["-axo", "pid=,comm="])
        for line in names.split(separator: "\n") {
            let fields = line.split(maxSplits: 1, whereSeparator: { $0.isWhitespace })
            guard fields.count == 2, let pid = Int(fields[0]) else { continue }
            let name = String(fields[1]).trimmingCharacters(in: .whitespaces)
            if URL(fileURLWithPath: name).lastPathComponent.lowercased().contains("minecraft") { throw problem("Close Minecraft and Minecraft Launcher before installing, then try again.") }
            if URL(fileURLWithPath: name).lastPathComponent.lowercased() == "java" {
                let paths = (try? processOutput("/usr/sbin/lsof", ["-a", "-p", String(pid), "-Fn"])) ?? ""
                if openedMinecraftFiles(paths, root: root) {
                    throw problem("Minecraft files are open in a Java process. Close Minecraft before installing.")
                }
            }
        }
    }
    private static func processOutput(_ executable: String, _ arguments: [String]) throws -> String {
        let process = Process(), pipe = Pipe()
        process.executableURL = URL(fileURLWithPath: executable); process.arguments = arguments
        process.standardOutput = pipe; process.standardError = FileHandle.nullDevice
        try process.run()
        let bytes = pipe.fileHandleForReading.readDataToEndOfFile(); process.waitUntilExit()
        guard bytes.count <= 2 * 1024 * 1024 else { throw problem("Running applications could not be checked safely.") }
        return String(data: bytes, encoding: .utf8) ?? ""
    }

    /// Explicit CLI tests use only a new temporary folder; no user installation or account data is accessed.
    static func runSelfTests(resources: URL) throws -> [String] {
        guard let resolved = Darwin.realpath(fm.temporaryDirectory.path, nil) else { throw problem("A temporary installer test directory could not be resolved.") }
        let temporary = URL(fileURLWithPath: String(cString: resolved), isDirectory: true)
        free(resolved)
        let root = temporary.appendingPathComponent("tuntun-installer-tests-\(UUID().uuidString)", isDirectory: true)
        try fm.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: root) }
        var passed: [String] = []
        func check(_ condition: @autoclosure () throws -> Bool, _ name: String) throws {
            guard try condition() else { throw problem("Installer self-test failed: \(name)") }; passed.append(name)
        }
        let sourcePayload = try loadPayload(resources: resources)
        func fixture(_ name: String) throws -> URL {
            let dir = root.appendingPathComponent(name, isDirectory: true); try fm.createDirectory(at: dir, withIntermediateDirectories: true)
            let json = #"{"profiles":{"ordinary":{"name":"Keep Me","lastVersionId":"26.3","javaArgs":"-Xmx4G"}},"settings":{"keep":"yes"},"accountOpaque":{"credential":"do-not-parse-test-sentinel"},"selectedUser":{"profile":"unchanged"}}"#
            try Data(json.utf8).write(to: dir.appendingPathComponent("launcher_profiles.json"))
            try fm.createDirectory(at: dir.appendingPathComponent("saves/KeepWorld"), withIntermediateDirectories: true)
            try Data("world-unchanged".utf8).write(to: dir.appendingPathComponent("saves/KeepWorld/level.dat"))
            return dir
        }
        func testInstall(_ dir: URL, fail: Int? = nil, payloadResources: URL? = nil, extras:Bool=false, check: () throws -> Void = {}) throws -> InstallResult {
            try perform(minecraftDirectory: dir, resources: payloadResources ?? resources, processCheck: check, failAfter: fail, downloadExtras:extras)
        }
        func payloadFixture(_ name: String, edit: (inout [String: Any], URL) throws -> Void) throws -> URL {
            let dir = root.appendingPathComponent(name, isDirectory: true)
            try fm.createDirectory(at: dir, withIntermediateDirectories: true)
            let payload = dir.appendingPathComponent("payload", isDirectory: true)
            try fm.copyItem(at: resources.appendingPathComponent("payload"), to: payload)
            let manifest = payload.appendingPathComponent("manifest.json")
            var document = try JSONSerialization.jsonObject(with: Data(contentsOf: manifest)) as! [String: Any]
            try edit(&document, payload)
            try JSONSerialization.data(withJSONObject: document, options: [.sortedKeys]).write(to: manifest)
            return dir
        }
        func backupContains(_ result: InstallResult, name: String, bytes: Data) throws -> Bool {
            let map = try JSONSerialization.jsonObject(with: Data(contentsOf: result.backupsDirectory.appendingPathComponent("restore-map.json"))) as! [[String: Any]]
            guard let item = map.first(where: { ($0["target"] as? String)?.hasSuffix("/" + name) == true }),
                  let backup = item["backup"] as? String, !backup.isEmpty else { return false }
            return try Data(contentsOf: result.backupsDirectory.appendingPathComponent(backup)) == bytes
        }
        let first = try fixture("preserve")
        let mods = first.appendingPathComponent("tun-tun-26.3/mods")
        try fm.createDirectory(at: mods, withIntermediateDirectories: true)
        try Data("old-bridge".utf8).write(to: mods.appendingPathComponent("tung-tung-bridge-1.7.0.jar"))
        try Data("other-mod".utf8).write(to: mods.appendingPathComponent("unrelated-mod.jar"))
        let oldVoiceName = "voicechat-fabric-2.6.23+26.3.jar", oldVoice = Data("old-voice-test-sentinel".utf8)
        try oldVoice.write(to: mods.appendingPathComponent(oldVoiceName))
        try Data("other-loader-voice".utf8).write(to: mods.appendingPathComponent("voicechat-neoforge-existing.jar"))
        let retired = first.appendingPathComponent("tun-tun-26.3/resourcepacks/TUNG-White-Glint.zip")
        try fm.createDirectory(at: retired.deletingLastPathComponent(), withIntermediateDirectories: true)
        let oldGlint = Data("retired-glint".utf8)
        try oldGlint.write(to: retired)
        let options = first.appendingPathComponent("tun-tun-26.3/options.txt")
        let oldOptions = Data("gamma:0.5\r\nresourcePacks:[\"vanilla\",\"file/TUNG-White-Glint.zip\",\"file/personal.zip\"]\r\nincompatibleResourcePacks:[\"file/TUNG-White-Glint.zip\"]\r\n".utf8)
        try oldOptions.write(to: options)
        let result = try testInstall(first,extras:true)
        try check(!fm.fileExists(atPath: retired.path) && (try backupContains(result, name: "TUNG-White-Glint.zip", bytes: oldGlint)), "retired White Glint removed with recoverable backup")
        let cleanedOptions = String(decoding: try Data(contentsOf: options), as: UTF8.self)
        try check(cleanedOptions == "gamma:0.5\r\nresourcePacks:[\"vanilla\",\"file/personal.zip\"]\r\nincompatibleResourcePacks:[]\r\n", "retired pack selections removed without changing other options or line endings")
        try check(try backupContains(result, name: "options.txt", bytes: oldOptions), "original pack selections backed up")
        let retiredRollback = try fixture("retired-rollback")
        let rollbackGlint = retiredRollback.appendingPathComponent("tun-tun-26.3/resourcepacks/TUNG-White-Glint.zip")
        try fm.createDirectory(at: rollbackGlint.deletingLastPathComponent(), withIntermediateDirectories: true)
        try oldGlint.write(to: rollbackGlint)
        do { _ = try testInstall(retiredRollback, fail: 1) } catch {}
        try check(try Data(contentsOf: rollbackGlint) == oldGlint, "rollback restores retired addon on interrupted install")
        try check(fm.fileExists(atPath:first.appendingPathComponent("tun-tun-26.3/resourcepacks/Tung-DrDonut-Meow.zip").path),"pinned optional Meow pack downloads to resourcepacks")
        let installed = try fileData(first.appendingPathComponent("launcher_profiles.json"), maximum: 8 * 1024 * 1024)
        let all = try JSONSerialization.jsonObject(with: installed) as! [String: Any]
        let profiles = all["profiles"] as! [String: Any]
        try check(profiles["ordinary"] as? [String: String] == ["name":"Keep Me","lastVersionId":"26.3","javaArgs":"-Xmx4G"], "unrelated profiles preserved")
        try check(String(data: installed, encoding: .utf8)!.contains(#""accountOpaque":{"credential":"do-not-parse-test-sentinel"}"#), "opaque account bytes preserved")
        try check(try Data(contentsOf: first.appendingPathComponent("saves/KeepWorld/level.dat")) == Data("world-unchanged".utf8), "world untouched")
        try check(try Data(contentsOf: mods.appendingPathComponent("unrelated-mod.jar")) == Data("other-mod".utf8), "unrelated mod untouched")
        try check(!fm.fileExists(atPath: mods.appendingPathComponent("tung-tung-bridge-1.7.0.jar").path), "old bridge backed up and removed")
        try check(try Data(contentsOf: mods.appendingPathComponent("tung-tung-bridge-1.30.3.jar")) == sourcePayload.mods["tung-tung-bridge-1.30.3.jar"], "real bridge installed exactly")
        try check(try Data(contentsOf: mods.appendingPathComponent("voicechat-neoforge-existing.jar")) == Data("other-loader-voice".utf8), "other loader voice mod preserved")
        if let voice = sourcePayload.mods[voiceChatMod] {
            try check(try Data(contentsOf: mods.appendingPathComponent(voiceChatMod)) == voice, "real pinned voice chat installed exactly")
            try check(!fm.fileExists(atPath: mods.appendingPathComponent(oldVoiceName).path)
                      && (try backupContains(result, name: oldVoiceName, bytes: oldVoice)), "old Fabric voice chat backed up and replaced")
        } else {
            try check(try Data(contentsOf: mods.appendingPathComponent(oldVoiceName)) == oldVoice, "base installer preserves existing voice chat")
        }
        let again = try testInstall(first)
        try check(again.profileId == result.profileId, "repeat install reuses existing profile")
        let existing = try fixture("existing-profile")
        let oldProfile = ["profiles": ["tun-tun-live-26.3": ["name": profileName, "gameDir": existing.appendingPathComponent("tun-tun-26.3").path,
                         "lastVersionId": versionId, "javaArgs": "-Xmx6G"]]]
        try JSONSerialization.data(withJSONObject: oldProfile).write(to: existing.appendingPathComponent("launcher_profiles.json"))
        let reused = try testInstall(existing)
        let reusedJSON = try JSONSerialization.jsonObject(with: Data(contentsOf: existing.appendingPathComponent("launcher_profiles.json"))) as! [String: Any]
        let reusedProfiles = reusedJSON["profiles"] as! [String: Any]
        try check(reused.profileId == "tun-tun-live-26.3" && reusedProfiles.count == 1, "existing matching Launcher profile reused without duplication")
        try check((reusedProfiles["tun-tun-live-26.3"] as? [String: String])?["javaArgs"] == "-Xmx6G", "existing matching profile keeps custom settings")
        let rollback = try fixture("rollback")
        let before = try Data(contentsOf: rollback.appendingPathComponent("launcher_profiles.json"))
        let rm = rollback.appendingPathComponent("tun-tun-26.3/mods")
        try fm.createDirectory(at: rm, withIntermediateDirectories: true)
        try Data("rollback-old-bridge".utf8).write(to: rm.appendingPathComponent("tung-tung-bridge-1.6.0.jar"))
        try oldVoice.write(to: rm.appendingPathComponent(oldVoiceName))
        do { _ = try testInstall(rollback, fail: sourcePayload.mods[voiceChatMod] == nil ? 5 : 6); throw problem("Injected failure did not fire") } catch {}
        try check(try Data(contentsOf: rollback.appendingPathComponent("launcher_profiles.json")) == before, "failed install preserves Launcher bytes")
        try check(try Data(contentsOf: rm.appendingPathComponent("tung-tung-bridge-1.6.0.jar")) == Data("rollback-old-bridge".utf8), "rollback restores removed old bridge")
        try check(!fm.fileExists(atPath: rm.appendingPathComponent("tung-tung-bridge-1.30.3.jar").path), "rollback removes newly written bridge")
        try check(try Data(contentsOf: rm.appendingPathComponent(oldVoiceName)) == oldVoice, "rollback preserves or restores original voice chat")
        try check(!fm.fileExists(atPath: rm.appendingPathComponent(voiceChatMod).path), "rollback leaves no new voice chat")
        let corrupt = try fixture("corrupt")
        let bad = Data("{broken".utf8); try bad.write(to: corrupt.appendingPathComponent("launcher_profiles.json"))
        var rejected = false
        do { _ = try testInstall(corrupt) } catch { rejected = true }
        try check(rejected && (try Data(contentsOf: corrupt.appendingPathComponent("launcher_profiles.json"))) == bad, "corrupt Launcher rejected unchanged")
        let symlink = try fixture("symlink")
        try fm.createSymbolicLink(at: symlink.appendingPathComponent("tun-tun-26.3"), withDestinationURL: first)
        rejected = false; do { _ = try testInstall(symlink) } catch { rejected = true }
        try check(rejected, "symlink destination rejected")
        rejected = false; do { _ = try testInstall(first.appendingPathComponent("..")) } catch { rejected = true }
        try check(rejected, "path traversal root rejected")
        let running = try fixture("running")
        rejected = false; do { _ = try testInstall(running, check: { throw problem("Minecraft running") }) } catch { rejected = true }
        try check(rejected && !fm.fileExists(atPath: running.appendingPathComponent("tun-tun-26.3").path), "running game refusal writes nothing")
        let custom = root.appendingPathComponent("Games/Vanilla", isDirectory: true)
        try check(openedMinecraftFiles("p123\nn\(custom.path)/versions/26.3/26.3.jar\n", root: custom), "Java game files under a custom Minecraft folder detected")
        try check(!openedMinecraftFiles("p123\nn\(custom.path)-other/unrelated.jar\n", root: custom), "running-file prefix respects directory boundary")
        let tamperedResources = root.appendingPathComponent("tampered-resources")
        try fm.createDirectory(at: tamperedResources, withIntermediateDirectories: true)
        try fm.copyItem(at: resources.appendingPathComponent("payload"), to: tamperedResources.appendingPathComponent("payload"))
        try Data("tampered".utf8).write(to: tamperedResources.appendingPathComponent("payload/mods/tung-tung-bridge-1.30.3.jar"))
        rejected = false; do { _ = try perform(minecraftDirectory: try fixture("hash"), resources: tamperedResources, processCheck: {}, failAfter: nil) } catch { rejected = true }
        try check(rejected, "payload checksum mismatch rejected before writing")
        let staleVersion = try payloadFixture("stale-core-version") { document, _ in document["version"] = "1.28.0" }
        rejected = false; do { _ = try loadPayload(resources: staleVersion) } catch { rejected = true }
        try check(rejected, "stale core manifest rejected before writing")
        let staleCore = try payloadFixture("stale-core-filename") { document, _ in
            var files = document["files"] as! [[String: Any]]
            for index in files.indices where files[index]["path"] as? String == "mods/tung-tung-bridge-1.30.3.jar" {
                files[index]["path"] = "mods/tung-tung-bridge-1.28.0.jar"
            }
            document["files"] = files
        }
        rejected = false; do { _ = try loadPayload(resources: staleCore) } catch { rejected = true }
        try check(rejected, "stale core filename rejected before writing")
        let extraPayload = try payloadFixture("extra-payload") { document, _ in
            var files = document["files"] as! [[String: Any]]
            files.append(["path":"mods/unapproved.jar", "kind":"mod", "sha256":String(repeating:"0", count:64)])
            document["files"] = files
        }
        rejected = false; do { _ = try testInstall(try fixture("extra-target"), payloadResources: extraPayload) } catch { rejected = true }
        try check(rejected, "extra payload mod rejected before writing")
        let invalidFlag = try payloadFixture("invalid-voice-flag") { document, _ in document["simpleVoiceChat"] = "true" }
        rejected = false; do { _ = try loadPayload(resources: invalidFlag) } catch { rejected = true }
        try check(rejected, "voice chat flag requires a JSON boolean")
        let basePayload = try payloadFixture("base-payload") { document, payload in
            document["simpleVoiceChat"] = false
            document["files"] = (document["files"] as! [[String: Any]]).filter { $0["path"] as? String != "mods/\(voiceChatMod)" }
            let voice = payload.appendingPathComponent("mods/\(voiceChatMod)")
            if fm.fileExists(atPath: voice.path) { try fm.removeItem(at: voice) }
        }
        let baseTarget = try fixture("base-keeps-voice"), baseModsDirectory = baseTarget.appendingPathComponent("tun-tun-26.3/mods")
        try fm.createDirectory(at: baseModsDirectory, withIntermediateDirectories: true)
        try oldVoice.write(to: baseModsDirectory.appendingPathComponent(oldVoiceName))
        try Data("existing-pinned-name-user-data".utf8).write(to: baseModsDirectory.appendingPathComponent(voiceChatMod))
        _ = try testInstall(baseTarget, payloadResources: basePayload)
        try check(try Data(contentsOf: baseModsDirectory.appendingPathComponent(oldVoiceName)) == oldVoice
                  && (try Data(contentsOf: baseModsDirectory.appendingPathComponent(voiceChatMod))) == Data("existing-pinned-name-user-data".utf8), "false voice flag preserves all existing Fabric voice mods")
        var baseDocument = try JSONSerialization.jsonObject(with: Data(contentsOf: basePayload.appendingPathComponent("payload/manifest.json"))) as! [String: Any]
        baseDocument.removeValue(forKey: "simpleVoiceChat")
        try JSONSerialization.data(withJSONObject: baseDocument).write(to: basePayload.appendingPathComponent("payload/manifest.json"))
        try check(Set(try loadPayload(resources: basePayload).mods.keys) == baseMods, "missing voice flag accepts exactly the base payload")
        if sourcePayload.mods[voiceChatMod] != nil {
            let unpinned = try payloadFixture("unpinned-voice-payload") { document, _ in
                var files = document["files"] as! [[String: Any]]
                for index in files.indices where files[index]["path"] as? String == "mods/\(voiceChatMod)" {
                    files[index]["path"] = "mods/voicechat-fabric-2.6.23+26.3.jar"
                }
                document["files"] = files
            }
            rejected = false; do { _ = try loadPayload(resources: unpinned) } catch { rejected = true }
            try check(rejected, "unpinned voice filename rejected")
            let alteredVoice = try payloadFixture("altered-voice-payload") { document, payload in
                let bytes = Data("rehashed-but-not-pinned-voice".utf8)
                try bytes.write(to: payload.appendingPathComponent("mods/\(voiceChatMod)"))
                var files = document["files"] as! [[String: Any]]
                for index in files.indices where files[index]["path"] as? String == "mods/\(voiceChatMod)" {
                    files[index]["sha256"] = SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined()
                }
                document["files"] = files
            }
            rejected = false; do { _ = try loadPayload(resources: alteredVoice) } catch { rejected = true }
            try check(rejected, "rehashed voice content rejected by independent official pin")
            let falseCombined = try payloadFixture("false-combined-payload") { document, _ in document["simpleVoiceChat"] = false }
            rejected = false; do { _ = try loadPayload(resources: falseCombined) } catch { rejected = true }
            try check(rejected, "voice payload cannot be installed with a false flag")
        }
        return passed
    }
}

/// A bounded JSON syntax walker finds the profiles value without decoding unknown account/token values.
enum JSONProfilesRange {
    static func find(in data: Data) throws -> Range<Int> {
        guard data.count <= 8 * 1024 * 1024, String(data: data, encoding: .utf8) != nil else { throw FabricInstaller.InstallerError(message: "Launcher profiles are invalid UTF-8 JSON.") }
        var parser = Parser(bytes: Array(data)); let range = try parser.root()
        return range
    }
    private struct Parser {
        let bytes: [UInt8]; var index = 0
        mutating func whitespace() { while index < bytes.count && [9,10,13,32].contains(bytes[index]) { index += 1 } }
        func failure() -> FabricInstaller.InstallerError { FabricInstaller.InstallerError(message: "Launcher profiles JSON is corrupt. Installation stopped without changing it.") }
        mutating func take(_ byte: UInt8) throws { whitespace(); guard index < bytes.count && bytes[index] == byte else { throw failure() }; index += 1 }
        mutating func string() throws -> Range<Int> {
            whitespace(); let start = index; try take(34)
            while index < bytes.count {
                let byte = bytes[index]; index += 1
                if byte == 34 { return start..<index }
                guard byte >= 32 else { throw failure() }
                if byte == 92 {
                    guard index < bytes.count else { throw failure() }; let escape = bytes[index]; index += 1
                    if escape == 117 {
                        guard index + 4 <= bytes.count else { throw failure() }
                        for value in bytes[index..<index+4] where !(48...57).contains(value) && !(65...70).contains(value) && !(97...102).contains(value) { _ = value; throw failure() }
                        let code = Int(String(bytes: bytes[index..<index+4], encoding: .ascii)!, radix: 16)!
                        index += 4
                        if (0xD800...0xDBFF).contains(code) {
                            guard index + 6 <= bytes.count && bytes[index] == 92 && bytes[index+1] == 117,
                                  let low = Int(String(bytes: bytes[index+2..<index+6], encoding: .ascii) ?? "", radix: 16),
                                  (0xDC00...0xDFFF).contains(low) else { throw failure() }
                            index += 6
                        } else if (0xDC00...0xDFFF).contains(code) { throw failure() }
                    } else if ![34,92,47,98,102,110,114,116].contains(escape) { throw failure() }
                }
            }
            throw failure()
        }
        mutating func value(_ depth: Int) throws {
            guard depth < 128 else { throw failure() }; whitespace(); guard index < bytes.count else { throw failure() }
            switch bytes[index] {
            case 34: _ = try string()
            case 123:
                try take(123); whitespace(); if index < bytes.count && bytes[index] == 125 { index += 1; return }
                var names = Set<String>()
                while true {
                    let range = try string()
                    guard range.count <= 65536, let name = try JSONSerialization.jsonObject(with: Data(bytes[range]), options: [.fragmentsAllowed]) as? String,
                          names.insert(name).inserted else { throw failure() }
                    try take(58); try value(depth + 1); whitespace(); guard index < bytes.count else { throw failure() }
                    if bytes[index] == 125 { index += 1; return }; try take(44)
                }
            case 91:
                try take(91); whitespace(); if index < bytes.count && bytes[index] == 93 { index += 1; return }
                while true { try value(depth + 1); whitespace(); guard index < bytes.count else { throw failure() }; if bytes[index] == 93 { index += 1; return }; try take(44) }
            case 116,102,110:
                let literal: [UInt8] = bytes[index] == 116 ? Array("true".utf8) : bytes[index] == 102 ? Array("false".utf8) : Array("null".utf8)
                guard index + literal.count <= bytes.count && Array(bytes[index..<index+literal.count]) == literal else { throw failure() }; index += literal.count
            default:
                if bytes[index] == 45 { index += 1 }; guard index < bytes.count else { throw failure() }
                if bytes[index] == 48 { index += 1 }
                else { guard (49...57).contains(bytes[index]) else { throw failure() }; while index < bytes.count && (48...57).contains(bytes[index]) { index += 1 } }
                if index < bytes.count && bytes[index] == 46 { index += 1; let start=index; while index < bytes.count && (48...57).contains(bytes[index]) { index += 1 }; guard index > start else { throw failure() } }
                if index < bytes.count && [69,101].contains(bytes[index]) { index += 1; if index < bytes.count && [43,45].contains(bytes[index]) { index += 1 }; let start=index; while index < bytes.count && (48...57).contains(bytes[index]) { index += 1 }; guard index > start else { throw failure() } }
            }
        }
        mutating func root() throws -> Range<Int> {
            if bytes.count >= 3 && Array(bytes[0..<3]) == [0xEF,0xBB,0xBF] { index = 3 }
            try take(123); var found: Range<Int>? = nil; var keys = Set<String>()
            whitespace(); if index < bytes.count && bytes[index] == 125 { throw failure() }
            while true {
                let keyRange = try string()
                guard keyRange.count <= 65536, let key = try JSONSerialization.jsonObject(with: Data(bytes[keyRange]), options: [.fragmentsAllowed]) as? String, keys.insert(key).inserted else { throw failure() }
                try take(58); whitespace(); let start = index; try value(0)
                if key == "profiles" { guard found == nil, bytes[start] == 123 else { throw failure() }; found = start..<index }
                whitespace(); guard index < bytes.count else { throw failure() }
                if bytes[index] == 125 { index += 1; break }; try take(44)
            }
            whitespace(); guard index == bytes.count, let found else { throw failure() }; return found
        }
    }
}
