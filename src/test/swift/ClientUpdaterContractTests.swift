import Foundation
import AppKit
struct NativeBridgeError: LocalizedError {var errorDescription:String?;static func unavailable(_ s:String)->Self{Self(errorDescription:s)}}
enum FabricInstaller {static func checkCanUpdate(minecraftDirectory:URL)throws{}}
@main struct Checks {
 static func main()throws {
  assert(ClientUpdater.validVersion("1.35.3"));assert(!ClientUpdater.validVersion("../x"));assert(!ClientUpdater.validVersion("1.2.3;whoami"))
  let name="Tung-Tung-Client-Mac-1.35.3-Voice-Chat.zip",sha=String(repeating:"a",count:64)
  let matched=try ClientUpdater.checksum(sha+"  "+name,name:name);assert(matched==sha)
  do {_ = try ClientUpdater.checksum(sha+"  wrong.zip",name:name);fatalError("accepted wrong asset")}catch{}
  _ = try ClientUpdater.assetURL(ClientUpdater.repo+"/releases/download/v1.35.3/"+name,version:"1.35.3",name:name)
  for url in ["https://evil.example/"+name,ClientUpdater.repo+"/releases/download/v1.35.2/"+name] {do{_ = try ClientUpdater.assetURL(url,version:"1.35.3",name:name);fatalError("accepted other source")}catch{}}
  let prepared=try ClientUpdater.prepare(current:"99.0.0",minecraft:URL(fileURLWithPath:"/unused-test-root"),resources:URL(fileURLWithPath:"/unused-test-resources"),progress:{ _ in })
  assert(prepared == nil)
  print("PASS: updater version, checksum, fixed-source and live GitHub current-version checks")
 }
}
