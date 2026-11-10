using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Security.AccessControl;
using System.Security.Cryptography;
using System.Security.Principal;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace TungTung.Windows
{
    // Compatible-runtime reference transport; never injects code into Roblox Player.
    // The private authentication key stays native and is never returned to WebView2.
    public sealed class RobloxBridge : IDisposable
    {
        public static readonly string[] ModuleIds = { "fly", "noclip", "speed", "super_jump", "low_gravity", "dash", "double_jump", "air_jump", "self_fling", "friend_fling", "slide", "spin", "freeze", "respawn", "return_spawn", "spectate", "freecam", "fov", "fullbright", "sunset", "bloom", "depth_of_field", "color_grade", "trails" };
        private readonly object gate = new object();
        private readonly SemaphoreSlim peers = new SemaphoreSlim(16, 16);
        private readonly Dictionary<string, Pending> requests = new Dictionary<string, Pending>();
        private readonly List<JObject> queue = new List<JObject>();
        public readonly string DirectoryPath;
        private TcpListener listener;
        private System.Threading.Timer timer;
        private string key = "", session = "", transportError = "";
        private DateTime heartbeat = DateTime.MinValue;
        private long revision = -1;
        private JObject snapshot = new JObject();
        private bool disposed;
        public Action<JObject> OnState;
        private sealed class Pending
        {
            public string Session;
            public DateTime Deadline;
            public readonly TaskCompletionSource<JObject> Completion = new TaskCompletionSource<JObject>(TaskCreationOptions.RunContinuationsAsynchronously);
        }
        public RobloxBridge(string directory = null)
        {
            DirectoryPath = directory ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "TungTungClient", "Roblox");
        }
        private static IOException Failure(string message) { return new IOException(message); }
        private static string Clip(JToken token, string fallback, int limit)
        {
            string value = token != null && token.Type == JTokenType.String ? (string)token : fallback;
            return value.Length > limit ? value.Substring(0, limit) : value;
        }
        private static bool Bool(JToken token, out bool value)
        {
            value = token != null && token.Type == JTokenType.Boolean && (bool)token;
            return token != null && token.Type == JTokenType.Boolean;
        }
        private static long? Integer(JToken token)
        {
            if (token == null || (token.Type != JTokenType.Integer && token.Type != JTokenType.Float)) return null;
            try { double value = (double)token; return !Double.IsNaN(value) && !Double.IsInfinity(value) && value == Math.Truncate(value) && Math.Abs(value) <= 9007199254740991D ? (long?)value : null; } catch { return null; }
        }
        private static JObject Settings(JObject raw)
        {
            var result = new JObject();
            foreach (var property in raw.Properties())
            {
                double min, max; bool numeric = true;
                switch (property.Name)
                {
                    case "flySpeed": case "freecamSpeed": min = 1; max = 300; break;
                    case "walkSpeed": min = 1; max = 100; break;
                    case "fov": min = 30; max = 120; break;
                    default: min = max = 0; numeric = false; break;
                }
                if (numeric && (property.Value.Type == JTokenType.Integer || property.Value.Type == JTokenType.Float))
                {
                    double value = (double)property.Value;
                    if (!Double.IsNaN(value) && !Double.IsInfinity(value) && value >= min && value <= max) { result[property.Name] = value; continue; }
                }
                if (property.Name == "shaderPreset" && property.Value.Type == JTokenType.String && new[] { "warm", "cinematic", "vibrant", "clean" }.Contains((string)property.Value)) { result[property.Name] = (string)property.Value; continue; }
                throw Failure("Invalid Roblox setting: " + property.Name.Substring(0, Math.Min(40, property.Name.Length)) + ".");
            }
            return result;
        }
        private static JObject Snapshot(JObject raw)
        {
            long? next = Integer(raw["revision"]);
            var modules = raw["modules"] as JArray;
            if (!next.HasValue || next < 0 || modules == null || modules.Count != ModuleIds.Length) throw Failure("Incomplete runtime state.");
            var cleanModules = new JArray(); var seen = new HashSet<string>();
            foreach (var token in modules)
            {
                var module = token as JObject; bool available, enabled;
                string id = module == null ? null : (string)module["id"];
                if (id == null || !ModuleIds.Contains(id) || !seen.Add(id) || !Bool(module["available"], out available) || !Bool(module["enabled"], out enabled) || (enabled && !available)) throw Failure("Invalid runtime module state.");
                string scope = Clip(module["scope"], "local", 30);
                cleanModules.Add(new JObject { ["id"] = id, ["available"] = available, ["enabled"] = enabled, ["status"] = Clip(module["status"], "Local runtime", 160), ["scope"] = new[] { "local", "server", "unavailable" }.Contains(scope) ? scope : "local" });
            }
            var players = raw["players"] as JArray ?? new JArray();
            if (players.Count > 200) throw Failure("Too many players in runtime state.");
            var cleanPlayers = new JArray(); var users = new HashSet<long>();
            foreach (var token in players)
            {
                var player = token as JObject;
                if (player == null) throw Failure("Invalid runtime player state.");
                long? id = Integer(player["userId"]); string name = Clip(player["name"], "", 64);
                if (!id.HasValue || id <= 0 || !users.Add(id.Value) || name.Length == 0) throw Failure("Invalid runtime player state.");
                bool flingAllowed; Bool(player["flingAllowed"], out flingAllowed);
                cleanPlayers.Add(new JObject { ["userId"] = id.Value, ["name"] = name, ["displayName"] = Clip(player["displayName"], name, 64), ["flingAllowed"] = flingAllowed });
            }
            string type = Clip(raw["serverType"], "unknown", 30);
            return new JObject { ["revision"] = next.Value, ["modules"] = cleanModules, ["players"] = cleanPlayers, ["settings"] = Settings(raw["settings"] as JObject ?? new JObject()), ["placeName"] = Clip(raw["placeName"], "Roblox", 128), ["serverType"] = new[] { "private", "public", "studio", "unknown" }.Contains(type) ? type : "unknown" };
        }
        private JObject Current()
        {
            bool live = session.Length > 0 && DateTime.UtcNow - heartbeat < TimeSpan.FromSeconds(5);
            return new JObject {
                ["connected"] = live, ["verified"] = live, ["installed"] = false, ["sessionId"] = live ? session : "", ["revision"] = revision,
                ["protocolVersion"] = 1, ["connectionScope"] = "local-runtime", ["serverConfirmed"] = false, ["requiresRuntime"] = true,
                ["settings"] = snapshot["settings"] == null ? new JObject() : snapshot["settings"].DeepClone(),
                ["players"] = live && snapshot["players"] != null ? snapshot["players"].DeepClone() : new JArray(),
                ["placeName"] = live ? (string)snapshot["placeName"] : "No runtime connected", ["serverType"] = live ? (string)snapshot["serverType"] : "unknown",
                ["message"] = live ? "Runtime connected. Local effects may be corrected by the game server." : "A compatible in-game runtime is required. This app does not inject code into Roblox Player.",
                ["modules"] = live && snapshot["modules"] != null ? snapshot["modules"].DeepClone() : new JArray(ModuleIds.Select(id => new JObject { ["id"] = id, ["enabled"] = false, ["available"] = false, ["status"] = "Runtime required", ["scope"] = "unavailable" })),
                ["error"] = transportError
            };
        }
        public JObject State { get { lock (gate) { Expire(); return Current(); } } }
        private void Publish() { var handler = OnState; if (handler != null) { try { handler(Current()); } catch { } } }
        private void Disconnect()
        {
            session = ""; revision = -1; snapshot = new JObject(); queue.Clear();
            foreach (var pending in requests.Values) pending.Completion.TrySetException(Failure("The Roblox runtime disconnected. Reconnect before applying an action."));
            requests.Clear(); Publish();
        }
        private void Expire()
        {
            if (session.Length > 0 && DateTime.UtcNow - heartbeat >= TimeSpan.FromSeconds(5)) { Disconnect(); return; }
            foreach (string id in requests.Where(pair => pair.Value.Deadline < DateTime.UtcNow).Select(pair => pair.Key).ToArray())
            {
                Pending pending = requests[id]; requests.Remove(id); queue.RemoveAll(command => (string)command["requestId"] == id);
                pending.Completion.TrySetException(Failure("The runtime did not acknowledge this action. Its state has not been changed in the app."));
            }
        }
        private bool Available(string id) { return ((JArray)snapshot["modules"]).OfType<JObject>().Any(module => (string)module["id"] == id && (bool?)module["available"] == true); }
        public Task<JObject> Send(JObject payload)
        {
            lock (gate)
            {
                Expire();
                if (session.Length == 0 || (string)payload["sessionId"] != session) throw Failure("Connect a compatible Roblox runtime before using this control.");
                if (requests.Count >= 32) throw Failure("Wait for pending Roblox actions to finish.");
                JObject command;
                switch ((string)payload["command"])
                {
                    case "setModule":
                        string id = (string)payload["moduleId"]; bool enabled;
                        if (id == null || !ModuleIds.Contains(id) || !Bool(payload["enabled"], out enabled) || !Available(id)) throw Failure("This module is unavailable in the connected runtime.");
                        if (enabled && new[] { "friend_fling", "spectate" }.Contains(id)) throw Failure("Choose a player for this action.");
                        command = new JObject { ["command"] = "setModule", ["moduleId"] = id, ["enabled"] = enabled }; break;
                    case "setSettings":
                        var settings = payload["settings"] as JObject;
                        if (settings == null || !settings.HasValues) throw Failure("Choose a Roblox setting.");
                        command = new JObject { ["command"] = "setSettings", ["settings"] = Settings(settings) }; break;
                    case "playerAction":
                        string action = (string)payload["action"]; long? target = Integer(payload["targetUserId"]);
                        if (!new[] { "friend_fling", "spectate" }.Contains(action) || !Available(action) || !target.HasValue || target <= 0 || !((JArray)snapshot["players"]).OfType<JObject>().Any(player => Integer(player["userId"]) == target && (action != "friend_fling" || (bool?)player["flingAllowed"] == true))) throw Failure("This player action is unavailable or the player has left.");
                        command = new JObject { ["command"] = "playerAction", ["action"] = action, ["targetUserId"] = target.Value }; break;
                    default: throw Failure("Unknown Roblox command.");
                }
                string requestId = Guid.NewGuid().ToString(); command["requestId"] = requestId; command["sessionId"] = session;
                var pending = new Pending { Session = session, Deadline = DateTime.UtcNow.AddSeconds(7) }; requests.Add(requestId, pending); queue.Add(command); return pending.Completion.Task;
            }
        }
        private JObject Handle(string path, JObject body)
        {
            lock (gate)
            {
                Expire();
                if (path == "/v1/connect")
                {
                    var raw = body["snapshot"] as JObject;
                    if (Integer(body["protocolVersion"]) != 1 || raw == null) throw Failure("Unsupported runtime protocol.");
                    JObject clean = Snapshot(raw); Disconnect(); session = Guid.NewGuid().ToString(); heartbeat = DateTime.UtcNow; revision = (long)clean["revision"]; snapshot = clean; Publish();
                    return new JObject { ["ok"] = true, ["sessionId"] = session, ["heartbeatSeconds"] = 1, ["expiresAfterSeconds"] = 5 };
                }
                if (session.Length == 0 || (string)body["sessionId"] != session) throw Failure("The runtime session has expired.");
                switch (path)
                {
                    case "/v1/poll": heartbeat = DateTime.UtcNow; var commands = new JArray(queue.Select(item => item.DeepClone())); queue.Clear(); return new JObject { ["ok"] = true, ["commands"] = commands };
                    case "/v1/state": case "/v1/ack":
                        var raw = body["snapshot"] as JObject;
                        if (raw == null) throw Failure("Runtime state is required.");
                        JObject clean = Snapshot(raw); long next = (long)clean["revision"];
                        if (next <= revision) throw Failure("Stale runtime state was rejected.");
                        Pending pending = null; bool ok = false;
                        if (path == "/v1/ack")
                        {
                            string requestId = (string)body["requestId"];
                            if (requestId == null || !requests.TryGetValue(requestId, out pending) || pending.Session != session || !Bool(body["ok"], out ok)) throw Failure("The acknowledgement does not belong to a pending action.");
                            requests.Remove(requestId); queue.RemoveAll(command => (string)command["requestId"] == requestId);
                        }
                        revision = next; snapshot = clean; heartbeat = DateTime.UtcNow; Publish();
                        if (pending != null) { if (ok) pending.Completion.TrySetResult(Current()); else pending.Completion.TrySetException(Failure(Clip(body["error"], "The runtime could not apply the action.", 300))); }
                        return new JObject { ["ok"] = true };
                    case "/v1/disconnect": Disconnect(); return new JObject { ["ok"] = true };
                    default: throw Failure("Unknown runtime endpoint.");
                }
            }
        }
        public void Start()
        {
            lock (gate)
            {
                if (listener != null || disposed) return;
                try
                {
                    byte[] bytes = new byte[32]; using (var random = RandomNumberGenerator.Create()) random.GetBytes(bytes); key = BitConverter.ToString(bytes).Replace("-", "").ToLowerInvariant();
                    listener = new TcpListener(IPAddress.Loopback, 0); listener.Start(16);
                    WriteDescriptor(((IPEndPoint)listener.LocalEndpoint).Port);
                    timer = new System.Threading.Timer(_ => { lock (gate) Expire(); }, null, 500, 500);
                    Task.Run(() => AcceptLoop()); transportError = ""; Publish();
                }
                catch { if (listener != null) listener.Stop(); listener = null; key = ""; transportError = "The private local Roblox transport could not start."; Publish(); }
            }
        }
        private void WriteDescriptor(int port)
        {
            Directory.CreateDirectory(DirectoryPath);
            var identity = WindowsIdentity.GetCurrent().User;
            var directorySecurity = new DirectorySecurity(); directorySecurity.SetAccessRuleProtection(true, false);
            directorySecurity.AddAccessRule(new FileSystemAccessRule(identity, FileSystemRights.FullControl, InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit, PropagationFlags.None, AccessControlType.Allow));
            Directory.SetAccessControl(DirectoryPath, directorySecurity);
            string file = Path.Combine(DirectoryPath, "connection.json"), pending = file + ".pending";
            var descriptor = new JObject { ["protocolVersion"] = 1, ["endpoint"] = "http://127.0.0.1:" + port + "/v1/", ["key"] = key, ["processId"] = Process.GetCurrentProcess().Id, ["requiresCompatibleRuntime"] = true };
            File.WriteAllText(pending, descriptor.ToString(Formatting.Indented), new UTF8Encoding(false));
            var security = new FileSecurity(); security.SetAccessRuleProtection(true, false); security.AddAccessRule(new FileSystemAccessRule(identity, FileSystemRights.FullControl, AccessControlType.Allow)); File.SetAccessControl(pending, security);
            if (File.Exists(file)) File.Replace(pending, file, null); else File.Move(pending, file);
        }
        private async Task AcceptLoop()
        {
            while (true)
            {
                TcpClient client;
                try { TcpListener current; lock (gate) current = listener; if (current == null) return; client = await current.AcceptTcpClientAsync(); }
                catch { return; }
                if (!peers.Wait(0)) { client.Close(); continue; }
                Task ignored = Task.Run(async () => { try { await Serve(client); } catch { } finally { client.Close(); peers.Release(); } });
            }
        }
        private async Task Serve(TcpClient client)
        {
            client.ReceiveTimeout = 4000; client.SendTimeout = 4000;
            using (var network = client.GetStream())
            using (var bytes = new MemoryStream())
            {
                byte[] block = new byte[8192]; DateTime deadline = DateTime.UtcNow.AddSeconds(4);
                while (DateTime.UtcNow < deadline)
                {
                    Task<int> read = network.ReadAsync(block, 0, block.Length);
                    if (await Task.WhenAny(read, Task.Delay(Math.Max(1, (int)(deadline - DateTime.UtcNow).TotalMilliseconds))) != read) return;
                    int count = await read; if (count == 0) return; bytes.Write(block, 0, count);
                    if (bytes.Length > 73728) { await Respond(network, 413, new JObject { ["error"] = "Request too large" }); return; }
                    byte[] all = bytes.ToArray(); int end = -1;
                    for (int i = 0; i + 3 < all.Length; i++) if (all[i] == 13 && all[i + 1] == 10 && all[i + 2] == 13 && all[i + 3] == 10) { end = i; break; }
                    if (end < 0) { if (bytes.Length > 8192) { await Respond(network, 400, new JObject { ["error"] = "Header too large" }); return; } continue; }
                    if (end > 8192) { await Respond(network, 400, new JObject { ["error"] = "Invalid header" }); return; }
                    string[] lines = new UTF8Encoding(false, true).GetString(all, 0, end).Split(new[] { "\r\n" }, StringSplitOptions.None);
                    string[] request = lines[0].Split(' ');
                    if (request.Length != 3 || request[0] != "POST" || request[2] != "HTTP/1.1" || !new[] { "/v1/connect", "/v1/poll", "/v1/state", "/v1/ack", "/v1/disconnect" }.Contains(request[1])) { await Respond(network, 400, new JObject { ["error"] = "Unknown request" }); return; }
                    var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase); bool valid = true;
                    foreach (string line in lines.Skip(1)) { int colon = line.IndexOf(':'); if (colon < 1 || headers.ContainsKey(line.Substring(0, colon))) { valid = false; break; } headers.Add(line.Substring(0, colon), line.Substring(colon + 1).Trim()); }
                    string lengthString, contentType; int length;
                    if (!valid || headers.ContainsKey("Transfer-Encoding") || !headers.TryGetValue("Content-Type", out contentType) || contentType.Split(';')[0] != "application/json" || !headers.TryGetValue("Content-Length", out lengthString) || lengthString.Length == 0 || lengthString.Any(c => c < '0' || c > '9') || !Int32.TryParse(lengthString, out length) || length < 2 || length > 65536) { await Respond(network, 400, new JObject { ["error"] = "Bounded JSON body required" }); return; }
                    int offset = end + 4; if (all.Length < offset + length) continue;
                    if (all.Length != offset + length) { await Respond(network, 400, new JObject { ["error"] = "Unexpected trailing data" }); return; }
                    string supplied; bool authenticated; lock (gate) authenticated = key.Length > 0 && headers.TryGetValue("X-Tung-Roblox-Key", out supplied) && supplied == key && !headers.ContainsKey("Origin");
                    if (!authenticated) { await Respond(network, 403, new JObject { ["error"] = "Local runtime authentication required" }); return; }
                    try { var body = JObject.Parse(new UTF8Encoding(false, true).GetString(all, offset, length)); await Respond(network, 200, Handle(request[1], body)); }
                    catch (Exception error) { await Respond(network, 400, new JObject { ["error"] = error is IOException ? error.Message : "Invalid runtime request." }); }
                    return;
                }
            }
        }
        private static async Task Respond(NetworkStream stream, int status, JObject value)
        {
            byte[] body = Encoding.UTF8.GetBytes(value.ToString(Formatting.None));
            byte[] header = Encoding.ASCII.GetBytes("HTTP/1.1 " + status + (status == 200 ? " OK" : " Error") + "\r\nContent-Type: application/json\r\nContent-Length: " + body.Length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n");
            await stream.WriteAsync(header, 0, header.Length); await stream.WriteAsync(body, 0, body.Length);
        }
        public void Dispose()
        {
            lock (gate)
            {
                if (disposed) return; disposed = true;
                if (timer != null) timer.Dispose(); timer = null;
                if (listener != null) listener.Stop(); listener = null;
                Disconnect(); key = "";
                try { File.Delete(Path.Combine(DirectoryPath, "connection.json")); } catch { }
            }
        }
    }
}
