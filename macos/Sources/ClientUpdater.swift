import Foundation
import AppKit
import CryptoKit

/// Downloads only this project's release assets; never receives URLs or commands from the web view.
enum ClientUpdater {
    static let repo = "https://github.com/ilovepiton/tung-tung-client"
    static func validVersion(_ value: String) -> Bool { value.range(of: #"^[0-9]+\.[0-9]+\.[0-9]+$"#, options: .regularExpression) != nil }
    static func assetURL(_ text: String, version: String, name: String) throws -> URL {
        guard validVersion(version), text == repo + "/releases/download/v" + version + "/" + name, let url=URL(string:text) else { throw NativeBridgeError.unavailable("Unexpected update asset URL") }
        return url
    }
    static func checksum(_ text: String, name: String) throws -> String {
        for line in text.split(separator:"\n") {
            let parts=line.split(whereSeparator:{$0.isWhitespace})
            if parts.count == 2 && parts[1] == name && parts[0].range(of:#"^[a-fA-F0-9]{64}$"#,options:.regularExpression) != nil { return parts[0].lowercased() }
        }
        throw NativeBridgeError.unavailable("The release has no matching SHA256 checksum")
    }
    private static func data(_ url: URL, maximum: Int) throws -> Data {
        let config=URLSessionConfiguration.ephemeral;config.timeoutIntervalForRequest=30;config.timeoutIntervalForResource=360
        let session=URLSession(configuration:config);defer{session.finishTasksAndInvalidate()}
        var request=URLRequest(url:url);request.setValue("TungTungClient-Updater",forHTTPHeaderField:"User-Agent")
        let done=DispatchSemaphore(value:0);var result:Result<Data,Error>?
        let task=session.dataTask(with:request){bytes,response,error in
            defer{done.signal()}
            if let error{result = .failure(error);return}
            guard let response=response as? HTTPURLResponse,response.statusCode==200,let bytes,bytes.count<=maximum else {result = .failure(NativeBridgeError.unavailable("Update download failed or exceeded its size limit"));return}
            result = .success(bytes)
        };task.resume()
        guard done.wait(timeout:.now()+370) == .success else {task.cancel();throw NativeBridgeError.unavailable("Update download timed out")}
        return try result!.get()
    }
    private static func run(_ file: String,_ args:[String]) throws -> String {
        let task=Process(),pipe=Pipe();task.executableURL=URL(fileURLWithPath:file);task.arguments=args;task.standardOutput=pipe;task.standardError=pipe
        try task.run();let bytes=pipe.fileHandleForReading.readDataToEndOfFile();task.waitUntilExit()
        let text=String(decoding:bytes,as:UTF8.self)
        guard task.terminationStatus==0 else {throw NativeBridgeError.unavailable(String(text.prefix(800)).isEmpty ? "Update preparation failed" : String(text.prefix(800)))}
        return text
    }
    /// Runs off the main thread. Returns nil when already current, otherwise a prepared helper.
    static func prepare(current:String,minecraft:URL,resources:URL,progress:(String)->Void) throws -> Process? {
        let gameRoot=minecraft.lastPathComponent == "tun-tun-26.3" ? minecraft.deletingLastPathComponent() : minecraft
        progress("Checking GitHub for updates…")
        let bytes=try data(URL(string:"https://api.github.com/repos/ilovepiton/tung-tung-client/releases/latest")!,maximum:1024*1024)
        guard let release=try JSONSerialization.jsonObject(with:bytes) as? [String:Any],let tag=release["tag_name"] as? String,tag.hasPrefix("v"),validVersion(String(tag.dropFirst())),let assets=release["assets"] as? [[String:Any]] else {throw NativeBridgeError.unavailable("Invalid GitHub release information")}
        let version=String(tag.dropFirst())
        guard version.compare(current,options:.numeric) == .orderedDescending else {progress("You're up to date · "+current);return nil}
        // Same process/file checks used by the real installer. Never terminate the game.
        try FabricInstaller.checkCanUpdate(minecraftDirectory:gameRoot)
        let name="Tung-Tung-Client-Mac-\(version)-Voice-Chat.zip",sumName="SHA256SUMS-\(version).txt"
        func source(_ name:String) throws -> URL {
            guard let asset=assets.first(where:{$0["name"] as? String == name}),let url=asset["browser_download_url"] as? String else {throw NativeBridgeError.unavailable("The latest release does not contain a complete Mac update")}
            return try assetURL(url,version:version,name:name)
        }
        let expected=try checksum(String(decoding:data(source(sumName),maximum:65536),as:UTF8.self),name:name)
        let fm=FileManager.default,root=fm.homeDirectoryForCurrentUser.appendingPathComponent("Library/Application Support/TungTungClient/Updates/\(version)")
        try fm.createDirectory(at:root,withIntermediateDirectories:true)
        let archive=root.appendingPathComponent(name)
        var cached=false
        if fm.fileExists(atPath:archive.path) { cached = SHA256.hash(data:try Data(contentsOf:archive,options:.mappedIfSafe)).map({String(format:"%02x",$0)}).joined() == expected }
        if !cached {
            progress("Downloading client \(version)…")
            let downloaded=try data(source(name),maximum:900*1024*1024)
            guard SHA256.hash(data:downloaded).map({String(format:"%02x",$0)}).joined()==expected else {throw NativeBridgeError.unavailable("Update checksum mismatch; nothing was installed")}
            try downloaded.write(to:archive,options:.atomic)
        }
        progress("Verifying and preparing \(version)…")
        let names=try run("/usr/bin/unzip",["-Z1",archive.path])
        guard names.split(separator:"\n").allSatisfy({!$0.hasPrefix("/") && !$0.contains("\\") && !$0.split(separator:"/").contains("..")}) else {throw NativeBridgeError.unavailable("Unsafe update archive path")}
        let stage=root.appendingPathComponent("extracted")
        if fm.fileExists(atPath:stage.path){try fm.removeItem(at:stage)}
        try fm.createDirectory(at:stage,withIntermediateDirectories:true)
        _=try run("/usr/bin/ditto",["-x","-k",archive.path,stage.path])
        let app=stage.appendingPathComponent("Tung Tung Client.app")
        guard let info=NSDictionary(contentsOf:app.appendingPathComponent("Contents/Info.plist")),info["CFBundleIdentifier"] as? String == "dev.tungtung.client",info["CFBundleShortVersionString"] as? String == version else {throw NativeBridgeError.unavailable("Unexpected Mac application in update")}
        if let files=fm.enumerator(at:app,includingPropertiesForKeys:[.isSymbolicLinkKey]) {
            for case let file as URL in files where (try file.resourceValues(forKeys:[.isSymbolicLinkKey])).isSymbolicLink == true {
                guard file.resolvingSymlinksInPath().path.hasPrefix(app.path+"/") else {throw NativeBridgeError.unavailable("External link in update bundle")}
            }
        }
        _=try run("/usr/bin/codesign",["--verify","--deep","--strict",app.path])
        try FabricInstaller.checkCanUpdate(minecraftDirectory:gameRoot)
        progress("Installing the new Fabric core…")
        _=try run(app.appendingPathComponent("Contents/MacOS/TungTungClient").path,["--install-core-update",gameRoot.path])
        let helper=resources.appendingPathComponent("update-apply.sh")
        guard fm.fileExists(atPath:helper.path) else {throw NativeBridgeError.unavailable("Bundled update helper is missing")}
        let task=Process();task.executableURL=URL(fileURLWithPath:"/bin/bash")
        task.arguments=[helper.path,app.path,Bundle.main.bundleURL.path,String(ProcessInfo.processInfo.processIdentifier),root.path]
        let log=root.appendingPathComponent("apply.log");fm.createFile(atPath:log.path,contents:nil)
        let handle=try FileHandle(forWritingTo:log);task.standardOutput=handle;task.standardError=handle
        progress("Update verified · restarting the client…")
        return task
    }
}
