using System;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Linq;
using System.Diagnostics;
using System.Security.Cryptography;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;

namespace TungTung.Windows {
 internal static class ClientUpdater {
  const string Repo="https://github.com/ilovepiton/tung-tung-client";
  static string Quote(string s) { return "\""+s.TrimEnd('\\')+"\""; }
  internal static string Checksum(string text,string name) {
   foreach(var line in text.Split('\n')) {var p=Regex.Split(line.Trim(),"\\s+");if(p.Length==2&&p[1]==name&&Regex.IsMatch(p[0],"^[a-fA-F0-9]{64}$"))return p[0].ToLowerInvariant();}
   throw new IOException("No matching SHA256 checksum in the release.");
  }
  static string Hash(string file) { using(var f=File.OpenRead(file))using(var h=SHA256.Create())return BitConverter.ToString(h.ComputeHash(f)).Replace("-","").ToLowerInvariant(); }
  static Uri Asset(JArray assets,string version,string name) {
   var item=assets.OfType<JObject>().FirstOrDefault(a=>(string)a["name"]==name);var url=(string)item?["browser_download_url"];
   if(url!=Repo+"/releases/download/v"+version+"/"+name)throw new IOException("Unexpected update asset URL.");return new Uri(url);
  }
  internal static async Task<ProcessStartInfo> Prepare(string current,string minecraft,string bundle,Action<string> progress) {
   if(String.Equals(Path.GetFileName(minecraft.TrimEnd('\\','/')),"tun-tun-26.3",StringComparison.OrdinalIgnoreCase))minecraft=Path.GetDirectoryName(minecraft.TrimEnd('\\','/'));
   ServicePointManager.SecurityProtocol|=SecurityProtocolType.Tls12;
   using(var web=new WebClient()) {
    web.Headers[HttpRequestHeader.UserAgent]="TungTungClient-Updater";
    progress("Checking GitHub for updates…");
    var json=await web.DownloadStringTaskAsync("https://api.github.com/repos/ilovepiton/tung-tung-client/releases/latest");
    if(json.Length>1024*1024)throw new IOException("Release information too large.");var release=JObject.Parse(json);
    var tag=(string)release["tag_name"];if(tag==null||!Regex.IsMatch(tag,"^v[0-9]+\\.[0-9]+\\.[0-9]+$"))throw new IOException("Invalid release version.");
    var version=tag.Substring(1);if(new Version(version)<=new Version(current)){progress("You're up to date · "+current);return null;}
    foreach(var name in new[]{"Minecraft","MinecraftLauncher","javaw","java"})if(Process.GetProcessesByName(name).Length>0)throw new IOException("Close Minecraft and its Launcher, then click Update again.");
    var assets=release["assets"] as JArray;if(assets==null)throw new IOException("Release assets missing.");
    var nameZip="Tung-Tung-Client-Windows-"+version+"-Voice-Chat.zip";
    var expected=Checksum(await web.DownloadStringTaskAsync(Asset(assets,version,"SHA256SUMS-"+version+".txt")),nameZip);
    var work=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"TungTungClient","Updates",version);Directory.CreateDirectory(work);
    var archive=Path.Combine(work,nameZip);
    if(!File.Exists(archive)||Hash(archive)!=expected) {
     progress("Downloading client "+version+"…");await web.DownloadFileTaskAsync(Asset(assets,version,nameZip),archive);
     if(new FileInfo(archive).Length>900L*1024*1024||Hash(archive)!=expected){File.Delete(archive);throw new IOException("Update checksum mismatch. Nothing installed.");}
    }
    progress("Verifying and preparing "+version+"…");
    var stage=Path.Combine(work,"extracted");if(Directory.Exists(stage))Directory.Delete(stage,true);Directory.CreateDirectory(stage);
    using(var zip=ZipFile.OpenRead(archive)) {
     long size=0;var prefix=Path.GetFullPath(stage)+Path.DirectorySeparatorChar;
     foreach(var entry in zip.Entries) {
      size+=entry.Length;
      var path=Path.GetFullPath(Path.Combine(stage,entry.FullName));
      if(size>2L*1024*1024*1024||!path.StartsWith(prefix,StringComparison.OrdinalIgnoreCase)||Path.IsPathRooted(entry.FullName)||entry.FullName.Split('/','\\').Contains("..")||(entry.ExternalAttributes>>16&0xF000)==0xA000)throw new IOException("Unsafe update archive.");
     }
    }
    ZipFile.ExtractToDirectory(archive,stage);
    var source=Path.Combine(stage,"Tung-Tung-Client-Windows-"+version);
    var manifest=JObject.Parse(File.ReadAllText(Path.Combine(source,"manifest.json")));
    if((string)manifest["version"]!=version||!File.Exists(Path.Combine(source,"Tung-Tung-Client.exe")))throw new IOException("Unexpected Windows update bundle.");
    var helper=Path.Combine(bundle,"desktop","Update-Client.ps1");if(!File.Exists(helper))throw new IOException("Update helper missing.");
    var info=new ProcessStartInfo(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),"WindowsPowerShell","v1.0","powershell.exe"));
    info.UseShellExecute=false;info.CreateNoWindow=true;
    info.Arguments="-NoLogo -NoProfile -ExecutionPolicy Bypass -File "+Quote(helper)+" -Source "+Quote(source)+" -Target "+Quote(bundle)+" -MinecraftPath "+Quote(minecraft)+" -ParentPid "+Process.GetCurrentProcess().Id+" -Work "+Quote(work);
    progress("Update verified · restarting the client…");return info;
   }
  }
 }
}
