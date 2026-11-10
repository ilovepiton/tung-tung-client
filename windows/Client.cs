using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Media;
using System.Runtime.InteropServices;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;
using Newtonsoft.Json.Linq;

[assembly: System.Reflection.AssemblyVersion("1.30.2.0")]
[assembly: System.Reflection.AssemblyFileVersion("1.30.2.0")]
[assembly: System.Reflection.AssemblyTitle("Tung Tung Client")]

namespace TungTung.Windows
{
    internal static class Entry
    {
        [STAThread]
        public static void Main()
        {
            Application.EnableVisualStyles(); Application.SetCompatibleTextRenderingDefault(false);
            using (Mutex instance = new Mutex(false, "Local\\TungTungClientModern"))
            {
                bool acquired;
                try { acquired = instance.WaitOne(0); } catch (AbandonedMutexException) { acquired = true; }
                if (!acquired) { MessageBox.Show("Tung Tung Client is already open. Use its system-tray icon."); return; }
                try { Application.Run(new ClientWindow()); }
                catch (Exception e) { MessageBox.Show(e.Message, "Tung Tung Client", MessageBoxButtons.OK, MessageBoxIcon.Error); }
                finally { instance.ReleaseMutex(); }
            }
        }
    }

    internal sealed class ClientWindow : Form
    {
        private const string Origin = "https://tung-tung.local";
        private readonly string bundle = AppDomain.CurrentDomain.BaseDirectory;
        private readonly string data = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "TungTungClient");
        private readonly WebView2 web = new WebView2();
        private readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer { Interval = 500 };
        private readonly NotifyIcon tray = new NotifyIcon();
        private readonly GameBridge bridge;
        private readonly RobloxBridge roblox = new RobloxBridge();
        private string minecraft, launcher, installStatus = "Close Minecraft and its Launcher, then install Fabric Core 1.30.1.";
        private bool sounds = true, ready, polling, installing, exiting;
        private long? openSequence;
        private SoundPlayer sound;

        public ClientWindow()
        {
            minecraft = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".minecraft");
            try
            {
                JObject prefs = JObject.Parse(File.ReadAllText(Path.Combine(data, "preferences.json")));
                minecraft = (string)prefs["minecraftFolder"] ?? minecraft; sounds = (bool?)prefs["soundsEnabled"] ?? true; launcher = (string)prefs["launcher"];
            }
            catch { }
            bridge = new GameBridge(minecraft);
            Text = "Tung Tung Client 1.30.2"; BackColor = Color.FromArgb(16, 12, 9);
            StartPosition = FormStartPosition.CenterScreen; ClientSize = new Size(1180, 760); MinimumSize = new Size(1000, 700);
            web.Dock = DockStyle.Fill; web.DefaultBackgroundColor = BackColor; Controls.Add(web);
            tray.Icon = SystemIcons.Application; tray.Text = "Tung Tung Client";
            var menu = new ContextMenuStrip(); menu.Items.Add("Open Tung Tung Client", null, (s, e) => ShowClient());
            menu.Items.Add("Quit", null, (s, e) => { exiting = true; Close(); });
            tray.ContextMenuStrip = menu; tray.DoubleClick += (s, e) => ShowClient(); tray.Visible = true;
            FormClosing += (s, e) => { if (!exiting && e.CloseReason == CloseReason.UserClosing) { e.Cancel = true; Hide(); } };
            FormClosed += (s, e) => { ready = false; roblox.Dispose(); timer.Stop(); timer.Dispose(); tray.Dispose(); if (sound != null) sound.Dispose(); web.Dispose(); };
            roblox.OnState = state => { if (ready && !IsDisposed && IsHandleCreated) BeginInvoke((Action)(() => Post(new { kind = "robloxState", state = state }))); };
            timer.Tick += async (s, e) => await Poll();
            Shown += async (s, e) => await Initialize();
        }
        private void ShowClient() { Show(); WindowState = FormWindowState.Normal; Activate(); }
        private async Task Initialize()
        {
            try
            {
                string ui = Path.Combine(bundle, "ui");
                if (!File.Exists(Path.Combine(ui, "index.html"))) throw new IOException("Extract the complete ZIP before opening the client.");
                Directory.CreateDirectory(data);
                var environment = await CoreWebView2Environment.CreateAsync(null, Path.Combine(data, "WebView2"));
                await web.EnsureCoreWebView2Async(environment);
                web.CoreWebView2.SetVirtualHostNameToFolderMapping("tung-tung.local", ui, CoreWebView2HostResourceAccessKind.Deny);
                web.CoreWebView2.Settings.AreDefaultContextMenusEnabled = false;
                web.CoreWebView2.Settings.AreDevToolsEnabled = false;
                web.CoreWebView2.Settings.AreHostObjectsAllowed = false;
                web.CoreWebView2.Settings.IsStatusBarEnabled = false;
                web.CoreWebView2.Settings.IsZoomControlEnabled = false;
                web.CoreWebView2.NavigationStarting += (s, e) => { if (e.Uri != Origin + "/index.html") e.Cancel = true; };
                web.CoreWebView2.NewWindowRequested += (s, e) => e.Handled = true;
                web.CoreWebView2.PermissionRequested += (s, e) => e.State = CoreWebView2PermissionState.Deny;
                web.CoreWebView2.WebMessageReceived += OnMessage;
                roblox.Start();
                web.NavigationCompleted += async (s, e) => { ready = e.IsSuccess; if (ready) { timer.Start(); await Poll(); } };
                web.CoreWebView2.Navigate(Origin + "/index.html");
            }
            catch (WebView2RuntimeNotFoundException)
            {
                if (MessageBox.Show("Microsoft WebView2 Runtime is needed to display the client. Install it now, then reopen Tung Tung Client?", "Tung Tung Client", MessageBoxButtons.YesNo) == DialogResult.Yes)
                    Process.Start(new ProcessStartInfo(Path.Combine(bundle, "MicrosoftEdgeWebview2Setup.exe")) { UseShellExecute = true });
                exiting = true; Close();
            }
            catch (Exception e) { MessageBox.Show(e.Message, Text); exiting = true; Close(); }
        }
        private async Task<JObject> ImportSky(JObject payload)
        {
            JObject before = await bridge.Poll(); int generation = (int?)payload["bridgeGeneration"] ?? -1;
            if ((bool?)before["connected"] != true || (int?)before["connectionGeneration"] != generation) throw new IOException("Minecraft connection changed.");
            using (var dialog = new OpenFileDialog { Title = "Choose your sky image", Filter = "PNG or JPG|*.png;*.jpg;*.jpeg", Multiselect = false })
            {
                if (dialog.ShowDialog(this) != DialogResult.OK) return bridge.State;
                if (new FileInfo(dialog.FileName).Length > 32 * 1024 * 1024) throw new IOException("Choose an image under 32 MB.");
                byte[] data;
                using (var input = Image.FromFile(dialog.FileName))
                {
                    if (input.Width > 16384 || input.Height > 16384 || (long)input.Width * input.Height > 64000000) throw new IOException("Choose an image under 64 megapixels.");
                    double factor = Math.Min(1, 2048.0 / Math.Max(input.Width,input.Height));
                    using (var output = new Bitmap(Math.Max(1,(int)(input.Width*factor)),Math.Max(1,(int)(input.Height*factor))))
                    using (var graphics = Graphics.FromImage(output))
                    using (var bytes = new MemoryStream())
                    {
                        graphics.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
                        graphics.DrawImage(input,0,0,output.Width,output.Height);output.Save(bytes,System.Drawing.Imaging.ImageFormat.Png);data=bytes.ToArray();
                    }
                }
                JObject fresh = await bridge.Poll();
                if ((int?)fresh["connectionGeneration"] != generation || !JToken.DeepEquals(before["worldSession"],fresh["worldSession"])) throw new IOException("Minecraft connection changed; choose the image again.");
                string nested=Path.Combine(minecraft,"tun-tun-26.3");string profile=Directory.Exists(Path.Combine(nested,"config"))?nested:minecraft;
                string dir=Path.Combine(profile,"config","tun-tun-media");Directory.CreateDirectory(dir);string file=Path.Combine(dir,"sky.png");
                if(File.Exists(file))File.Copy(file,Path.Combine(dir,"previous-sky.png"),true);
                File.WriteAllBytes(file+".pending",data);if(File.Exists(file))File.Replace(file+".pending",file,null);else File.Move(file+".pending",file);
                return await bridge.Send("action",new JObject{["action"]="media_setting",["key"]="sky",["enabled"]=true,["bridgeGeneration"]=generation});
            }
        }
        private JObject Preferences()
        {
            return new JObject { ["minecraftFolder"] = minecraft, ["soundsEnabled"] = sounds, ["simpleVoiceChat"] = true, ["installing"] = installing, ["installStatus"] = installStatus, ["launcherAvailable"] = true, ["version"] = "1.30.2" };
        }
        private JObject Presented(JObject state) { state["native"] = Preferences(); return state; }
        private void Post(object message) { if (!IsDisposed && web.CoreWebView2 != null) web.CoreWebView2.PostWebMessageAsJson(Newtonsoft.Json.JsonConvert.SerializeObject(message)); }
        private async Task Poll()
        {
            if (ready) Post(new { kind = "robloxState", state = roblox.State });
            if (!ready || polling || installing) return; polling = true;
            try
            {
                JObject state = await bridge.Poll();
                long? next = (long?)state["openClientSequence"];
                if (openSequence.HasValue && next.HasValue && next > openSequence) ShowClient();
                openSequence = next; Post(new { kind = "state", state = Presented(state) });
            }
            finally { polling = false; }
        }
        private async void OnMessage(object sender, CoreWebView2WebMessageReceivedEventArgs e)
        {
            string id = null;
            try
            {
                if (e.Source != Origin + "/index.html" || e.WebMessageAsJson.Length > 2 * 1024 * 1024) return;
                JObject message = JObject.Parse(e.WebMessageAsJson);
                id = (string)message["id"]; string action = (string)message["action"];
                JObject payload = message["payload"] as JObject ?? new JObject();
                if (String.IsNullOrEmpty(id) || id.Length > 100) return;
                if (action != "saveJSON" && e.WebMessageAsJson.Length > 65536) throw new IOException("Request is too large.");
                object result = new JObject();
                switch (action)
                {
                    case "getPreferences": result = Preferences(); break;
                    case "getState": result = Presented(bridge.State); break;
                    case "robloxState": result = roblox.State; break;
                    case "robloxCommand": result = await roblox.Send(payload); break;
                    case "robloxPair":
                        roblox.Start();
                        if (Directory.Exists(roblox.DirectoryPath)) Process.Start(new ProcessStartInfo("explorer.exe", Quote(roblox.DirectoryPath)) { UseShellExecute = true });
                        result = roblox.State;
                        break;
                    case "robloxSetup":
                        string runtimeFolder = Path.Combine(bundle, "roblox");
                        if (!Directory.Exists(runtimeFolder)) throw new IOException("The compatible runtime files are missing. Extract the complete client ZIP.");
                        Process.Start(new ProcessStartInfo("explorer.exe", Quote(runtimeFolder)) { UseShellExecute = true });
                        result = roblox.State; break;
                    case "openRoblox":
                        Process.Start(new ProcessStartInfo("https://www.roblox.com/home") { UseShellExecute = true });
                        result = new JObject { ["opened"] = true, ["connected"] = false, ["message"] = "Roblox opened. A compatible runtime is still required for game controls." }; break;
                    case "importSky": result = Presented(await ImportSky(payload)); break;
                    case "module": case "settings": case "action":
                        result = Presented(await bridge.Send(action, payload));
                        if (action == "action" && (string)payload["action"] == "voice_mix_test") { FocusMinecraft(); Hide(); }
                        break;
                    case "resume":
                        JObject state = bridge.State;
                        if ((bool?)state["connected"] == true) await bridge.Send("action", new JObject { ["action"] = "resume", ["bridgeGeneration"] = state["connectionGeneration"] });
                        FocusMinecraft(); Hide(); break;
                    case "hide": case "windowClose": Hide(); break;
                    case "install": case "installCore": await Install(); result = Preferences(); break;
                    case "chooseFolder": case "chooseMinecraftFolder":
                        if (installing) throw new IOException("Wait for installation to finish.");
                        using (var dialog = new FolderBrowserDialog { Description = "Choose the Minecraft Launcher .minecraft folder", SelectedPath = minecraft })
                            if (dialog.ShowDialog(this) == DialogResult.OK) { minecraft = dialog.SelectedPath; await bridge.SetFolder(minecraft); SavePreferences(); openSequence = null; }
                        result = Presented(bridge.State); break;
                    case "showFolder": case "showMinecraftFolder":
                        if (Directory.Exists(minecraft)) Process.Start(new ProcessStartInfo("explorer.exe", Quote(minecraft)) { UseShellExecute = true }); break;
                    case "launchMinecraft": LaunchMinecraft(); break;
                    case "openChess":
                        var chessPages = new Dictionary<string,string> { {"play","https://www.chess.com/play/online"}, {"bots","https://www.chess.com/play/computer"}, {"puzzles","https://www.chess.com/puzzles"}, {"analysis","https://www.chess.com/analysis"}, {"learn","https://www.chess.com/lessons"} };
                        string chessSection = (string)payload["section"], chessUrl;
                        if (chessSection == null || !chessPages.TryGetValue(chessSection, out chessUrl)) throw new IOException("Choose a Chess.com tool.");
                        Process.Start(new ProcessStartInfo(chessUrl) { UseShellExecute = true }); result = new JObject { ["opened"] = chessSection }; break;
                    case "microphoneSettings": Process.Start(new ProcessStartInfo("ms-settings:privacy-microphone") { UseShellExecute = true }); break;
                    case "copy": Clipboard.SetText((string)payload["text"] ?? ""); break;
                    case "saveJSON":
                        string json = (string)payload["json"] ?? "{}"; JToken.Parse(json);
                        using (var dialog = new SaveFileDialog { FileName = "Tung-Tung-Findings.json", Filter = "JSON files (*.json)|*.json" })
                            if (dialog.ShowDialog(this) == DialogResult.OK) File.WriteAllText(dialog.FileName, json); break;
                    case "sounds": sounds = (bool?)payload["enabled"] == true; SavePreferences(); result = Preferences(); break;
                    case "playSound": PlaySound((string)payload["name"]); break;
                    case "about": MessageBox.Show("Tung Tung Client 1.30.2\nMinecraft Java 26.3 / Fabric Core 1.30.1\nSimple Voice Chat 2.6.24\nRoblox compatible-runtime tools", Text); break;
                    case "quit": exiting = true; Close(); return;
                    default: throw new IOException("Unknown client action.");
                }
                Post(new { kind = "reply", reply = new { id = id, ok = true, data = result } });
            }
            catch (Exception error) { Post(new { kind = "reply", reply = new { id = id, ok = false, error = error.Message } }); }
        }
        private static string Quote(string value) { return "\"" + value.Replace("\"", "").TrimEnd('\\') + "\""; }
        private async Task Install()
        {
            if (installing) throw new IOException("Installation is already running."); installing = true;
            string root = minecraft;
            if (String.Equals(Path.GetFileName(root.TrimEnd('\\', '/')), "tun-tun-26.3", StringComparison.OrdinalIgnoreCase)) root = Path.GetDirectoryName(root.TrimEnd('\\', '/'));
            try
            {
                installStatus = "Installing Fabric core and Simple Voice Chat…";
                Post(new { kind = "state", state = Presented(bridge.State) });
                string output = await Task.Run(() =>
                {
                    var start = new ProcessStartInfo(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "WindowsPowerShell", "v1.0", "powershell.exe"));
                    start.Arguments = "-NoLogo -NoProfile -ExecutionPolicy Bypass -File " + Quote(Path.Combine(bundle, "desktop", "Install-Core.ps1")) + " -MinecraftPath " + Quote(root);
                    start.WorkingDirectory = bundle; start.UseShellExecute = false; start.CreateNoWindow = true;
                    start.RedirectStandardOutput = true; start.RedirectStandardError = true;
                    using (var process = Process.Start(start))
                    {
                        Task<string> stderr = process.StandardError.ReadToEndAsync(); string stdout = process.StandardOutput.ReadToEnd(); process.WaitForExit();
                        if (process.ExitCode != 0) throw new IOException(stderr.Result.Trim()); return stdout;
                    }
                });
                JObject.Parse(output.Trim()); installStatus = "Installed. Open Minecraft Launcher, select Tung Tung Client 26.3 and Play.";
            }
            catch (Exception error) { installStatus = "Installation failed: " + error.Message; throw; }
            finally { installing = false; Post(new { kind = "state", state = Presented(bridge.State) }); }
        }
        private void SavePreferences()
        {
            Directory.CreateDirectory(data); JObject prefs = Preferences(); prefs["launcher"] = launcher;
            File.WriteAllText(Path.Combine(data, "preferences.json"), prefs.ToString());
        }
        private void LaunchMinecraft()
        {
            if (installing) throw new IOException("Wait for installation to finish.");
            if (!String.IsNullOrEmpty(launcher) && File.Exists(launcher)) { Process.Start(new ProcessStartInfo(launcher) { UseShellExecute = true }); return; }
            foreach (string root in new[] { Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData) + "\\Programs" })
            {
                string candidate = Path.Combine(root, "Minecraft Launcher", "MinecraftLauncher.exe");
                if (File.Exists(candidate)) { launcher = candidate; SavePreferences(); Process.Start(new ProcessStartInfo(launcher) { UseShellExecute = true }); return; }
            }
            // Store installations are opened from Start; do not guess a package ID.
            MessageBox.Show("Open Minecraft Launcher from the Windows Start menu, select Tung Tung Client 26.3 and click Play.", Text);
        }
        private void PlaySound(string name)
        {
            if (!sounds) return;
            string path = Path.Combine(bundle, "ui", "assets", name == "startup" ? "startup-meow.wav" : "ui-click.wav");
            if (!File.Exists(path)) return; if (sound != null) sound.Dispose(); sound = new SoundPlayer(path); sound.Play();
        }
        private delegate bool WindowVisitor(IntPtr window, IntPtr context);
        [DllImport("user32.dll")] private static extern bool EnumWindows(WindowVisitor visitor, IntPtr context);
        [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr window, out uint pid);
        [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetWindowText(IntPtr window, System.Text.StringBuilder caption, int length);
        [DllImport("user32.dll")] private static extern bool SetForegroundWindow(IntPtr window);
        private static void FocusMinecraft()
        {
            EnumWindows((window, context) =>
            {
                uint pid; GetWindowThreadProcessId(window, out pid);
                var caption = new System.Text.StringBuilder(512); GetWindowText(window, caption, caption.Capacity);
                try { if (caption.ToString().StartsWith("Minecraft", StringComparison.OrdinalIgnoreCase) && Process.GetProcessById((int)pid).ProcessName.StartsWith("java", StringComparison.OrdinalIgnoreCase)) { SetForegroundWindow(window); return false; } } catch { }
                return true;
            }, IntPtr.Zero);
        }
    }
}
