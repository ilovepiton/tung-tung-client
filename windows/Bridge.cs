using System;
using System.IO;
using System.Net;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;

namespace TungTung.Windows
{
    // Only this native class sees the token. JavaScript receives a session number.
    public sealed class GameBridge
    {
        private readonly SemaphoreSlim gate = new SemaphoreSlim(1, 1);
        private string folder, identity;
        private int generation;
        private JObject latest = new JObject { ["connected"] = false, ["connectionGeneration"] = 0 };
        public GameBridge(string minecraftFolder) { folder = minecraftFolder; }
        public JObject State { get { return (JObject)latest.DeepClone(); } }
        public async Task SetFolder(string value)
        {
            await gate.WaitAsync();
            try { folder = Path.GetFullPath(value); identity = null; generation++; latest = Offline(); }
            finally { gate.Release(); }
        }
        private JObject Offline() { return new JObject { ["connected"] = false, ["connectionGeneration"] = generation }; }
        private Tuple<int, string> Descriptor()
        {
            foreach (string file in new[] { Path.Combine(folder, "tun-tun-26.3", "config", "tun-tun-connection.json"), Path.Combine(folder, "config", "tun-tun-connection.json") })
            {
                if (!File.Exists(file)) continue;
                if (new FileInfo(file).Length > 8192) throw new IOException("Invalid game connection file. Restart Minecraft.");
                JObject d = JObject.Parse(File.ReadAllText(file));
                JToken p = d["port"], t = d["token"];
                if (p == null || p.Type != JTokenType.Integer || (long)p < 1 || (long)p > 65535 || t == null || t.Type != JTokenType.String || !Regex.IsMatch((string)t, "\\A[0-9a-fA-F]{64}\\z"))
                    throw new IOException("Invalid game connection file. Restart Minecraft.");
                string next = (string)p + ":" + (string)t;
                if (identity != next) { identity = next; generation++; latest = Offline(); }
                return Tuple.Create((int)p, (string)t);
            }
            throw new IOException("Start Tung Tung Client 26.3 in Minecraft Launcher and join a world.");
        }
        private JObject Request(Tuple<int, string> descriptor, string endpoint, JObject body)
        {
            HttpWebRequest request = (HttpWebRequest)WebRequest.Create("http://127.0.0.1:" + descriptor.Item1 + endpoint);
            request.Proxy = null; request.AllowAutoRedirect = false; request.KeepAlive = false;
            request.Timeout = 4500; request.ReadWriteTimeout = 4500;
            request.Headers["X-Tun-Token"] = descriptor.Item2;
            if (body != null)
            {
                byte[] data = Encoding.UTF8.GetBytes(body.ToString(Newtonsoft.Json.Formatting.None));
                if (data.Length > 65536) throw new IOException("Game request exceeds 64 KB.");
                request.Method = "POST"; request.ContentType = "application/json"; request.ContentLength = data.Length;
                using (Stream output = request.GetRequestStream()) output.Write(data, 0, data.Length);
            }
            try
            {
                using (HttpWebResponse response = (HttpWebResponse)request.GetResponse())
                {
                    if ((int)response.StatusCode < 200 || (int)response.StatusCode > 299) throw new IOException("Unexpected game response.");
                    if (response.ContentLength > 2 * 1024 * 1024) throw new IOException("Game response exceeds 2 MB.");
                    using (Stream input = response.GetResponseStream())
                    using (MemoryStream output = new MemoryStream())
                    {
                        byte[] buffer = new byte[16384]; int read;
                        while ((read = input.Read(buffer, 0, buffer.Length)) > 0)
                        {
                            if (output.Length + read > 2 * 1024 * 1024) throw new IOException("Game response exceeds 2 MB.");
                            output.Write(buffer, 0, read);
                        }
                        return JObject.Parse(Encoding.UTF8.GetString(output.ToArray()));
                    }
                }
            }
            catch (WebException error)
            {
                if (error.Response != null) error.Response.Dispose();
                throw new IOException("Minecraft could not apply the request. Check the current world and try again.", error);
            }
        }
        private JObject Adopt(JObject state)
        {
            if (latest["worldSession"] != null && (!JToken.DeepEquals(latest["worldSession"], state["worldSession"]) || !JToken.DeepEquals(latest["connected"], state["connected"]) || !JToken.DeepEquals(latest["server"], state["server"]))) generation++;
            state["connectionGeneration"] = generation; latest = state; return State;
        }
        public async Task<JObject> Poll()
        {
            await gate.WaitAsync();
            try
            {
                var descriptor = Descriptor(); string expected = identity;
                JObject incoming = await Task.Run(() => Request(descriptor, "/state", null));
                Descriptor(); if (expected != identity) throw new IOException("Minecraft restarted.");
                return Adopt(incoming);
            }
            catch { if ((bool?)latest["connected"] == true) generation++; latest = Offline(); return State; }
            finally { gate.Release(); }
        }
        public async Task<JObject> Send(string action, JObject payload)
        {
            if (action != "module" && action != "settings" && action != "action") throw new ArgumentException("Unknown game operation.");
            await gate.WaitAsync();
            try
            {
                var descriptor = Descriptor(); string expected = identity;
                int requested = (int?)payload["bridgeGeneration"] ?? -1;
                // Refresh before writing; the core also verifies the observed world session.
                JObject fresh = await Task.Run(() => Request(descriptor, "/state", null));
                Descriptor(); if (expected != identity) throw new IOException("Minecraft restarted before the action.");
                Adopt(fresh);
                if (requested != generation || (bool?)latest["connected"] != true) throw new IOException("Minecraft connection changed. Apply the action again.");
                JObject body = (JObject)payload.DeepClone(); body.Remove("bridgeGeneration");
                if (latest["worldSession"] != null) body["expectedWorldSession"] = latest["worldSession"].DeepClone();
                JObject answer = await Task.Run(() => Request(descriptor, "/" + action, body));
                Descriptor(); if (expected != identity) throw new IOException("The response belongs to an earlier Minecraft instance.");
                return Adopt(answer);
            }
            finally { gate.Release(); }
        }
    }
}
