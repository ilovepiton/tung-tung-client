import Foundation

enum BuilderFiles {
    static func copy(source: URL, minecraftDirectory: URL) throws -> String {
        guard ["schem","schematic","litematic","nbt"].contains(source.pathExtension.lowercased()) else {
            throw NativeBridgeError.unavailable("Choose a .schem, .litematic or .nbt file.")
        }
        let handle=try FileHandle(forReadingFrom:source);defer{try? handle.close()}
        let data=handle.readData(ofLength:16*1024*1024+1)
        guard !data.isEmpty,data.count<=16*1024*1024 else {throw NativeBridgeError.unavailable("Choose a non-empty schematic up to 16 MB.")}
        let configs=[minecraftDirectory.appendingPathComponent("tun-tun-26.3/config"),minecraftDirectory.appendingPathComponent("config")]
        guard let config=configs.first(where:{FileManager.default.fileExists(atPath:$0.appendingPathComponent("tun-tun-connection.json").path)}) else {throw NativeBridgeError.unavailable("The selected Minecraft instance is offline.")}
        let folder=config.appendingPathComponent("tuntun-schematics",isDirectory:true)
        try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true)
        let name=UUID().uuidString.lowercased().replacingOccurrences(of:"-",with:"")+"."+source.pathExtension.lowercased()
        try data.write(to:folder.appendingPathComponent(name),options:.atomic)
        return name
    }
}
