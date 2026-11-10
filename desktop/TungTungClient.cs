using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Drawing.Text;
using System.Globalization;
using System.IO;
using System.Net;
using System.Runtime.InteropServices;
using System.Media;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;

namespace TunTun
{
    public static class ClientApp
    {
        public static string GameDirSelected;
        public static Action InstallRequested;

        public static void Run(string bundlePath)
        {
            if (Thread.CurrentThread.GetApartmentState() != ApartmentState.STA)
            {
                Thread ui = new Thread(delegate() { Run(bundlePath); });
                ui.SetApartmentState(ApartmentState.STA);
                ui.Start();
                ui.Join();
                return;
            }
            Application.EnableVisualStyles();
            Application.Run(new ClientWindow(bundlePath));
        }
        public static void RenderPreviews(string bundlePath, string outputDir)
        {
            if (Thread.CurrentThread.GetApartmentState() != ApartmentState.STA)
            {
                Exception failure = null;
                Thread ui = new Thread(delegate() { try { RenderPreviews(bundlePath, outputDir); } catch (Exception ex) { failure = ex; } });
                ui.SetApartmentState(ApartmentState.STA); ui.Start(); ui.Join();
                if (failure != null) throw new InvalidOperationException("Could not render native previews.", failure);
                return;
            }
            Application.EnableVisualStyles();
            Directory.CreateDirectory(outputDir);
            using (ClientWindow preview = new ClientWindow(bundlePath, true))
            {
                preview.StartPosition = FormStartPosition.Manual;
                preview.Location = new Point(-30000, -30000);
                preview.Show(); Application.DoEvents();
                string[] pages = new string[] { "intro", "home", "modules", "findings", "finder_settings", "pvp", "tutorials", "settings", "pvp_settings" };
                string[] names = new string[] { "intro", "home", "world-tools", "findings", "finder-settings", "pvp", "tutorials", "settings", "pvp-settings" };
                for (int i = 0; i < pages.Length; i++) preview.RenderPreview(pages[i], Path.Combine(outputDir, names[i] + ".png"));
            }
        }
    }

    internal sealed class Module
    {
        public string Id, Name, Category, Note, DonutStatus;
        public bool Enabled, Available;
        public Module(string id, string name, string category, bool available, string note, string status)
        { Id = id; Name = name; Category = category; Available = available; Note = note; DonutStatus = status; }
    }

    internal sealed class Finding
    {
        public int X, Y, Z, Score;
        public string Kind, Reasons;
        public string Coordinates { get { return X + " / " + Y + " / " + Z; } }
    }

    internal static class Json
    {
        public static Dictionary<string, object> Object(object value)
        { return value as Dictionary<string, object> ?? new Dictionary<string, object>(); }
        public static object Get(Dictionary<string, object> values, string key)
        { object value; return values.TryGetValue(key, out value) ? value : null; }
        public static string String(Dictionary<string, object> values, string key, string fallback)
        { object value = Get(values, key); return value == null ? fallback : Convert.ToString(value, CultureInfo.InvariantCulture); }
        public static double Number(Dictionary<string, object> values, string key, double fallback)
        { double number; return Double.TryParse(String(values, key, ""), NumberStyles.Any, CultureInfo.InvariantCulture, out number) ? number : fallback; }
        public static bool Boolean(Dictionary<string, object> values, string key, bool fallback)
        { object value = Get(values, key); return value is bool ? (bool)value : fallback; }
        public static IEnumerable Items(object value)
        { return value as IEnumerable ?? new object[0]; }
        public static string Encode(object value) { return new JavaScriptSerializer().Serialize(value); }
    }

    internal sealed class ClientWindow : Form
    {
        private readonly Dashboard view;
        private readonly NotifyIcon tray;
        private readonly System.Windows.Forms.Timer pollTimer;
        private readonly System.Windows.Forms.Timer windowFadeTimer;
        private readonly string bundlePath;
        private readonly bool previewMode;
        private int polling, installing;
        private int port;
        private string token, gameRoot, descriptorFile;
        private DateTime descriptorChecked = DateTime.MinValue;
        private long lastOpenSequence = -1;
        private bool allowExit, closed, resumeOnHide;
        private IntPtr gameWindow = IntPtr.Zero;
        private readonly string preferencesFile;
        private bool startupMuted;
        private DateTime windowFadeStarted;
        private SoundPlayer startupSound;
        private SoundPlayer clickSound;
        private bool resourcesReleased;
        private readonly Queue<UiCommand> commands = new Queue<UiCommand>();
        private readonly object commandSync = new object();
        private bool commandsRunning;
        private sealed class UiCommand
        {
            public readonly string Endpoint; public readonly object Body;
            public readonly Action<Dictionary<string, object>> Applied;
            public readonly Action Failed;
            public UiCommand(string endpoint, object body, Action<Dictionary<string, object>> applied, Action failed) { Endpoint = endpoint; Body = body; Applied = applied; Failed = failed; }
        }

        public ClientWindow(string bundle) : this(bundle, false) { }
        public ClientWindow(string bundle, bool preview)
        {
            previewMode = preview;
            bundlePath = Path.GetFullPath(bundle);
            gameRoot = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".minecraft");
            preferencesFile = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "TunTunClient", "preferences.json");
            if (!previewMode) ReadPreferences();
            ClientApp.GameDirSelected = gameRoot;
            Text = "Tung Tung Client";
            BackColor = Color.Black;
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.None;
            ClientSize = new Size(1180, 760);
            MinimumSize = new Size(1000, 660);
            KeyPreview = true;
            Icon = SystemIcons.Application;
            Opacity = previewMode ? 1 : 0;
            view = new Dashboard(this, bundlePath, previewMode);
            view.Dock = DockStyle.Fill;
            Controls.Add(view);
            if (!previewMode)
            {
                tray = new NotifyIcon(); tray.Icon = SystemIcons.Application;
                tray.Text = "Tung Tung Client";
                ContextMenuStrip menu = new ContextMenuStrip();
                menu.Items.Add("Open Tung Tung Client", null, delegate { ShowClient(); });
                menu.Items.Add("Exit", null, delegate { allowExit = true; Close(); });
                tray.ContextMenuStrip = menu; tray.DoubleClick += delegate { ShowClient(); }; tray.Visible = true;
            }
            pollTimer = new System.Windows.Forms.Timer();
            pollTimer.Interval = 250;
            pollTimer.Tick += delegate { Poll(); };
            if (!previewMode) pollTimer.Start();
            windowFadeTimer = new System.Windows.Forms.Timer(); windowFadeTimer.Interval = 15;
            windowFadeTimer.Tick += delegate
            {
                double progress = Math.Max(0, Math.Min(1, (DateTime.UtcNow - windowFadeStarted).TotalMilliseconds / 180));
                Opacity = progress;
                if (progress >= 1) windowFadeTimer.Stop();
            };
            Shown += delegate
            {
                if (previewMode) return;
                view.StartIntro(); StartWindowFade(); PlayStartupSound();
            };
            KeyDown += delegate(object sender, KeyEventArgs e)
            {
                if ((e.KeyCode == Keys.P || e.KeyCode == Keys.Escape) && !view.IsTyping && view.TrySkipIntro())
                { e.Handled = true; e.SuppressKeyPress = true; return; }
                if (e.KeyCode == Keys.P && e.Modifiers == Keys.None && !view.IsTyping)
                { e.Handled = true; e.SuppressKeyPress = true; HideClient(); }
                else if (e.KeyCode == Keys.Escape && !view.IsTyping) HideClient();
            };
            FormClosed += delegate { ReleaseResources(); };
        }
        private void ReleaseResources()
        {
            if (resourcesReleased) return;
            resourcesReleased = true; closed = true;
            if (pollTimer != null) { pollTimer.Stop(); pollTimer.Dispose(); }
            if (windowFadeTimer != null) { windowFadeTimer.Stop(); windowFadeTimer.Dispose(); }
            if (tray != null) { tray.Visible = false; tray.Dispose(); }
            if (startupSound != null) startupSound.Dispose();
            if (clickSound != null) clickSound.Dispose();
        }
        protected override void Dispose(bool disposing)
        { if (disposing) ReleaseResources(); base.Dispose(disposing); }
        public bool StartupMuted { get { return startupMuted; } }
        public void ToggleStartupSound()
        {
            startupMuted = !startupMuted; WritePreferences();
            if (startupMuted && startupSound != null) startupSound.Stop();
            if (startupMuted && clickSound != null) clickSound.Stop();
            view.Notify(startupMuted ? "Client sounds muted." : "Client sounds enabled. Startup meow plays next launch.");
        }
        public void PlayClickSound()
        {
            if (previewMode || startupMuted) return;
            try
            {
                string sound = Path.Combine(bundlePath, "desktop", "assets", "ui-click.wav");
                if (clickSound == null && File.Exists(sound)) clickSound = new SoundPlayer(sound);
                if (clickSound != null) clickSound.Play();
            }
            catch { }
        }
        private void PlayStartupSound()
        {
            if (previewMode || startupMuted) return;
            string sound = Path.Combine(bundlePath, "desktop", "assets", "startup-meow.wav");
            try { if (File.Exists(sound)) { startupSound = new SoundPlayer(sound); startupSound.Play(); } } catch { }
        }
        private void StartWindowFade()
        {
            if (previewMode) return;
            Opacity = 0; windowFadeStarted = DateTime.UtcNow; windowFadeTimer.Start();
        }
        internal void RenderPreview(string page, string file)
        {
            view.SetPreviewPage(page); view.CreateControl(); Application.DoEvents();
            using (Bitmap picture = new Bitmap(1180, 760))
            { view.DrawToBitmap(picture, new Rectangle(0, 0, 1180, 760)); picture.Save(file, ImageFormat.Png); }
        }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            if (!allowExit && e.CloseReason == CloseReason.UserClosing) { e.Cancel = true; HideClient(); }
            base.OnFormClosing(e);
        }
        protected override bool ShowWithoutActivation
        { get { return previewMode || base.ShowWithoutActivation; } }

        public void ExitClient() { allowExit = true; Close(); }
        public void HideClient()
        {
            Hide(); windowFadeTimer.Stop(); Opacity = 1;
            if (resumeOnHide)
            {
                resumeOnHide = false;
                if (gameWindow != IntPtr.Zero && IsWindow(gameWindow)) SetForegroundWindow(gameWindow);
                if (view.Connected) Send("/action", new { action = "resume" });
            }
        }
        public void MinimizeClient() { WindowState = FormWindowState.Minimized; }
        public void ShowClient()
        {
            view.BeginOpeningFade(); StartWindowFade();
            TopMost = true;
            Show(); WindowState = FormWindowState.Normal; Activate(); BringToFront();
            BeginInvoke(new Action(delegate { if (!IsDisposed) TopMost = false; }));
        }
        public string GameRoot { get { return gameRoot; } }
        public bool Installing { get { return Interlocked.CompareExchange(ref installing, 0, 0) != 0; } }

        [DllImport("user32.dll")] private static extern bool ReleaseCapture();
        [DllImport("user32.dll")] private static extern IntPtr SendMessage(IntPtr window, int message, IntPtr wParam, IntPtr lParam);
        [DllImport("user32.dll")] private static extern IntPtr GetForegroundWindow();
        [DllImport("user32.dll")] private static extern bool SetForegroundWindow(IntPtr handle);
        [DllImport("user32.dll")] private static extern bool IsWindow(IntPtr handle);
        [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr handle, out uint processId);
        [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetWindowText(IntPtr handle, StringBuilder text, int length);
        private void CaptureGameWindow()
        {
            IntPtr foreground = GetForegroundWindow();
            StringBuilder caption = new StringBuilder(512); GetWindowText(foreground, caption, caption.Capacity);
            gameWindow = IntPtr.Zero;
            if (caption.ToString().IndexOf("Minecraft", StringComparison.OrdinalIgnoreCase) < 0) return;
            try
            {
                uint processId; GetWindowThreadProcessId(foreground, out processId);
                using (Process process = Process.GetProcessById((int)processId))
                    if (process.ProcessName.Equals("javaw", StringComparison.OrdinalIgnoreCase) || process.ProcessName.Equals("java", StringComparison.OrdinalIgnoreCase)) gameWindow = foreground;
            }
            catch { }
        }
        public void DragWindow() { ReleaseCapture(); SendMessage(Handle, 0xA1, new IntPtr(2), IntPtr.Zero); }
        protected override void WndProc(ref Message message)
        {
            if (message.Msg == 0x84 && WindowState == FormWindowState.Normal)
            {
                base.WndProc(ref message);
                if ((int)message.Result == 1)
                {
                    Point p = PointToClient(new Point((short)((long)message.LParam & 0xffff), (short)(((long)message.LParam >> 16) & 0xffff)));
                    const int edge = 7;
                    bool left = p.X < edge, right = p.X >= ClientSize.Width - edge;
                    bool top = p.Y < edge, bottom = p.Y >= ClientSize.Height - edge;
                    int hit = left && top ? 13 : right && top ? 14 : left && bottom ? 16 : right && bottom ? 17 : left ? 10 : right ? 11 : top ? 12 : bottom ? 15 : 1;
                    message.Result = new IntPtr(hit);
                }
                return;
            }
            base.WndProc(ref message);
        }

        public void BrowseGameRoot()
        {
            using (FolderBrowserDialog picker = new FolderBrowserDialog())
            {
                picker.Description = "Choose your .minecraft folder or Tung Tung Client profile folder";
                if (Directory.Exists(gameRoot)) picker.SelectedPath = gameRoot;
                if (picker.ShowDialog(this) != DialogResult.OK) return;
                gameRoot = picker.SelectedPath;
                ClientApp.GameDirSelected = gameRoot;
                port = 0; token = null; descriptorChecked = DateTime.MinValue; lastOpenSequence = -1;
                WritePreferences(); view.Notify("Minecraft folder updated. Connecting…"); view.Invalidate();
            }
        }

        public void InstallCore()
        {
            if (Installing) return;
            if (ClientApp.InstallRequested != null) { ClientApp.InstallRequested(); return; }
            string installer = Path.Combine(bundlePath, "desktop", "Install-Core.ps1");
            if (!File.Exists(installer)) { view.Notify("Install-Core.ps1 is missing. Extract the full ZIP first."); return; }
            Interlocked.Exchange(ref installing, 1);
            view.Notify("Installing Fabric profile… Keep this window open."); view.Invalidate();
            string selectedRoot = gameRoot;
            if (String.Equals(Path.GetFileName(selectedRoot.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar)), "tun-tun-26.3", StringComparison.OrdinalIgnoreCase))
                selectedRoot = Path.GetDirectoryName(selectedRoot.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar));
            ThreadPool.QueueUserWorkItem(delegate
            {
                string result;
                try
                {
                    ProcessStartInfo start = new ProcessStartInfo("powershell.exe");
                    start.Arguments = "-NoProfile -ExecutionPolicy Bypass -File \"" + installer.Replace("\"", "") + "\" -MinecraftPath \"" + selectedRoot.Replace("\"", "").Replace('\\', '/') + "\"";
                    start.UseShellExecute = false; start.CreateNoWindow = true;
                    start.RedirectStandardOutput = true; start.RedirectStandardError = true;
                    start.StandardOutputEncoding = Encoding.UTF8; start.StandardErrorEncoding = Encoding.UTF8;
                    start.WorkingDirectory = bundlePath;
                    using (Process process = new Process())
                    {
                        process.StartInfo = start;
                        StringBuilder stdout = new StringBuilder(), stderr = new StringBuilder();
                        process.OutputDataReceived += delegate(object s, DataReceivedEventArgs e) { if (e.Data != null) stdout.AppendLine(e.Data); };
                        process.ErrorDataReceived += delegate(object s, DataReceivedEventArgs e) { if (e.Data != null) stderr.AppendLine(e.Data); };
                        process.Start(); process.BeginOutputReadLine(); process.BeginErrorReadLine(); process.WaitForExit();
                        if (process.ExitCode != 0)
                        {
                            string detail = stderr.ToString().Trim();
                            if (detail.Length == 0) detail = stdout.ToString().Trim();
                            result = "Installation failed: " + (detail.Length > 210 ? detail.Substring(0, 210) : detail);
                        }
                        else
                        {
                            result = "Installed. Restart Minecraft Launcher, choose Tung Tung Client 26.3, then Play.";
                            try
                            {
                                Dictionary<string, object> answer = Json.Object(new JavaScriptSerializer().DeserializeObject(stdout.ToString().Trim()));
                                string profileName = Json.String(answer, "ProfileName", "Tung Tung Client 26.3");
                                result = Json.String(answer, "message", "Installed. Restart Minecraft Launcher, choose " + profileName + ", then Play.");
                            }
                            catch { }
                        }
                    }
                }
                catch (Exception ex) { result = "Installation failed: " + ex.Message; }
                Interlocked.Exchange(ref installing, 0);
                Ui(delegate { descriptorChecked = DateTime.MinValue; view.Notify(result); view.Invalidate(); });
            });
        }

        private void ReadPreferences()
        {
            try
            {
                if (!File.Exists(preferencesFile)) return;
                Dictionary<string, object> prefs = Json.Object(new JavaScriptSerializer().DeserializeObject(File.ReadAllText(preferencesFile)));
                gameRoot = Json.String(prefs, "gameRoot", gameRoot);
                startupMuted = Json.Boolean(prefs, "startupMuted", false);
            }
            catch { }
        }
        private void WritePreferences()
        {
            if (previewMode) return;
            try { Directory.CreateDirectory(Path.GetDirectoryName(preferencesFile)); File.WriteAllText(preferencesFile, Json.Encode(new { gameRoot = gameRoot, startupMuted = startupMuted }), Encoding.UTF8); }
            catch { }
        }
        private void Discover()
        {
            if ((DateTime.UtcNow - descriptorChecked).TotalSeconds < 4 && port != 0) return;
            descriptorChecked = DateTime.UtcNow;
            string[] candidates = new string[]
            {
                Path.Combine(gameRoot, "tun-tun-26.3", "config", "tun-tun-connection.json"),
                Path.Combine(gameRoot, "config", "tun-tun-connection.json")
            };
            port = 0; token = null;
            foreach (string candidate in candidates)
            {
                try
                {
                    if (!File.Exists(candidate)) continue;
                    Dictionary<string, object> descriptor = Json.Object(new JavaScriptSerializer().DeserializeObject(File.ReadAllText(candidate)));
                    int foundPort = (int)Json.Number(descriptor, "port", 0);
                    string foundToken = Json.String(descriptor, "token", "");
                    if (foundPort < 1 || foundPort > 65535 || foundToken.Length < 16) continue;
                    port = foundPort; token = foundToken; descriptorFile = candidate; return;
                }
                catch { }
            }
        }
        private string Request(string endpoint, object body)
        {
            int requestPort = port;
            string requestToken = token;
            if (requestPort == 0 || String.IsNullOrEmpty(requestToken)) throw new IOException("Open the Tung Tung Client Minecraft profile first.");
            HttpWebRequest request = (HttpWebRequest)WebRequest.Create("http://127.0.0.1:" + requestPort + endpoint);
            request.Proxy = null; request.Timeout = body == null ? 1500 : 4500; request.ReadWriteTimeout = request.Timeout;
            request.ServicePoint.Expect100Continue = false;
            request.AllowAutoRedirect = false;
            request.Headers["X-Tun-Token"] = requestToken;
            request.KeepAlive = false;
            if (body != null)
            {
                request.Method = "POST"; request.ContentType = "application/json; charset=utf-8";
                byte[] bytes = Encoding.UTF8.GetBytes(Json.Encode(body)); request.ContentLength = bytes.Length;
                using (Stream output = request.GetRequestStream()) output.Write(bytes, 0, bytes.Length);
            }
            using (WebResponse response = request.GetResponse())
            using (StreamReader input = new StreamReader(response.GetResponseStream(), Encoding.UTF8)) return input.ReadToEnd();
        }
        private void Poll()
        {
            if (closed || Interlocked.CompareExchange(ref polling, 1, 0) != 0) return;
            ThreadPool.QueueUserWorkItem(delegate
            {
                try
                {
                    Discover();
                    if (port == 0) { Ui(delegate { view.Disconnect("Open Minecraft with the Tung Tung Client profile to connect."); }); return; }
                    Dictionary<string, object> state = Json.Object(new JavaScriptSerializer().DeserializeObject(Request("/state", null)));
                    Ui(delegate
                    {
                        view.UpdateState(state);
                        long sequence = (long)Json.Number(state, "openClientSequence", 0);
                        if (lastOpenSequence >= 0 && sequence > lastOpenSequence)
                        {
                            if (Visible && Form.ActiveForm == this) HideClient();
                            else { CaptureGameWindow(); resumeOnHide = true; ShowClient(); }
                        }
                        lastOpenSequence = sequence;
                    });
                }
                catch { port = 0; token = null; Ui(delegate { view.Disconnect("Minecraft is offline. Start the Tung Tung Client profile."); }); }
                finally { Interlocked.Exchange(ref polling, 0); }
            });
        }
        private void Ui(Action action)
        {
            if (closed || IsDisposed || !IsHandleCreated) return;
            try { BeginInvoke(action); } catch (InvalidOperationException) { }
        }
        public void Send(string endpoint, object body)
        { Send(endpoint, body, null, null); }
        public void Send(string endpoint, object body, Action<Dictionary<string, object>> applied)
        { Send(endpoint, body, applied, null); }
        public void Send(string endpoint, object body, Action<Dictionary<string, object>> applied, Action failed)
        {
            if (!view.Connected) { view.Notify("Connect Minecraft first. Install the core or select its game folder."); return; }
            lock (commandSync)
            {
                commands.Enqueue(new UiCommand(endpoint, body, applied, failed));
                if (commandsRunning) return;
                commandsRunning = true;
            }
            ThreadPool.QueueUserWorkItem(delegate
            {
                while (true)
                {
                    UiCommand command;
                    lock (commandSync)
                    {
                        if (closed || commands.Count == 0) { commands.Clear(); commandsRunning = false; return; }
                        command = commands.Dequeue();
                    }
                    try
                    {
                        Dictionary<string, object> result = Json.Object(new JavaScriptSerializer().DeserializeObject(Request(command.Endpoint, command.Body)));
                        string message = Json.String(result, "message", "Applied to Minecraft.");
                        Ui(delegate { view.Notify(message); if (command.Applied != null) command.Applied(result); });
                    }
                    catch (Exception ex) { Ui(delegate { view.Notify("Could not apply: " + ex.Message); if (command.Failed != null) command.Failed(); }); }
                }
            });
        }
    }

    internal sealed class Dashboard : Control
    {
        private readonly ClientWindow window;
        private readonly bool previewMode;
        private readonly TextBox search;
        private readonly TextBox friends;
        private readonly Font body = new Font("Segoe UI", 10f, FontStyle.Regular);
        private readonly Font small = new Font("Segoe UI", 8f, FontStyle.Regular);
        private readonly Font medium = new Font("Segoe UI", 12f, FontStyle.Bold);
        private readonly Font title = new Font("Segoe UI", 24f, FontStyle.Bold);
        private readonly Font brand = new Font("Segoe UI", 21f, FontStyle.Bold);
        private readonly Font sidebarBrand = new Font("Segoe UI", 17f, FontStyle.Bold);
        private readonly Font mono = new Font("Consolas", 10f, FontStyle.Regular);
        private readonly Color crimson = Color.FromArgb(255, 51, 100);
        private readonly Color wood = Color.FromArgb(200, 145, 85);
        private Color red { get { return page == "pvp" || page == "pvp_settings" ? crimson : wood; } }
        private readonly Color text = Color.FromArgb(242, 235, 225);
        private readonly Color muted = Color.FromArgb(147, 137, 125);
        private Color line { get { return page == "pvp" || page == "pvp_settings" ? Color.FromArgb(47, 29, 41) : Color.FromArgb(50, 37, 24); } }
        private readonly List<Module> modules = new List<Module>();
        private readonly List<Finding> findings = new List<Finding>();
        private readonly List<Hit> hits = new List<Hit>();
        private readonly string[] categories = new string[] { "All modules", "Visual", "Finder", "Camera", "Movement", "Utility" };
        private string page = "home", category = "All modules", server = "No world connected", connectionNote = "Minecraft is offline", toast = "", selectedModule = "chunk_finder";
        private DateTime toastExpires;
        private bool connected, scanning, tracers = true;
        private int sensitivity = 65, radiusChunks = 12, espRange = 128, scroll, selectedFinding = -1;
        private double pvpRange = 4, pvpMaxSelfDamage = 6, pvpMinTargetDamage = 4, pvpTotemHealth = 10;
        private int pvpDelayTicks = 6;
        private bool pvpTargetPlayers = true, pvpTargetHostiles, friendsDirty;
        private string pvpFriends = "";
        private readonly string[] finderKeys = new string[] { "chest", "spawner", "barrel", "shulker", "ender_chest", "hopper", "furnace", "crafting", "enchanting", "anvil", "beacon", "armor_stand" };
        private readonly string[] finderNames = new string[] { "Chests", "Spawners", "Barrels", "Shulker boxes", "Ender chests", "Hoppers", "Furnaces", "Crafting tables", "Enchanting tables", "Anvils", "Beacons", "Armor stands" };
        private readonly Dictionary<string, bool> finderTargets = new Dictionary<string, bool>();
        private bool finderTargetsDirty, finderTargetsSupported = true;
        private bool finderTargetsAwaitingResponse;
        private int finderTargetsRevision;
        private double playerX, playerY, playerZ;
        private float scale = 1f, offsetX, offsetY;
        private PointF mouse;
        private int activeSlider = -1;
        private Image mascot;
        private Image pvpMascot, findingsMascot, dancer, logo;
        private readonly Dictionary<Image, Rectangle> imageBounds = new Dictionary<Image, Rectangle>();
        private readonly Dictionary<string, Image> moduleItems = new Dictionary<string, Image>();
        private readonly string itemFolder;
        private string selectedGuide = "chunk_finder";
        private readonly System.Windows.Forms.Timer animation;
        private double pulse;
        private bool introActive = true, openingFade;
        private float openingInitialAlpha = 1;
        private DateTime introStarted = DateTime.UtcNow, openingStarted;

        private sealed class Hit
        {
            public RectangleF Bounds; public string Action, Id; public int Value;
            public Hit(RectangleF bounds, string action, string id, int value) { Bounds = bounds; Action = action; Id = id; Value = value; }
        }
        public bool Connected { get { return connected; } }
        public bool IsTyping { get { return search.Focused || friends.Focused; } }
        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern IntPtr SendMessage(IntPtr handle, int message, IntPtr wParam, string lParam);

        public Dashboard(ClientWindow parent, string bundlePath, bool preview)
        {
            window = parent;
            previewMode = preview;
            foreach (string key in finderKeys) finderTargets[key] = true;
            DoubleBuffered = true;
            SetStyle(ControlStyles.ResizeRedraw | ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);
            search = new TextBox();
            search.BorderStyle = BorderStyle.None;
            search.BackColor = Color.FromArgb(25, 21, 30); search.ForeColor = text; search.Font = body;
            search.HandleCreated += delegate { SendMessage(search.Handle, 0x1501, new IntPtr(1), "Search modules…"); };
            search.TextChanged += delegate { scroll = 0; Invalidate(); };
            search.MouseDown += delegate(object sender, MouseEventArgs e) { if (e.Button == MouseButtons.Left) window.PlayClickSound(); };
            search.KeyDown += delegate(object sender, KeyEventArgs e) { if (e.KeyCode == Keys.Escape) { search.Text = ""; Focus(); e.Handled = true; } };
            Controls.Add(search);
            friends = new TextBox();
            friends.BorderStyle = BorderStyle.None; friends.MaxLength = 512;
            friends.BackColor = Color.FromArgb(25, 21, 30); friends.ForeColor = text; friends.Font = body;
            friends.Visible = false; friends.Enabled = false;
            friends.HandleCreated += delegate { SendMessage(friends.Handle, 0x1501, new IntPtr(1), "PlayerOne, PlayerTwo"); };
            friends.TextChanged += delegate { pvpFriends = friends.Text; friendsDirty = true; };
            friends.MouseDown += delegate(object sender, MouseEventArgs e) { if (e.Button == MouseButtons.Left) window.PlayClickSound(); };
            friends.KeyDown += delegate(object sender, KeyEventArgs e)
            {
                if (e.KeyCode == Keys.Enter) { SendSettings(); Focus(); e.Handled = true; e.SuppressKeyPress = true; }
                else if (e.KeyCode == Keys.Escape) { Focus(); e.Handled = true; e.SuppressKeyPress = true; }
            };
            Controls.Add(friends);
            Catalog();
            string assetFolder = Path.Combine(bundlePath, "desktop", "assets");
            itemFolder = Path.Combine(assetFolder, "items");
            mascot = LoadImage(Path.Combine(assetFolder, "world-mascot.png"));
            if (mascot == null) mascot = LoadImage(Path.Combine(assetFolder, "tun-tun.png"));
            pvpMascot = LoadImage(Path.Combine(assetFolder, "pvp-mascot.png"));
            findingsMascot = LoadImage(Path.Combine(assetFolder, "findings-mascot.png"));
            dancer = LoadImage(Path.Combine(assetFolder, "dancer.png"));
            logo = LoadImage(Path.Combine(assetFolder, "logo.png"));
            animation = new System.Windows.Forms.Timer(); animation.Interval = 33;
            animation.Tick += delegate
            {
                pulse += 0.15;
                bool changed = false;
                if (introActive && (DateTime.UtcNow - introStarted).TotalMilliseconds >= 2100) { introActive = false; changed = true; }
                if (openingFade && (DateTime.UtcNow - openingStarted).TotalMilliseconds >= 500) { openingFade = false; changed = true; }
                if (changed) LayoutInputs();
                if (Visible) Invalidate();
            };
            if (!previewMode) animation.Start();
            LayoutInputs();
        }
        protected override void Dispose(bool disposing)
        {
            if (disposing) { animation.Stop(); animation.Dispose(); if (mascot != null) mascot.Dispose(); if (pvpMascot != null) pvpMascot.Dispose(); if (findingsMascot != null) findingsMascot.Dispose(); if (dancer != null) dancer.Dispose(); if (logo != null) logo.Dispose(); foreach (Image item in moduleItems.Values) if (item != null) item.Dispose(); body.Dispose(); small.Dispose(); medium.Dispose(); title.Dispose(); brand.Dispose(); sidebarBrand.Dispose(); mono.Dispose(); }
            base.Dispose(disposing);
        }
        public void StartIntro()
        { introActive = true; openingFade = false; introStarted = DateTime.UtcNow; page = "home"; LayoutInputs(); Invalidate(); }
        public void BeginOpeningFade()
        {
            if (introActive || previewMode) return;
            openingFade = true; openingInitialAlpha = 1; openingStarted = DateTime.UtcNow; LayoutInputs(); Invalidate();
        }
        public bool TrySkipIntro()
        {
            if (!introActive) return false;
            double elapsed = (DateTime.UtcNow - introStarted).TotalMilliseconds;
            openingInitialAlpha = elapsed < 1600 ? 1 : (float)Math.Max(0, 1 - (elapsed - 1600) / 500);
            introActive = false; openingFade = true; openingStarted = DateTime.UtcNow; LayoutInputs(); Invalidate(); return true;
        }
        public void SetPreviewPage(string previewPage)
        {
            page = previewPage == "intro" ? "home" : previewPage;
            introActive = previewPage == "intro"; introStarted = DateTime.UtcNow;
            openingFade = false; openingInitialAlpha = 1; pulse = 0.75; LayoutInputs(); Invalidate();
        }
        private Image LoadImage(string file)
        {
            try
            {
                if (!File.Exists(file)) return null;
                Bitmap picture;
                using (FileStream input = File.OpenRead(file)) using (Image original = Image.FromStream(input)) picture = new Bitmap(original);
                imageBounds[picture] = AlphaBounds(picture); return picture;
            }
            catch { return null; }
        }
        private static Rectangle AlphaBounds(Bitmap picture)
        {
            int minX = picture.Width, minY = picture.Height, maxX = -1, maxY = -1;
            BitmapData data = null;
            try
            {
                data = picture.LockBits(new Rectangle(0, 0, picture.Width, picture.Height), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
                byte[] row = new byte[picture.Width * 4];
                for (int y = 0; y < picture.Height; y++)
                {
                    Marshal.Copy(IntPtr.Add(data.Scan0, y * data.Stride), row, 0, row.Length);
                    for (int x = 0; x < picture.Width; x++) if (row[x * 4 + 3] > 8)
                    { minX = Math.Min(minX, x); maxX = Math.Max(maxX, x); minY = Math.Min(minY, y); maxY = Math.Max(maxY, y); }
                }
            }
            catch { return new Rectangle(0, 0, picture.Width, picture.Height); }
            finally { if (data != null) picture.UnlockBits(data); }
            return maxX >= minX && maxY >= minY ? Rectangle.FromLTRB(minX, minY, maxX + 1, maxY + 1) : new Rectangle(0, 0, picture.Width, picture.Height);
        }
        private void DrawAsset(Graphics g, Image picture, RectangleF target, float opacity, bool animate)
        {
            if (picture == null || opacity <= 0) return;
            Rectangle source; if (!imageBounds.TryGetValue(picture, out source)) source = new Rectangle(0, 0, picture.Width, picture.Height);
            float fit = Math.Min(target.Width / source.Width, target.Height / source.Height);
            RectangleF actual = new RectangleF(target.X + (target.Width - source.Width * fit) / 2, target.Y + (target.Height - source.Height * fit) / 2, source.Width * fit, source.Height * fit);
            GraphicsState saved = g.Save();
            if (animate)
            {
                float centerX = actual.X + actual.Width / 2, centerY = actual.Y + actual.Height / 2;
                g.TranslateTransform(centerX, centerY + (float)Math.Sin(pulse) * 6f);
                g.RotateTransform((float)Math.Sin(pulse) * 3.5f); g.TranslateTransform(-centerX, -centerY);
            }
            using (ImageAttributes attributes = new ImageAttributes())
            {
                ColorMatrix matrix = new ColorMatrix(); matrix.Matrix33 = Math.Max(0, Math.Min(1, opacity)); attributes.SetColorMatrix(matrix);
                g.DrawImage(picture, Rectangle.Round(actual), source.X, source.Y, source.Width, source.Height, GraphicsUnit.Pixel, attributes);
            }
            g.Restore(saved);
        }
        private void DrawModuleIcon(Graphics g, Module module, RectangleF bounds, Color fallbackColor)
        {
            Image item;
            if (!moduleItems.TryGetValue(module.Id, out item))
            { item = LoadImage(Path.Combine(itemFolder, module.Id + ".png")); moduleItems[module.Id] = item; }
            if (item == null) { Icon(g, ModuleIcon(module.Id), bounds, fallbackColor); return; }
            GraphicsState saved = g.Save();
            g.InterpolationMode = InterpolationMode.NearestNeighbor; g.PixelOffsetMode = PixelOffsetMode.Half;
            DrawAsset(g, item, bounds, 1, false); g.Restore(saved);
        }
        private void Catalog()
        {
            Add("chunk_finder", "Chunk Finder", "Finder", true, "Twelve received signal types. Solid-color models: orange chests. Matching chunks use one color band per type. View-center tracers; amethyst stays separate.", "Client visuals · detection unknown");
            Add("storage_esp", "Storage ESP", "Visual", true, "Solid-color highlights for received container models; chests are orange. Optional tracers start at the view center.", "Client visuals · detection unknown");
            Add("amethyst_esp", "Amethyst ESP", "Visual", true, "Purple solid-color highlights for received budding amethyst and crystals. Budding grows via random ticks; existing crystals do not prove player presence.", "Client visuals · detection unknown");
            Add("xray", "Shadow Byte X-ray", "Visual", true, "Hides common terrain, water and lava from received chunks. Vanilla Fabric rendering only; server-hidden blocks remain unknown.", "Client visuals · detection unknown");
            Add("player_esp", "Player ESP", "Visual", true, "Solid-color highlights for players already received by your client, with optional tracers from the view center. Normal server tracking range still applies.", "Client visuals · detection unknown");
            Add("freelook", "Freelook", "Camera", true, "Look around independently of your movement direction. The local camera does not change your player's aim.", "Local camera · detection unknown");
            Add("freecam", "Freecam", "Camera", true, "Move the local camera independently of the player. The body remains vulnerable and only server-loaded chunks are available.", "Local camera · detection unknown");
            Add("base_chunks", "Base Chunks", "Finder", true, "Optional chunk boundaries for stronger activity candidates. A chunk marker is evidence, rather than proof of a player base.", "Client visuals · detection unknown");
            Add("player_chunks", "Player Chunks", "Finder", true, "Optional chunk boundaries around players received from the server. Use Player ESP for model highlights.", "Client visuals · detection unknown");
            Add("spawner_chunks", "Spawner Chunks", "Finder", true, "Optional chunk boundaries for received normal and trial spawners. Spawners can be naturally generated.", "Client visuals · detection unknown");
            Add("player_detection", "Player Detection", "Finder", true, "Show tracked players in the loaded world.", "Client visuals · detection unknown");
            Add("auto_totem", "Auto Totem", "PvP", true, "Move a totem into the offhand when empty or health reaches your configured threshold. Inventory actions are visible to the server.", "Server-visible inventory");
            Add("killaura", "Kill Aura", "PvP", true, "Attack visible hostile mobs within 3 blocks with full game cooldown. The legacy hostile-mob targeting remains separate from shared PvP target settings.", "Server-visible combat");
            Add("auto_crystal", "Auto Crystal", "PvP", true, "Use end crystals around eligible nearby targets. Shared range, delay, target and estimated explosion-damage limits apply.", "Server-visible crystal actions");
            Add("auto_anchor", "Auto Anchor", "PvP", true, "Use charged respawn anchors around eligible nearby targets. Shared delay and estimated explosion-damage limits apply.", "Server-visible anchor actions");
            Add("auto_gapple", "Auto Gapple", "PvP", true, "Use a golden apple when the configured health threshold is reached and an apple is available. Item use is visible to the server.", "Server-visible item use");
            Add("trigger_bot", "Trigger Bot", "PvP", true, "Attack an eligible target under your crosshair using normal game attacks and cooldown. Shared target and friend settings apply.", "Server-visible combat");
            Add("auto_weapon", "Auto Weapon", "PvP", true, "Select an available hotbar weapon for eligible nearby targets. Shared target and friend settings apply; inventory selection is server-visible.", "Server-visible inventory");
            Add("flight", "Flight", "Movement", false, "Needs a server-granted flight ability. A desktop app cannot grant flight on a remote server; detection timing is unverified.", "High anti-cheat exposure · timing unverified");
            Add("noclip", "NoClip", "Movement", false, "Needs server support. Client-side movement cannot change authoritative collisions; detection timing is unverified.", "High anti-cheat exposure · timing unverified");
            Add("boat_fly", "Boat Fly", "Movement", false, "Unavailable. No vehicle or anti-cheat bypass is included; detection timing is unverified.", "High anti-cheat exposure · timing unverified");
            Add("fast_swim", "Fast Swim", "Movement", false, "Unavailable. Swimming speed is governed by the game and server; detection timing is unverified.", "High anti-cheat exposure · timing unverified");
            Add("super_trident", "Super Trident", "Movement", false, "Unavailable. No modified trident movement is sent; detection timing is unverified.", "High anti-cheat exposure · timing unverified");
            Add("deepslate_bypass", "Deepslate Bypass", "Utility", false, "Unavailable. Server-side hidden block data cannot be reconstructed by this app.", "Hidden server data · detection unknown");
            Add("donut_flight_bypass", "Donut Flight Bypass", "Movement", false, "Unavailable. No anti-cheat bypass is implemented; detection timing is unverified.", "High anti-cheat exposure · timing unverified");
            Add("anti_kick", "Anti Kick", "Utility", false, "Unavailable. A client cannot prevent an authoritative server kick.", "Server control · timing unverified");
            Add("auction_sniper", "Auction Sniper", "Utility", false, "Needs a server-specific auction adapter. No automatic purchases are implemented.", "Server adapter · timing unverified");
        }
        private void Add(string id, string name, string cat, bool available, string note, string status)
        { modules.Add(new Module(id, name, cat, available, note, status)); }

        public void Notify(string message) { toast = message; toastExpires = DateTime.UtcNow.AddSeconds(9); Invalidate(); }
        public void Disconnect(string note)
        {
            bool changed = connected || connectionNote != note;
            connected = false; scanning = false; connectionNote = note; server = "No world connected";
            friends.Enabled = false;
            if (changed) { foreach (Module module in modules) module.Enabled = false; findings.Clear(); selectedFinding = -1; Invalidate(); }
        }
        public void UpdateState(Dictionary<string, object> state)
        {
            connected = Json.Boolean(state, "connected", false);
            scanning = Json.Boolean(state, "scanning", false);
            server = Json.String(state, "server", connected ? "Singleplayer" : "No world connected");
            connectionNote = Json.String(state, "message", connected ? "Live bridge ready" : "Join a world to scan loaded chunks.");
            Dictionary<string, object> player = Json.Object(Json.Get(state, "player"));
            playerX = Json.Number(player, "x", 0); playerY = Json.Number(player, "y", 0); playerZ = Json.Number(player, "z", 0);
            Dictionary<string, object> settings = Json.Object(Json.Get(state, "settings"));
            Dictionary<string, object> receivedTargets = Json.Object(Json.Get(settings, "finderTargets"));
            finderTargetsSupported = true;
            bool targetsMatch = true;
            foreach (string key in finderKeys)
            {
                object value = Json.Get(receivedTargets, key);
                if (!(value is bool)) finderTargetsSupported = false;
                if (!(value is bool) || (bool)value != finderTargets[key]) targetsMatch = false;
            }
            if (targetsMatch && !finderTargetsAwaitingResponse) finderTargetsDirty = false;
            if (!finderTargetsDirty)
                foreach (string key in finderKeys)
                { object value = Json.Get(receivedTargets, key); if (value is bool) finderTargets[key] = (bool)value; }
            if (activeSlider < 0)
            {
                sensitivity = (int)Json.Number(settings, "sensitivity", sensitivity);
                radiusChunks = (int)Json.Number(settings, "radiusChunks", radiusChunks);
                espRange = (int)Json.Number(settings, "espRange", espRange);
                tracers = Json.Boolean(settings, "espTracers", tracers);
                pvpRange = Json.Number(settings, "pvpRange", pvpRange);
                pvpDelayTicks = (int)Json.Number(settings, "pvpDelayTicks", pvpDelayTicks);
                pvpMaxSelfDamage = Json.Number(settings, "pvpMaxSelfDamage", pvpMaxSelfDamage);
                pvpMinTargetDamage = Json.Number(settings, "pvpMinTargetDamage", pvpMinTargetDamage);
                pvpTotemHealth = Json.Number(settings, "pvpTotemHealth", pvpTotemHealth);
                pvpTargetPlayers = Json.Boolean(settings, "pvpTargetPlayers", pvpTargetPlayers);
                pvpTargetHostiles = Json.Boolean(settings, "pvpTargetHostiles", pvpTargetHostiles);
            }
            string serverFriends = Json.String(settings, "pvpFriends", pvpFriends);
            if (NormalizeFriends(serverFriends) == NormalizeFriends(pvpFriends)) friendsDirty = false;
            if (!friends.Focused && !friendsDirty)
            {
                pvpFriends = serverFriends;
                if (friends.Text != serverFriends) friends.Text = serverFriends;
                friendsDirty = false;
            }
            friends.Enabled = connected;
            object moduleValues = Json.Get(state, "modules");
            if (moduleValues != null)
            {
                modules.Clear();
                foreach (object value in Json.Items(moduleValues))
                {
                    Dictionary<string, object> info = Json.Object(value);
                    string id = Json.String(info, "id", "");
                    Module m = new Module(id, Json.String(info, "name", "Module"), NormalizeCategory(Json.String(info, "category", "Utility"), id), Json.Boolean(info, "available", false), Json.String(info, "note", ""), Json.String(info, "donutStatus", "Detection unknown"));
                    m.Enabled = Json.Boolean(info, "enabled", false); modules.Add(m);
                }
            }
            string selectedCoordinates = selectedFinding >= 0 && selectedFinding < findings.Count ? findings[selectedFinding].Coordinates : "";
            findings.Clear();
            foreach (object value in Json.Items(Json.Get(state, "findings")))
            {
                Dictionary<string, object> info = Json.Object(value);
                Finding f = new Finding(); f.X = (int)Json.Number(info, "x", 0); f.Y = (int)Json.Number(info, "y", 0); f.Z = (int)Json.Number(info, "z", 0); f.Score = (int)Json.Number(info, "score", 0); f.Kind = Json.String(info, "kind", "Signal");
                object reasons = Json.Get(info, "reasons");
                if (reasons is string) f.Reasons = (string)reasons;
                else { List<string> joined = new List<string>(); foreach (object reason in Json.Items(reasons)) joined.Add(Convert.ToString(reason)); f.Reasons = String.Join(" · ", joined.ToArray()); }
                findings.Add(f);
            }
            findings.Sort(delegate(Finding a, Finding b) { return b.Score.CompareTo(a.Score); });
            selectedFinding = -1;
            for (int i = 0; i < findings.Count; i++) if (findings[i].Coordinates == selectedCoordinates) { selectedFinding = i; break; }
            if (selectedFinding < 0 && findings.Count > 0) selectedFinding = 0;
            Invalidate();
        }
        private static string NormalizeCategory(string cat, string id)
        {
            if (IsPvpModule(id)) return "PvP";
            if (IsMovementModule(id)) return "Movement";
            if (cat.Equals("visuals", StringComparison.OrdinalIgnoreCase)) return "Visual";
            if (cat.Equals("discovery", StringComparison.OrdinalIgnoreCase)) return "Finder";
            if (cat.Equals("automation", StringComparison.OrdinalIgnoreCase)) return "Combat";
            if (cat.Equals("server", StringComparison.OrdinalIgnoreCase)) return "Utility";
            foreach (string name in new string[] { "Visual", "Finder", "Camera", "Movement", "Combat", "Utility" }) if (name.Equals(cat, StringComparison.OrdinalIgnoreCase)) return name;
            return "Utility";
        }
        private static bool IsMovementModule(string id)
        {
            switch (id)
            {
                case "flight": case "noclip": case "boat_fly": case "fast_swim": case "super_trident": case "donut_flight_bypass": return true;
                default: return false;
            }
        }
        private static bool IsPvpModule(string id)
        {
            switch (id)
            {
                case "auto_totem": case "killaura": case "auto_crystal": case "auto_anchor": case "auto_gapple": case "trigger_bot": case "auto_weapon": return true;
                default: return false;
            }
        }
        private static string NormalizeFriends(string names)
        {
            List<string> normalized = new List<string>();
            foreach (string name in (names ?? "").Split(','))
            {
                string clean = name.Trim().ToLowerInvariant();
                if (clean.Length > 0 && !normalized.Contains(clean)) normalized.Add(clean);
            }
            normalized.Sort(StringComparer.Ordinal);
            return String.Join(",", normalized.ToArray());
        }

        protected override void OnResize(EventArgs e)
        {
            base.OnResize(e);
            scale = Math.Min(Width / 1180f, Height / 760f);
            offsetX = (Width - 1180f * scale) / 2; offsetY = (Height - 760f * scale) / 2;
            LayoutInputs();
        }
        private void LayoutInputs()
        {
            bool showInputs = !introActive && !openingFade;
            if (search != null) { search.Bounds = Pixel(new RectangleF(301, 143, 511, 24)); search.Visible = showInputs && (page == "modules" || page == "pvp"); }
            if (friends != null) { friends.Bounds = Pixel(new RectangleF(279, 590, 564, 24)); friends.Visible = showInputs && page == "pvp_settings"; }
        }
        private void Navigate(string nextPage)
        {
            if (nextPage != page && (nextPage == "modules" || nextPage == "pvp"))
            { search.Text = ""; selectedModule = nextPage == "pvp" ? "auto_totem" : "chunk_finder"; }
            page = nextPage; scroll = 0; LayoutInputs();
        }
        private Rectangle Pixel(RectangleF r) { return new Rectangle((int)(offsetX + r.X * scale), (int)(offsetY + r.Y * scale), (int)(r.Width * scale), (int)(r.Height * scale)); }
        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            Graphics g = e.Graphics; g.Clear(Color.Black);
            g.TranslateTransform(offsetX, offsetY); g.ScaleTransform(scale, scale);
            g.SmoothingMode = SmoothingMode.AntiAlias; g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;
            hits.Clear();
            Background(g); Sidebar(g); Chrome(g);
            if (page == "home") Home(g);
            else if (page == "modules" || page == "pvp") Modules(g);
            else if (page == "findings") Findings(g);
            else if (page == "finder_settings") FinderSettings(g);
            else if (page == "tutorials") Tutorials(g);
            else if (page == "pvp_settings") PvpSettings(g);
            else Settings(g);
            Portrait(g); Footer(g);
            if (DateTime.UtcNow < toastExpires)
            {
                Box(g, new RectangleF(250, 689, 670, 42), Color.FromArgb(252, 20, 13, 8), Color.FromArgb(146, red), 10);
                Label(g, toast, body, text, new RectangleF(269, 697, 634, 29));
            }
            DrawOpening(g);
            if (previewMode) Label(g, "OFFLINE DESIGN PREVIEW", small, muted, new RectangleF(12, 739, 207, 19));
        }
        private void Background(Graphics g)
        {
            g.FillRectangle(Brushes.Black, 0, 0, 1180, 760);
            using (SolidBrush brush = new SolidBrush(Color.FromArgb(6, red))) g.FillEllipse(brush, 790, -130, 520, 520);
            using (Pen p = new Pen(Color.FromArgb(7, red))) for (int x = 230; x < 1180; x += 48) g.DrawLine(p, x, 72, x - 115, 740);
            using (SolidBrush brush = new SolidBrush(Color.FromArgb(5, red))) g.FillEllipse(brush, 730, 560, 460, 340);
            using (Pen p = new Pen(Color.FromArgb(65, red))) g.DrawRectangle(p, 0, 0, 1179, 759);
        }
        private void Chrome(Graphics g)
        {
            Label(g, page == "pvp" || page == "pvp_settings" ? "CRIMSON PVP" : "WOOD & SHADOW", small, muted, new RectangleF(254, 24, 180, 20));
            Label(g, "26.3 / FABRIC", small, red, new RectangleF(444, 24, 190, 20));
            Label(g, "P", small, text, new RectangleF(953, 23, 19, 20));
            Label(g, "TO OPEN / HIDE", small, muted, new RectangleF(975, 23, 112, 20));
            Hit close = Register(new RectangleF(1140, 14, 28, 28), "close", "", 0);
            Hit minimize = Register(new RectangleF(1105, 14, 28, 28), "minimize", "", 0);
            using (Pen p = new Pen(text, 1.5f))
            {
                g.DrawLine(p, 1113, 30, 1124, 30); g.DrawLine(p, 1149, 23, 1158, 32); g.DrawLine(p, 1158, 23, 1149, 32);
            }
            if (close.Bounds.Contains(mouse)) Box(g, close.Bounds, Color.FromArgb(70, red), Color.Transparent, 6);
            if (minimize.Bounds.Contains(mouse)) Box(g, minimize.Bounds, Color.FromArgb(30, 255, 255, 255), Color.Transparent, 6);
            Register(new RectangleF(230, 0, 715, 56), "drag", "", 0);
        }
        private void Sidebar(Graphics g)
        {
            using (SolidBrush b = new SolidBrush(Color.FromArgb(255, 8, 6, 4))) g.FillRectangle(b, 1, 1, 223, 758);
            using (Pen p = new Pen(line)) g.DrawLine(p, 224, 0, 224, 760);
            if (logo != null) DrawAsset(g, logo, new RectangleF(18, 15, 188, 82), 1, false);
            else { Label(g, "TUNG TUNG", sidebarBrand, text, new RectangleF(18, 27, 188, 35), StringAlignment.Center); Label(g, "CLIENT", small, red, new RectangleF(18, 66, 188, 20), StringAlignment.Center); }
            Label(g, "YOUR WORLD. YOUR SIGNAL.", small, muted, new RectangleF(24, 106, 195, 20));
            using (Pen p = new Pen(line)) g.DrawLine(p, 23, 137, 202, 137);
            Label(g, "WORKSPACE", small, muted, new RectangleF(24, 158, 180, 19));
            Nav(g, "home", "Client lobby", "home", 187);
            Nav(g, "modules", "World Tools", "cube", 230);
            Nav(g, "findings", "Findings", "scan", 273);
            Nav(g, "pvp", "PvP arsenal", "shield", 316);
            Nav(g, "tutorials", "Tutorials", "book", 359);
            Nav(g, "settings", "Control center", "sliders", 402);
            Label(g, "WORLD CATEGORIES", small, muted, new RectangleF(24, 452, 180, 19));
            int y = 481;
            foreach (string cat in categories)
            {
                RectangleF rect = new RectangleF(17, y, 190, 25);
                bool active = page == "modules" && category == cat;
                if (active) Box(g, rect, Color.FromArgb(35, red), Color.Transparent, 8);
                using (SolidBrush dot = new SolidBrush(active ? red : Color.FromArgb(68, 57, 44))) g.FillEllipse(dot, 30, y + 10, 6, 6);
                Label(g, cat, body, active ? text : muted, new RectangleF(48, y + 1, 150, 22));
                Register(rect, "category", cat, 0); y += 27;
            }
            Box(g, new RectangleF(18, 663, 187, 65), Color.FromArgb(17, 12, 8), line, 10);
            using (SolidBrush dot = new SolidBrush(connected ? Color.FromArgb(101, 229, 164) : red)) g.FillEllipse(dot, 31, 681, 7, 7);
            Label(g, connected ? "CORE CONNECTED" : "CORE OFFLINE", small, text, new RectangleF(47, 675, 151, 20));
            Label(g, connected ? "Game bridge • localhost" : "Install once on Windows", small, muted, new RectangleF(31, 699, 164, 18));
            Register(new RectangleF(18, 663, 187, 65), "page", "settings", 0);
        }
        private void Nav(Graphics g, string id, string label, string icon, int y)
        {
            RectangleF bounds = new RectangleF(17, y, 190, 42); bool active = page == id || (id == "pvp" && page == "pvp_settings") || (id == "modules" && page == "finder_settings");
            if (active) { Box(g, bounds, Color.FromArgb(36, red), Color.FromArgb(90, red), 9); using (SolidBrush b = new SolidBrush(red)) g.FillRectangle(b, 17, y + 11, 3, 20); }
            Icon(g, icon, new RectangleF(30, y + 10, 22, 22), active ? red : muted);
            Label(g, label, body, active ? text : muted, new RectangleF(64, y + 9, 132, 27)); Register(bounds, "page", id, 0);
        }
        private void Home(Graphics g)
        {
            Label(g, "CLIENT LOBBY", small, wood, new RectangleF(256, 79, 611, 23));
            if (logo != null) DrawAsset(g, logo, new RectangleF(285, 122, 555, 170), 1, false);
            else Label(g, "Tung Tung Client", title, text, new RectangleF(274, 171, 566, 60), StringAlignment.Center);
            Label(g, "A little wood. A little chaos. Your own client.", body, muted, new RectangleF(256, 302, 611, 28), StringAlignment.Center);
            HomeCard(g, new RectangleF(256, 345, 298, 170), "World Tools", "Explore loaded chunks, find storage and control your camera.", "cube", wood, "modules");
            HomeCard(g, new RectangleF(569, 345, 298, 170), "PvP arsenal", "Crystals, anchors, totems and combat controls in one place.", "shield", crimson, "pvp");
            Box(g, new RectangleF(256, 540, 611, 134), Color.FromArgb(14, 10, 7), line, 13);
            using (SolidBrush dot = new SolidBrush(connected ? Color.FromArgb(101, 229, 164) : wood)) g.FillEllipse(dot, 278, 561, 7, 7);
            Label(g, connected ? "Minecraft connected" : "Bring your world online.", medium, text, new RectangleF(296, 550, 543, 30));
            Label(g, connected ? server : "Install the Fabric core once, then open the Tung Tung Client profile in Minecraft Launcher.", body, muted, new RectangleF(277, 587, 570, 42));
            Button(g, new RectangleF(277, 636, 260, 30), window.Installing ? "INSTALLING…" : "INSTALL FABRIC CORE", "install", "", 0, true);
            Button(g, new RectangleF(552, 636, 295, 30), "CHOOSE MINECRAFT FOLDER", "browse", "", 0, false);
            Label(g, "Press P from Minecraft to open this client. Your current page stays ready.", small, muted, new RectangleF(256, 698, 611, 23));
        }
        private void HomeCard(Graphics g, RectangleF bounds, string caption, string note, string icon, Color accent, string destination)
        {
            bool hover = bounds.Contains(mouse);
            Box(g, bounds, Color.FromArgb(15, 10, 9), Color.FromArgb(hover ? 170 : 95, accent), 15);
            using (LinearGradientBrush brush = new LinearGradientBrush(bounds, Color.FromArgb(28, accent), Color.Transparent, 90f))
            using (GraphicsPath path = Rounded(bounds, 15)) g.FillPath(brush, path);
            Icon(g, icon, new RectangleF(bounds.X + 21, bounds.Y + 21, 31, 31), accent);
            Label(g, caption, medium, text, new RectangleF(bounds.X + 21, bounds.Y + 67, 256, 33));
            Label(g, note, body, muted, new RectangleF(bounds.X + 21, bounds.Y + 104, 246, 47));
            Label(g, "→", medium, accent, new RectangleF(bounds.Right - 39, bounds.Y + 24, 24, 27));
            Register(bounds, "page", destination, 0);
        }
        private void DrawOpening(Graphics g)
        {
            float alpha = 0;
            if (introActive)
            {
                double elapsed = (DateTime.UtcNow - introStarted).TotalMilliseconds;
                alpha = elapsed < 1600 ? 1 : (float)Math.Max(0, 1 - (elapsed - 1600) / 500);
            }
            else if (openingFade) alpha = openingInitialAlpha * (float)Math.Max(0, Math.Min(1, 1 - (DateTime.UtcNow - openingStarted).TotalMilliseconds / 500));
            if (alpha <= 0) return;
            using (SolidBrush shade = new SolidBrush(Color.FromArgb((int)(255 * alpha), Color.Black))) g.FillRectangle(shade, 0, 0, 1180, 760);
            if (!introActive) return;
            if (logo != null) DrawAsset(g, logo, new RectangleF(320, 252, 540, 215), alpha, false);
            else Label(g, "Tung Tung Client", title, Color.FromArgb((int)(255 * alpha), wood), new RectangleF(320, 320, 540, 60), StringAlignment.Center);
            DrawAsset(g, dancer, new RectangleF(906, 254, 150, 280), alpha, true);
            Label(g, "Welcome to your client.", body, Color.FromArgb((int)(230 * alpha), muted), new RectangleF(320, 493, 540, 28), StringAlignment.Center);
            Label(g, "P / ESC · SKIP INTRO", small, Color.FromArgb((int)(210 * alpha), muted), new RectangleF(853, 694, 287, 23), StringAlignment.Far);
            Label(g, "×", medium, Color.FromArgb((int)(220 * alpha), muted), new RectangleF(1145, 16, 20, 25));
        }
        private sealed class Guide
        {
            public readonly string Summary, Steps, Limit;
            public Guide(string summary, string steps, string limit) { Summary = summary; Steps = steps; Limit = limit; }
        }
        private static Guide GetGuide(string id)
        {
            switch (id)
            {
                case "chunk_finder": return new Guide(
                    "Scores selected blocks and tracked armor stands. Same-position amethyst growth across uninterrupted loaded samples remains separate evidence; your own presence can cause it.",
                    "Choose signals and enable Chunk Finder. Solid-color models get matching chunk bands, one per type, plus optional view-center tracers.",
                    "Weak selected hits still appear in Findings. Sensitivity changes candidate scoring. Other ESP modules stay independent; crystals and natural features do not prove a base.");
                case "storage_esp": return new Guide(
                    "Fills received container models with their type color: chests are orange. Model details and texture cutouts remain intact; contents are not revealed.",
                    "Enable Storage ESP in World Tools → Visual. Adjust ESP range and tracers in Control center, then view received objects in Minecraft.",
                    "Storage contents require normal server interaction. Unloaded chunks and server-hidden blocks remain unknown.");
                case "player_esp": return new Guide(
                    "Fills received player models with their type color, labels and optional view-center tracers. The server determines which players enter the tracking list.",
                    "Enable Player ESP in World Tools → Visual, then adjust ESP range and tracers in Control center.",
                    "It cannot reveal players outside tracking range or obtain player data the server has not sent.");
                case "amethyst_esp": return new Guide(
                    "Fills received budding amethyst and crystal models with purple. Budding grows through random ticks; visible crystals do not confirm nearby players.",
                    "Enable Amethyst ESP in World Tools → Visual. Explore loaded chunks and adjust range or tracers in Control center.",
                    "Existing crystals may be old or natural. Chunk Finder compares uninterrupted loaded samples; unloading/reloading breaks observation history, and your own presence can cause growth.");
                case "xray": return new Guide(
                    "Hides common terrain plus water and lava, removes fog and brightens the view. It exposes faces of interesting blocks already received from the server.",
                    "Enable Shadow Byte X-ray in World Tools → Visual or from Findings. Explore loaded terrain; switching it off rebuilds normal terrain meshes.",
                    "Vanilla Fabric renderer only. Uses received chunks at normal client distance; blocks hidden by the server remain unknown.");
                case "freecam": return new Guide(
                    "Moves a local camera independently while the player's body stays in place. Camera movement is limited to terrain already received by the client.",
                    "Enable Freecam, return to Minecraft and use WASD, Space and Shift. Disable Freecam to restore your normal camera.",
                    "Your body remains vulnerable. Freecam and Freelook are mutually exclusive; neither loads distant terrain on its own.");
                case "freelook": return new Guide(
                    "Provides an independent third-person camera without changing your player's aim. The player's normal movement continues while you look around.",
                    "Enable Freelook, return to Minecraft and move your mouse. Disable it to restore your previous camera mode.",
                    "Freelook and Freecam are mutually exclusive. A local camera feature does not provide a detection guarantee.");
                case "base_chunks": return new Guide(
                    "Adds optional chunk boundaries for stronger activity candidates. Chunk Finder also adds matching colors for selected signals.",
                    "Enable Base Chunks for stronger activity candidates. Enable Chunk Finder as well to include weak selected matches in Findings.",
                    "Natural structures and incomplete received data can create misleading signals. Treat the score as a heuristic.");
                case "player_chunks": return new Guide(
                    "Adds optional chunk boundaries around players currently tracked by the server. Player ESP separately highlights their models.",
                    "Enable Player Chunks in World Tools → Finder. Use its boundaries alongside Player ESP to inspect received player positions.",
                    "Players outside the server's tracking range remain unknown. This module does not infer hidden or unloaded players.");
                case "player_detection": return new Guide(
                    "Notifies you when another player enters the received client-world player list. It uses tracked entities, rather than a hidden-player search.",
                    "Enable Player Detection in World Tools → Finder, then watch in-game notifications as loaded players change.",
                    "No player data exists beyond tracking range. Disconnected or unloaded players are not retained as live detections.");
                case "spawner_chunks": return new Guide(
                    "Adds optional chunk boundaries for received normal and trial spawners. Spawners can be natural, so their presence is not proof of a player build.",
                    "Enable Spawner Chunks for its explicit chunk boundaries. Chunk Finder selections control solid-color models, matching chunk colors and candidate scoring.",
                    "Only received spawner blocks qualify. A natural dungeon or trial chamber can create the same signal.");
                case "auto_totem": return new Guide(
                    "Equips a totem in the offhand when it is empty or health plus absorption reaches the shared health threshold. Uses normal inventory interactions.",
                    "Carry a totem in your inventory, enable Auto Totem and set the PvP Totem health threshold. Return to Minecraft.",
                    "Needs an available inventory totem and server-accepted slot actions. A server can delay or reject the inventory change.");
                case "killaura": return new Guide(
                    "Attacks visible hostile mobs within three blocks using normal attacks and full attack cooldown. This legacy module has its own target list.",
                    "Enable KillAura, hold a weapon and return to Minecraft near visible hostile mobs.",
                    "The new shared PvP target switches do not redefine legacy targeting. Normal reach and server-visible combat still apply.");
                case "auto_crystal": return new Guide(
                    "Places and breaks nearby end crystals around eligible targets, using received block data and estimated blast exposure. Placement and attacks use normal game interactions.",
                    "Put crystals in the hotbar, select target types, add friends, then set range, delay and damage limits in PvP settings.",
                    "Needs valid obsidian or bedrock placement and normal reach. Damage estimates do not confirm actual damage; server actions remain observable.");
                case "auto_anchor": return new Guide(
                    "Places, charges and detonates respawn anchors where anchors explode, waiting for received block changes between stages.",
                    "Provide hotbar anchors, glowstone and an empty or other-item slot. Keep glowstone out of the offhand; configure targets and damage limits.",
                    "Received updates are observed client state, rather than confirmed server acknowledgement. Open menus, detached cameras or manual item use pause automation.");
                case "auto_gapple": return new Guide(
                    "Uses hotbar golden or enchanted golden apples when health plus absorption reaches the shared PvP health threshold, restoring the slot and use-key state it owns.",
                    "Put apples in the hotbar, enable Auto Gapple, set the Totem health threshold, then return to focused gameplay.",
                    "The threshold is shared with Auto Totem. Manual item use, open menus or detached camera controls pause automation.");
                case "trigger_bot": return new Guide(
                    "Attacks the eligible entity under your crosshair at normal game reach and full attack cooldown. Shared targets, friends and action delay determine eligibility.",
                    "Enable TriggerBot, choose PvP target types and friends, then aim directly at an eligible target in Minecraft.",
                    "It does not rotate your view or expand reach. Combat actions remain server-visible.");
                case "auto_weapon": return new Guide(
                    "Selects the best stock sword or axe in the hotbar when your crosshair points at an eligible target. It does not attack by itself.",
                    "Put candidate swords or axes in the hotbar, enable Auto Weapon and aim at eligible targets.",
                    "Ranking ignores enchantments. The previous slot restores when the target disappears, unless you manually select another slot.");
                default: return null;
            }
        }
        private List<Module> Guides()
        {
            List<Module> result = new List<Module>();
            foreach (Module module in modules) if (module.Available && GetGuide(module.Id) != null) result.Add(module);
            return result;
        }
        private void Tutorials(Graphics g)
        {
            Label(g, "Learn the tools.", title, text, new RectangleF(254, 74, 611, 47));
            Label(g, "Concise guides for every implemented module.", body, muted, new RectangleF(257, 119, 611, 27));
            Box(g, new RectangleF(256, 165, 206, 540), Color.FromArgb(14, 10, 7), line, 12);
            Box(g, new RectangleF(480, 165, 387, 540), Color.FromArgb(14, 10, 7), line, 12);
            List<Module> guides = Guides();
            Label(g, "IMPLEMENTED GUIDES · " + guides.Count, small, wood, new RectangleF(273, 182, 181, 24));
            scroll = Math.Max(0, Math.Min(scroll, Math.Max(0, guides.Count - 10)));
            for (int i = scroll; i < Math.Min(guides.Count, scroll + 10); i++)
            {
                Module module = guides[i]; float y = 214 + (i - scroll) * 43;
                RectangleF row = new RectangleF(267, y, 184, 37);
                if (module.Id == selectedGuide) Box(g, row, Color.FromArgb(32, wood), Color.FromArgb(95, wood), 7);
                DrawModuleIcon(g, module, new RectangleF(278, y + 9, 19, 19), wood);
                Label(g, module.Name, body, module.Id == selectedGuide ? text : muted, new RectangleF(306, y + 8, 135, 27));
                Register(row, "guide", module.Id, 0);
            }
            if (guides.Count > 10)
            {
                Button(g, new RectangleF(277, 666, 76, 28), "↑ PREVIOUS", "scroll", "", -1, false);
                Button(g, new RectangleF(370, 666, 76, 28), "NEXT ↓", "scroll", "", 1, false);
            }
            Module selected = guides.Find(delegate(Module module) { return module.Id == selectedGuide; });
            if (selected == null && guides.Count > 0) { selected = guides[0]; selectedGuide = selected.Id; }
            if (selected == null)
            {
                Label(g, "No implemented guides available", medium, text, new RectangleF(502, 230, 333, 69));
                Label(g, "Connect a supported core to see its available tools.", body, muted, new RectangleF(502, 321, 333, 80)); return;
            }
            Guide guide = GetGuide(selected.Id);
            Label(g, "MODULE GUIDE", small, wood, new RectangleF(502, 181, 333, 25));
            DrawModuleIcon(g, selected, new RectangleF(502, 218, 32, 32), wood);
            Label(g, selected.Name, medium, text, new RectangleF(548, 216, 287, 43));
            Label(g, TechnicalStatus(selected), small, muted, new RectangleF(502, 268, 333, 23));
            using (Pen pen = new Pen(line)) g.DrawLine(pen, 502, 292, 845, 292);
            Label(g, "HOW IT WORKS", small, wood, new RectangleF(502, 306, 333, 21));
            Label(g, guide.Summary, body, text, new RectangleF(502, 335, 333, 97));
            Label(g, "QUICK START", small, wood, new RectangleF(502, 449, 333, 21));
            Label(g, guide.Steps, body, muted, new RectangleF(502, 477, 333, 87));
            Label(g, "WHAT TO KNOW", small, wood, new RectangleF(502, 581, 333, 21));
            Label(g, guide.Limit, body, muted, new RectangleF(502, 607, 333, 84));
        }
        private void Modules(Graphics g)
        {
            bool pvpPage = page == "pvp";
            Label(g, pvpPage ? "PvP arsenal." : "World Tools.", title, text, new RectangleF(254, 74, 430, 47));
            if (pvpPage) Button(g, new RectangleF(700, 82, 167, 34), "PVP SETTINGS", "page", "pvp_settings", 0, false);
            Label(g, pvpPage ? "Combat, crystals, anchors and inventory automation." : "Live visuals, loaded-chunk signals and camera controls.", body, muted, new RectangleF(257, 111, 618, 22));
            Box(g, new RectangleF(256, 140, 611, 36), Color.FromArgb(25, 21, 30), line, 9);
            Icon(g, "search", new RectangleF(270, 149, 17, 17), muted);
            if (search.Text.Length == 0 && !search.Focused) Label(g, "Search modules…", body, muted, new RectangleF(301, 148, 475, 24));
            Register(new RectangleF(256, 140, 611, 36), "search", "", 0);
            List<Module> visible = Filtered();
            Label(g, pvpPage ? "COMBAT & AUTOMATION" : category.ToUpperInvariant(), small, red, new RectangleF(258, 186, 270, 20));
            int enabled = 0; foreach (Module m in modules) if (m.Enabled && IsPvpModule(m.Id) == pvpPage) enabled++;
            Label(g, enabled + " ACTIVE  /  " + visible.Count + " MODULES", small, muted, new RectangleF(671, 186, 208, 20));
            int maxScroll = Math.Max(0, (visible.Count + 1) / 2 - 3);
            scroll = Math.Max(0, Math.Min(scroll, maxScroll));
            for (int i = scroll * 2; i < Math.Min(visible.Count, scroll * 2 + 6); i++)
            {
                int slot = i - scroll * 2; float x = 256 + (slot % 2) * 313, y = 216 + (slot / 2) * 143;
                ModuleCard(g, visible[i], new RectangleF(x, y, 298, 129));
            }
            if (visible.Count == 0) { Label(g, "No modules found", medium, text, new RectangleF(275, 291, 540, 40)); Label(g, "Try another category or search term.", body, muted, new RectangleF(275, 330, 540, 40)); }
            if (maxScroll > 0)
            {
                using (SolidBrush b = new SolidBrush(line)) g.FillRectangle(b, 875, 218, 3, 410);
                using (SolidBrush b = new SolidBrush(red)) g.FillRectangle(b, 875, 218 + 315f * scroll / maxScroll, 3, 95);
                Label(g, "SCROLL TO EXPLORE", small, muted, new RectangleF(256, 647, 290, 20));
                Button(g, new RectangleF(781, 642, 36, 28), "↑", "scroll", "", -1, false);
                Button(g, new RectangleF(831, 642, 36, 28), "↓", "scroll", "", 1, false);
            }
            Label(g, pvpPage ? "Actions run inside Minecraft. Set targets, friends and damage limits in PvP settings." : "Solid-color models and matching chunks appear in Minecraft. Received data only.", small, muted, new RectangleF(256, 675, 615, 20));
        }
        private List<Module> Filtered()
        {
            List<Module> visible = new List<Module>();
            bool pvpPage = page == "pvp";
            foreach (Module module in modules)
                if (IsPvpModule(module.Id) == pvpPage && (pvpPage || category == "All modules" || module.Category == category) && (search.Text.Length == 0 || module.Name.IndexOf(search.Text, StringComparison.OrdinalIgnoreCase) >= 0)) visible.Add(module);
            return visible;
        }
        private void ModuleCard(Graphics g, Module module, RectangleF bounds)
        {
            bool selected = selectedModule == module.Id, hover = bounds.Contains(mouse), active = module.Enabled;
            Color edge = selected || active ? Color.FromArgb(170, red) : hover ? Color.FromArgb(94, red) : line;
            if (active) Glow(g, bounds, red, 5);
            Box(g, bounds, page == "pvp" ? Color.FromArgb(248, 22, 14, 23) : Color.FromArgb(248, 17, 12, 8), edge, 13);
            using (LinearGradientBrush b = new LinearGradientBrush(bounds, Color.FromArgb(active ? 34 : 16, red), Color.Transparent, 90f))
            using (GraphicsPath path = Rounded(bounds, 13)) g.FillPath(b, path);
            DrawModuleIcon(g, module, new RectangleF(bounds.X + 16, bounds.Y + 18, 28, 28), module.Available ? red : Color.FromArgb(100, 83, 64));
            Label(g, module.Name, medium, module.Available ? text : muted, new RectangleF(bounds.X + 56, bounds.Y + 16, 185, 35));
            bool canToggle = connected && module.Available;
            Toggle(g, new RectangleF(bounds.Right - 52, bounds.Y + 23, 35, 19), active, canToggle);
            Label(g, module.Category.ToUpperInvariant(), small, muted, new RectangleF(bounds.X + 17, bounds.Y + 62, 240, 18));
            string status = !module.Available ? "UNAVAILABLE IN THIS RELEASE" : !connected ? "CONNECT MINECRAFT TO ENABLE" : active ? "ENABLED IN MINECRAFT" : "READY TO ENABLE";
            if (module.Id == "chunk_finder" && module.Available) status = active ? "ENABLED · CLICK FOR SIGNAL OPTIONS" : "CLICK FOR OPTIONS · SWITCH TO ENABLE";
            Color statusColor = !module.Available ? Color.FromArgb(128, 116, 102) : active ? Color.FromArgb(105, 226, 164) : muted;
            Label(g, status, small, statusColor, new RectangleF(bounds.X + 17, bounds.Y + 82, 268, 20));
            Label(g, TechnicalStatus(module), small, Color.FromArgb(185, red), new RectangleF(bounds.X + 17, bounds.Y + 107, 268, 17));
            Register(bounds, "select", module.Id, 0);
            Register(new RectangleF(bounds.Right - 61, bounds.Y + 10, 57, 43), "toggle", module.Id, 0);
        }
        private void Findings(Graphics g)
        {
            Label(g, "Follow the signal.", title, text, new RectangleF(254, 74, 605, 47));
            Button(g, new RectangleF(700, 82, 167, 34), "FINDER OPTIONS", "page", "finder_settings", 0, false);
            Label(g, "Candidate evidence and observed changes in received chunks.", body, muted, new RectangleF(257, 119, 615, 27));
            Box(g, new RectangleF(256, 165, 611, 79), Color.FromArgb(17, 12, 8), line, 12);
            Icon(g, "scan", new RectangleF(276, 187, 32, 32), red);
            Label(g, findings.Count.ToString(), brand, text, new RectangleF(325, 176, 95, 38));
            Label(g, "SIGNALS", small, muted, new RectangleF(327, 218, 110, 18));
            Module xray = modules.Find(delegate(Module m) { return m.Id == "xray"; });
            if (xray != null) Button(g, new RectangleF(461, 186, 189, 33), xray.Enabled ? "SHADOW BYTE X-RAY: ON" : "SHADOW BYTE X-RAY: OFF", "toggle", "xray", 0, xray.Enabled);
            Label(g, scanning ? "SCANNING" : connected ? "SCAN READY" : "OFFLINE", small, scanning ? Color.FromArgb(105, 226, 164) : muted, new RectangleF(695, 183, 145, 20));
            Label(g, "Radius " + radiusChunks + " chunks", body, muted, new RectangleF(672, 207, 177, 24));
            Label(g, "SIGNAL / POSITION", small, muted, new RectangleF(274, 266, 320, 20));
            Label(g, "SCORE", small, muted, new RectangleF(765, 266, 85, 20));
            int maxScroll = Math.Max(0, findings.Count - 6); scroll = Math.Min(scroll, maxScroll);
            for (int i = scroll; i < Math.Min(findings.Count, scroll + 6); i++)
            {
                Finding f = findings[i]; float y = 296 + (i - scroll) * 55;
                RectangleF row = new RectangleF(256, y, 611, 48);
                Box(g, row, selectedFinding == i ? Color.FromArgb(35, red) : Color.FromArgb(15, 11, 8), selectedFinding == i ? Color.FromArgb(120, red) : line, 8);
                Label(g, f.Kind, body, text, new RectangleF(274, y + 5, 262, 22));
                Label(g, f.Coordinates, small, muted, new RectangleF(274, y + 27, 357, 17));
                Label(g, f.Score.ToString(), medium, f.Score >= 60 ? red : muted, new RectangleF(782, y + 11, 70, 28));
                Register(row, "finding", "", i);
            }
            if (findings.Count == 0)
            {
                Icon(g, "scan", new RectangleF(512, 377, 85, 85), Color.FromArgb(100, red));
                Label(g, connected ? "No signals yet" : "Your world goes here", medium, text, new RectangleF(335, 484, 463, 31), StringAlignment.Center);
                Label(g, connected ? "Enable Chunk Finder and explore loaded terrain." : "Install the core, start the Tung Tung Client profile and join a world.", body, muted, new RectangleF(297, 525, 533, 55), StringAlignment.Center);
            }
            Button(g, new RectangleF(256, 650, 185, 38), "COPY POSITION", "copy", "", 0, true);
            Button(g, new RectangleF(454, 650, 185, 38), "EXPORT JSON", "export", "", 0, false);
            Button(g, new RectangleF(652, 650, 215, 38), "CLEAR FINDINGS", "clear", "", 0, false);
            Label(g, "A signal score is a heuristic. It does not prove a player base exists.", small, muted, new RectangleF(256, 700, 611, 21));
        }
        private void Settings(Graphics g)
        {
            Label(g, "Make it yours.", title, text, new RectangleF(254, 74, 605, 47));
            Label(g, "One desktop client. One Fabric core. A live connection.", body, muted, new RectangleF(257, 119, 614, 27));
            Box(g, new RectangleF(256, 165, 611, 159), Color.FromArgb(17, 12, 8), line, 12);
            Label(g, "MINECRAFT CONNECTION", small, red, new RectangleF(277, 182, 540, 22));
            Label(g, connected ? server : "Waiting for Minecraft", medium, text, new RectangleF(277, 207, 561, 31));
            Label(g, window.GameRoot, small, muted, new RectangleF(277, 242, 561, 24));
            Button(g, new RectangleF(276, 276, 259, 33), window.Installing ? "INSTALLING…" : "INSTALL FABRIC CORE", "install", "", 0, true);
            Button(g, new RectangleF(550, 276, 297, 33), "CHOOSE MINECRAFT FOLDER", "browse", "", 0, false);
            Box(g, new RectangleF(256, 342, 611, 345), Color.FromArgb(17, 12, 8), line, 12);
            Label(g, "SCAN & VISUALS", small, red, new RectangleF(277, 360, 540, 21));
            Slider(g, 0, "Sensitivity", sensitivity, 10, 90, "%", 400);
            Slider(g, 1, "Loaded-chunk radius", radiusChunks, 2, 32, " chunks", 470);
            Slider(g, 2, "ESP range", espRange, 16, 256, " blocks", 540);
            Label(g, "Tracers from view center", body, text, new RectangleF(277, 609, 353, 28));
            Toggle(g, new RectangleF(804, 612, 43, 23), tracers, connected);
            Register(new RectangleF(771, 604, 80, 42), "tracers", "", 0);
            Label(g, "Client sounds · meow + clicks", body, text, new RectangleF(277, 649, 470, 28));
            Toggle(g, new RectangleF(804, 651, 43, 23), !window.StartupMuted, true);
            Register(new RectangleF(771, 643, 80, 39), "startup_sound", "", 0);
            Label(g, "P opens this app from Minecraft; P / Esc hides it here. Exit is in the tray.", small, muted, new RectangleF(256, 702, 615, 23));
        }
        private void FinderSettings(Graphics g)
        {
            Label(g, "Finder options.", title, text, new RectangleF(254, 74, 430, 47));
            Button(g, new RectangleF(719, 82, 148, 34), "BACK TO WORLD TOOLS", "page", "modules", 0, false);
            Label(g, "Choose evidence for candidate scores, solid-color models and matching chunks.", body, muted, new RectangleF(257, 119, 611, 27));
            int selected = 0;
            for (int i = 0; i < finderKeys.Length; i++)
            {
                string key = finderKeys[i]; bool included = finderTargets[key];
                if (included) selected++;
                RectangleF bounds = new RectangleF(256 + (i % 3) * 208, 165 + (i / 3) * 91, 195, 80);
                Box(g, bounds, Color.FromArgb(17, 12, 8), included ? Color.FromArgb(125, wood) : line, 11);
                Module signal = new Module("finder_" + key, finderNames[i], "Finder", true, "", "");
                DrawModuleIcon(g, signal, new RectangleF(bounds.X + 14, bounds.Y + 19, 26, 26), included ? wood : muted);
                Label(g, finderNames[i], body, included ? text : muted, new RectangleF(bounds.X + 48, bounds.Y + 16, 101, 37));
                Toggle(g, new RectangleF(bounds.Right - 42, bounds.Y + 24, 29, 17), included, connected && finderTargetsSupported);
                Label(g, included ? "INCLUDED" : "EXCLUDED", small, included ? wood : muted, new RectangleF(bounds.X + 14, bounds.Y + 58, 167, 17));
                Register(bounds, "finder_target", key, 0);
            }
            string count = selected + " OF 12 SIGNALS SELECTED";
            if (!connected) count += " · OFFLINE";
            else if (!finderTargetsSupported) count = "UPDATE CORE FOR TARGET OPTIONS";
            else if (finderTargetsDirty) count += " · PENDING";
            Label(g, count, small, wood, new RectangleF(256, 528, 395, 23));
            Button(g, new RectangleF(665, 522, 95, 28), "ALL", "finder_all", "", 1, false);
            Button(g, new RectangleF(772, 522, 95, 28), "NONE", "finder_all", "", 0, false);
            Box(g, new RectangleF(256, 552, 611, 141), Color.FromArgb(17, 12, 8), line, 12);
            Slider(g, 0, "Candidate sensitivity", sensitivity, 10, 90, "%", 563);
            Slider(g, 1, "Loaded-chunk radius", radiusChunks, 2, 32, " chunks", 629);
            Label(g, "Object highlights do not depend on candidate scores. Amethyst observations stay separate.", small, muted, new RectangleF(256, 702, 611, 23));
        }
        private void PvpSettings(Graphics g)
        {
            Label(g, "Tune your combat.", title, text, new RectangleF(254, 74, 440, 47));
            Button(g, new RectangleF(730, 82, 137, 34), "BACK TO PVP", "page", "pvp", 0, false);
            Label(g, "Shared target controls, action timing and explosion-damage limits.", body, muted, new RectangleF(257, 119, 614, 27));
            Box(g, new RectangleF(256, 165, 611, 530), Color.FromArgb(24, 18, 28), line, 12);
            Slider(g, 3, "Combat range", pvpRange, 2, 6, " blocks", 188);
            Slider(g, 4, "Action delay", pvpDelayTicks, 2, 20, " ticks", 243);
            Slider(g, 5, "Maximum self damage", pvpMaxSelfDamage, 1, 12, " HP", 298);
            Slider(g, 6, "Minimum target damage", pvpMinTargetDamage, 1, 12, " HP", 353);
            Slider(g, 7, "Totem health threshold", pvpTotemHealth, 1, 20, " HP", 408);
            Label(g, "Target players", body, text, new RectangleF(277, 479, 353, 28));
            Toggle(g, new RectangleF(804, 481, 43, 23), pvpTargetPlayers, connected);
            Register(new RectangleF(771, 473, 80, 39), "pvp_players", "", 0);
            Label(g, "Target hostile mobs", body, text, new RectangleF(277, 517, 353, 28));
            Toggle(g, new RectangleF(804, 519, 43, 23), pvpTargetHostiles, connected);
            Register(new RectangleF(771, 511, 80, 39), "pvp_hostiles", "", 0);
            Label(g, "Friends · comma-separated names, case-insensitive", body, text, new RectangleF(277, 554, 568, 26));
            Box(g, new RectangleF(275, 582, 572, 45), Color.FromArgb(25, 21, 30), line, 8);
            Register(new RectangleF(275, 582, 572, 45), "friends", "", 0);
            Label(g, "Excluded from shared PvP targeting. Press Enter or Apply Friends to save.", small, muted, new RectangleF(277, 644, 354, 41));
            Button(g, new RectangleF(657, 643, 190, 36), "APPLY FRIENDS", "pvp_apply", "", 0, true);
            Label(g, "Range selects targets; normal game reach applies. Explosion damage values are estimates.", small, muted, new RectangleF(256, 702, 611, 23));
        }
        private void Slider(Graphics g, int id, string caption, double value, double min, double max, string unit, float y)
        {
            Label(g, caption, body, text, new RectangleF(277, y, 376, 26));
            Label(g, value.ToString("0.#", CultureInfo.InvariantCulture) + unit, mono, red, new RectangleF(699, y, 148, 26), StringAlignment.Far);
            float fraction = (float)Math.Max(0, Math.Min(1, (value - min) / (max - min)));
            RectangleF track = new RectangleF(277, y + 36, 568, 5);
            Box(g, track, line, Color.Transparent, 2);
            Box(g, new RectangleF(277, y + 36, Math.Max(1, 568 * fraction), 5), connected ? red : muted, Color.Transparent, 2);
            using (SolidBrush b = new SolidBrush(connected ? text : muted)) g.FillEllipse(b, 271 + 568 * fraction, y + 32, 13, 13);
            Register(new RectangleF(273, y + 25, 576, 27), "slider", "", id);
        }
        private void Portrait(Graphics g)
        {
            RectangleF panel = new RectangleF(898, 82, 254, 398);
            Color portraitBase = page == "pvp" || page == "pvp_settings" ? Color.FromArgb(15, 9, 15) : Color.FromArgb(13, 9, 6);
            Box(g, panel, portraitBase, Color.FromArgb(67, red), 16);
            using (LinearGradientBrush b = new LinearGradientBrush(panel, Color.FromArgb(24, red), Color.FromArgb(3, red), 90f))
            using (GraphicsPath path = Rounded(panel, 16)) g.FillPath(b, path);
            string portraitTitle = page == "home" ? "LOBBY DANCER" : page == "findings" ? "FINDINGS COMPANION" : page == "pvp" || page == "pvp_settings" ? "PVP COMPANION" : "WORLD COMPANION";
            Label(g, portraitTitle, small, red, new RectangleF(919, 101, 211, 21), StringAlignment.Center);
            Image portrait = page == "home" ? dancer : page == "findings" ? findingsMascot : page == "pvp" || page == "pvp_settings" ? pvpMascot : mascot;
            if (portrait != null)
            {
                RectangleF target = new RectangleF(910, 126, 232, 301);
                GraphicsState state = g.Save(); using (GraphicsPath clip = Rounded(new RectangleF(907, 125, 236, 302), 9)) g.SetClip(clip);
                DrawAsset(g, portrait, target, 1, page == "home"); g.Restore(state);
            }
            else WoodenMascot(g, new RectangleF(918, 138, 209, 270));
            using (LinearGradientBrush b = new LinearGradientBrush(new Rectangle(907, 382, 236, 75), Color.Transparent, portraitBase, 90f)) g.FillRectangle(b, 907, 382, 236, 75);
            Label(g, "TUNG TUNG", brand, text, new RectangleF(918, 415, 214, 38), StringAlignment.Center);
            Label(g, "CLIENT", small, red, new RectangleF(918, 454, 214, 19), StringAlignment.Center);
            Detail(g);
        }
        private void Detail(Graphics g)
        {
            Box(g, new RectangleF(898, 498, 254, 214), page == "pvp" || page == "pvp_settings" ? Color.FromArgb(17, 11, 18) : Color.FromArgb(14, 10, 7), line, 13);
            if (page == "finder_settings")
            {
                Label(g, "SELECTED EVIDENCE", small, wood, new RectangleF(917, 516, 216, 21));
                Label(g, "Candidate signals.", medium, text, new RectangleF(917, 546, 217, 37));
                Label(g, "Selections control solid-color models, matching chunk bands and scores. Armor stands use normal tracking. Natural features can produce unverified candidates.", small, muted, new RectangleF(917, 583, 216, 78));
                Box(g, new RectangleF(914, 667, 222, 29), Color.FromArgb(36, wood), Color.Transparent, 6);
                Label(g, "RECEIVED DATA · CANDIDATE ONLY", small, Color.FromArgb(225, wood), new RectangleF(923, 673, 205, 19));
                return;
            }
            if (page == "tutorials")
            {
                Label(g, "MODULE GUIDES", small, wood, new RectangleF(917, 516, 216, 21));
                Label(g, "Know how it works.", medium, text, new RectangleF(917, 546, 217, 45));
                Label(g, "Choose a tool on the left to learn its mechanics, quick start and limits. Open World Tools or PvP arsenal to enable it, then return here for its guide.", small, muted, new RectangleF(917, 599, 216, 88));
                return;
            }
            if (page == "home")
            {
                Label(g, "WELCOME TO THE LOBBY", small, wood, new RectangleF(917, 516, 216, 21));
                Label(g, "Make yourself at home.", medium, text, new RectangleF(917, 546, 217, 45));
                Label(g, "World Tools keeps exploration and camera features together. PvP has its own arsenal and settings. Press P in Minecraft to return to this client.", small, muted, new RectangleF(917, 599, 216, 88));
                return;
            }
            if (page == "findings" && selectedFinding >= 0 && selectedFinding < findings.Count)
            {
                Finding f = findings[selectedFinding];
                Label(g, "SELECTED SIGNAL", small, red, new RectangleF(917, 516, 216, 21));
                Label(g, f.Kind, medium, text, new RectangleF(917, 547, 216, 41));
                Label(g, f.Coordinates, mono, text, new RectangleF(917, 585, 216, 26));
                Label(g, f.Reasons, small, muted, new RectangleF(917, 618, 216, 76));
                return;
            }
            if (page == "pvp_settings")
            {
                Label(g, "PVP CONTROL CENTER", small, red, new RectangleF(917, 516, 216, 21));
                Label(g, "Know your targets.", medium, text, new RectangleF(917, 546, 217, 37));
                Label(g, "New PvP modules share target and friend filters. Kill Aura keeps its 3-block hostile-mob targeting. Damage limits use estimates; actions are server-visible.", small, muted, new RectangleF(917, 583, 216, 78));
                Box(g, new RectangleF(914, 667, 222, 29), Color.FromArgb(36, red), Color.Transparent, 6);
                Label(g, "DETECTION UNKNOWN · NO GUARANTEE", small, Color.FromArgb(225, red), new RectangleF(923, 673, 205, 19));
                return;
            }
            Module selected = modules.Find(delegate(Module m) { return m.Id == selectedModule; });
            if (selected == null) selected = modules.Count > 0 ? modules[0] : null;
            Label(g, "DONUT SMP DETECTION", small, red, new RectangleF(917, 516, 216, 21));
            Label(g, page == "settings" ? "Technical exposure" : selected == null ? "Choose a module" : selected.Name, medium, text, new RectangleF(917, 546, 217, 37));
            string note = page == "settings" ? "Camera and visuals stay local. Inventory, combat and movement actions reach the server. Detection and timing on Donut SMP are unknown; no guarantee." : selected == null ? "Connect Minecraft to see available modules." : selected.Note;
            Label(g, note, small, muted, new RectangleF(917, 583, 216, 75));
            Box(g, new RectangleF(914, 667, 222, 29), Color.FromArgb(36, red), Color.Transparent, 6);
            Label(g, page == "settings" ? "DETECTION UNKNOWN · NO GUARANTEE" : selected == null ? "DETECTION UNKNOWN" : TechnicalStatus(selected), small, Color.FromArgb(225, red), new RectangleF(923, 673, 205, 19));
        }
        private static string TechnicalStatus(Module module)
        {
            if (module.Id == "freecam" || module.Id == "freelook") return "LOCAL CAMERA · DETECTION UNKNOWN";
            if (module.Id == "auto_totem" || module.Id == "auto_weapon") return "SERVER-VISIBLE INVENTORY";
            if (module.Id == "killaura" || module.Id == "trigger_bot") return "SERVER-VISIBLE COMBAT";
            if (module.Id == "auto_crystal") return "SERVER-VISIBLE CRYSTAL ACTIONS";
            if (module.Id == "auto_anchor") return "SERVER-VISIBLE ANCHOR ACTIONS";
            if (module.Id == "auto_gapple") return "SERVER-VISIBLE ITEM USE";
            if (module.Id == "player_detection") return "CLIENT ALERTS · DETECTION UNKNOWN";
            if (IsMovementModule(module.Id) || module.Category == "Movement") return "HIGH EXPOSURE · TIMING UNVERIFIED";
            if (module.Id == "auction_sniper") return "SERVER ADAPTER · TIMING UNVERIFIED";
            if (module.Id == "anti_kick") return "SERVER CONTROL · TIMING UNVERIFIED";
            if (module.Id == "deepslate_bypass") return "HIDDEN DATA · DETECTION UNKNOWN";
            return "CLIENT VISUALS · DETECTION UNKNOWN";
        }
        private void Footer(Graphics g)
        {
            using (Pen p = new Pen(line)) g.DrawLine(p, 225, 734, 1179, 734);
            Label(g, connected ? "LIVE  •  " + server : "OFFLINE  •  Connect the Fabric core", small, connected ? Color.FromArgb(105, 226, 164) : muted, new RectangleF(254, 742, 510, 18));
            string position = connected ? String.Format(CultureInfo.InvariantCulture, "XYZ  {0:0} / {1:0} / {2:0}", playerX, playerY, playerZ) : "WINDOWS PORTABLE  /  v1.7.0";
            Label(g, position, small, muted, new RectangleF(838, 742, 313, 18), StringAlignment.Far);
        }
        private Hit Register(RectangleF bounds, string action, string id, int value)
        { Hit hit = new Hit(bounds, action, id, value); hits.Add(hit); return hit; }
        private void Button(Graphics g, RectangleF bounds, string caption, string action, string id, int value, bool primary)
        {
            bool hover = bounds.Contains(mouse);
            Box(g, bounds, primary ? Color.FromArgb(hover ? 90 : 62, red) : Color.FromArgb(hover ? 28 : 19, 16, 12), primary ? Color.FromArgb(150, red) : line, 8);
            Label(g, caption, small, text, new RectangleF(bounds.X + 8, bounds.Y + 9, bounds.Width - 16, bounds.Height - 8), StringAlignment.Center);
            Register(bounds, action, id, value);
        }
        protected override void OnMouseMove(MouseEventArgs e)
        {
            base.OnMouseMove(e); mouse = new PointF((e.X - offsetX) / scale, (e.Y - offsetY) / scale);
            if (activeSlider >= 0 && e.Button == MouseButtons.Left) SetSlider(activeSlider, mouse.X);
            bool hover = false; foreach (Hit hit in hits) if (hit.Bounds.Contains(mouse)) { hover = true; break; }
            Cursor = hover ? Cursors.Hand : Cursors.Default; Invalidate();
        }
        protected override void OnMouseLeave(EventArgs e) { base.OnMouseLeave(e); mouse = new PointF(-1, -1); Invalidate(); }
        protected override void OnMouseWheel(MouseEventArgs e)
        { base.OnMouseWheel(e); if (page == "modules" || page == "pvp") scroll = Math.Max(0, scroll - Math.Sign(e.Delta)); else if (page == "findings") scroll = Math.Max(0, Math.Min(Math.Max(0, findings.Count - 6), scroll - Math.Sign(e.Delta))); else if (page == "tutorials") scroll = Math.Max(0, Math.Min(Math.Max(0, Guides().Count - 10), scroll - Math.Sign(e.Delta))); Invalidate(); }
        protected override void OnMouseDown(MouseEventArgs e)
        {
            base.OnMouseDown(e); if (e.Button != MouseButtons.Left) return;
            mouse = new PointF((e.X - offsetX) / scale, (e.Y - offsetY) / scale);
            if (introActive)
            {
                window.PlayClickSound();
                if (new RectangleF(1138, 10, 34, 35).Contains(mouse)) window.HideClient(); else TrySkipIntro();
                return;
            }
            if (openingFade) return;
            Hit hit = null; for (int i = hits.Count - 1; i >= 0; i--) if (hits[i].Bounds.Contains(mouse)) { hit = hits[i]; break; }
            if (hit == null) { Focus(); return; }
            if (hit.Action != "drag") window.PlayClickSound();
            if (hit.Action != "search" && hit.Action != "friends") Focus();
            switch (hit.Action)
            {
                case "drag": window.DragWindow(); break;
                case "close": window.HideClient(); break;
                case "minimize": window.MinimizeClient(); break;
                case "page": Navigate(hit.Id); break;
                case "category": Navigate("modules"); category = hit.Id; break;
                case "search": search.Focus(); break;
                case "friends": if (connected) friends.Focus(); else Notify("Connect Minecraft to edit PvP friends."); break;
                case "pvp_apply": SendSettings(); break;
                case "pvp_players": if (connected) { pvpTargetPlayers = !pvpTargetPlayers; SendSettings(); } else Notify("Connect Minecraft to change PvP targets."); break;
                case "pvp_hostiles": if (connected) { pvpTargetHostiles = !pvpTargetHostiles; SendSettings(); } else Notify("Connect Minecraft to change PvP targets."); break;
                case "select": selectedModule = hit.Id; if (hit.Id == "chunk_finder") Navigate("finder_settings"); break;
                case "finder_target":
                    if (!connected) Notify("Connect Minecraft to change Finder evidence.");
                    else if (!finderTargetsSupported) Notify("Install the current Fabric core to use target options.");
                    else if (finderTargets.ContainsKey(hit.Id)) { finderTargets[hit.Id] = !finderTargets[hit.Id]; MarkFinderTargetsChanged(); SendSettings(); }
                    break;
                case "finder_all":
                    if (!connected) Notify("Connect Minecraft to change Finder evidence.");
                    else if (!finderTargetsSupported) Notify("Install the current Fabric core to use target options.");
                    else { foreach (string key in finderKeys) finderTargets[key] = hit.Value != 0; MarkFinderTargetsChanged(); SendSettings(); }
                    break;
                case "guide": selectedGuide = hit.Id; selectedModule = hit.Id; break;
                case "toggle":
                    selectedModule = hit.Id;
                    Module selected = modules.Find(delegate(Module m) { return m.Id == hit.Id; });
                    if (selected != null && selected.Available) window.Send("/module", new { id = selected.Id, enabled = !selected.Enabled });
                    else Notify(selected == null ? "Module is unavailable." : selected.Note);
                    break;
                case "scroll": scroll = Math.Max(0, scroll + hit.Value); break;
                case "finding": selectedFinding = hit.Value; break;
                case "copy": CopyFinding(); break;
                case "export": Export(); break;
                case "clear": window.Send("/action", new { action = "clear" }); break;
                case "browse": window.BrowseGameRoot(); break;
                case "install": window.InstallCore(); break;
                case "startup_sound": window.ToggleStartupSound(); break;
                case "tracers": if (connected) { tracers = !tracers; SendSettings(); } else Notify("Connect Minecraft to change visual settings."); break;
                case "slider":
                    if (connected) { activeSlider = hit.Value; Capture = true; SetSlider(activeSlider, mouse.X); }
                    else Notify("Connect Minecraft to change settings.");
                    break;
            }
            Invalidate();
        }
        protected override void OnMouseUp(MouseEventArgs e)
        { base.OnMouseUp(e); if (activeSlider >= 0) { activeSlider = -1; Capture = false; SendSettings(); } }
        private void SetSlider(int id, float x)
        {
            double fraction = Math.Max(0, Math.Min(1, (x - 277) / 568));
            if (id == 0) sensitivity = (int)Math.Round(10 + fraction * 80);
            else if (id == 1) radiusChunks = (int)Math.Round(2 + fraction * 30);
            else if (id == 2) espRange = (int)Math.Round(16 + fraction * 240);
            else if (id == 3) pvpRange = Math.Round(2 + fraction * 4, 1);
            else if (id == 4) pvpDelayTicks = (int)Math.Round(2 + fraction * 18);
            else if (id == 5) pvpMaxSelfDamage = Math.Round(1 + fraction * 11, 1);
            else if (id == 6) pvpMinTargetDamage = Math.Round(1 + fraction * 11, 1);
            else if (id == 7) pvpTotemHealth = Math.Round(1 + fraction * 19, 1);
            Invalidate();
        }
        private void SendSettings()
        {
            int sentRevision = finderTargetsRevision;
            window.Send("/settings", new
            {
                sensitivity = sensitivity, radiusChunks = radiusChunks, espRange = espRange, espTracers = tracers,
                pvpRange = pvpRange, pvpDelayTicks = pvpDelayTicks, pvpMaxSelfDamage = pvpMaxSelfDamage,
                pvpMinTargetDamage = pvpMinTargetDamage, pvpTotemHealth = pvpTotemHealth,
                pvpTargetPlayers = pvpTargetPlayers, pvpTargetHostiles = pvpTargetHostiles, pvpFriends = pvpFriends,
                finderTargets = new Dictionary<string, bool>(finderTargets)
            }, delegate(Dictionary<string, object> applied)
            {
                if (sentRevision != finderTargetsRevision) return;
                finderTargetsAwaitingResponse = false;
                UpdateState(applied);
            }, delegate
            {
                if (sentRevision == finderTargetsRevision) finderTargetsAwaitingResponse = false;
            });
        }
        private void MarkFinderTargetsChanged()
        { finderTargetsDirty = true; finderTargetsAwaitingResponse = true; finderTargetsRevision++; }
        private void CopyFinding()
        {
            if (selectedFinding < 0 || selectedFinding >= findings.Count) { Notify("Select a finding first."); return; }
            Finding f = findings[selectedFinding];
            try { Clipboard.SetText(f.X + " " + f.Y + " " + f.Z); Notify("Copied XYZ: " + f.Coordinates); }
            catch { Notify("Clipboard is busy. Try again."); }
        }
        private void Export()
        {
            if (findings.Count == 0) { Notify("No findings to export yet."); return; }
            using (SaveFileDialog picker = new SaveFileDialog())
            {
                picker.Title = "Tung Tung Client — export findings"; picker.Filter = "JSON findings (*.json)|*.json"; picker.FileName = "tung-tung-client-findings-" + DateTime.Now.ToString("yyyy-MM-dd-HHmm") + ".json";
                if (picker.ShowDialog(window) != DialogResult.OK) return;
                try
                {
                    List<object> rows = new List<object>(); foreach (Finding f in findings) rows.Add(new { x = f.X, y = f.Y, z = f.Z, score = f.Score, kind = f.Kind, reasons = f.Reasons });
                    File.WriteAllText(picker.FileName, Json.Encode(new { exportedAt = DateTime.UtcNow.ToString("o"), server = server, findings = rows }), Encoding.UTF8);
                    Notify("Exported " + findings.Count + " findings.");
                }
                catch (Exception ex) { Notify("Export failed: " + ex.Message); }
            }
        }

        private static GraphicsPath Rounded(RectangleF bounds, float radius)
        {
            GraphicsPath path = new GraphicsPath(); float d = Math.Min(radius * 2, Math.Min(bounds.Width, bounds.Height));
            if (d < 1) { path.AddRectangle(bounds); return path; }
            path.AddArc(bounds.X, bounds.Y, d, d, 180, 90); path.AddArc(bounds.Right - d, bounds.Y, d, d, 270, 90);
            path.AddArc(bounds.Right - d, bounds.Bottom - d, d, d, 0, 90); path.AddArc(bounds.X, bounds.Bottom - d, d, d, 90, 90); path.CloseFigure(); return path;
        }
        private static void Box(Graphics g, RectangleF bounds, Color fill, Color border, float radius)
        {
            using (GraphicsPath path = Rounded(bounds, radius))
            {
                if (fill.A != 0) using (SolidBrush b = new SolidBrush(fill)) g.FillPath(b, path);
                if (border.A != 0) using (Pen p = new Pen(border, 1f)) g.DrawPath(p, path);
            }
        }
        private static void Glow(Graphics g, RectangleF bounds, Color color, int width)
        {
            for (int i = width; i >= 1; i--) using (Pen p = new Pen(Color.FromArgb(9, color), i * 2)) using (GraphicsPath path = Rounded(bounds, 13)) g.DrawPath(p, path);
        }
        private static void Label(Graphics g, string value, Font font, Color color, RectangleF bounds)
        { Label(g, value, font, color, bounds, StringAlignment.Near); }
        private static void Label(Graphics g, string value, Font font, Color color, RectangleF bounds, StringAlignment alignment)
        {
            using (SolidBrush b = new SolidBrush(color))
            using (StringFormat format = new StringFormat())
            { format.Alignment = alignment; format.LineAlignment = StringAlignment.Near; format.Trimming = StringTrimming.EllipsisWord; g.DrawString(value ?? "", font, b, bounds, format); }
        }
        private void Toggle(Graphics g, RectangleF bounds, bool enabled, bool available)
        {
            Box(g, bounds, enabled ? Color.FromArgb(240, red) : available ? Color.FromArgb(57, 40, 30) : Color.FromArgb(32, 26, 22), Color.Transparent, bounds.Height / 2);
            float size = bounds.Height - 6;
            using (SolidBrush b = new SolidBrush(available ? Color.FromArgb(255, 249, 251) : Color.FromArgb(93, 82, 100))) g.FillEllipse(b, enabled ? bounds.Right - size - 3 : bounds.X + 3, bounds.Y + 3, size, size);
        }
        private static string ModuleIcon(string id)
        {
            if (id == "xray") return "eye";
            if (id.Contains("crystal")) return "crystal";
            if (id.Contains("anchor")) return "anchor";
            if (id.Contains("gapple")) return "apple";
            if (id.Contains("weapon") || id.Contains("trigger")) return "sword";
            if (id.Contains("esp") || id.Contains("detection") || id.Contains("look")) return "eye";
            if (id.Contains("cam")) return "camera";
            if (id.Contains("chunk") || id.Contains("finder")) return "cube";
            if (id.Contains("flight") || id.Contains("fly") || id.Contains("swim") || id.Contains("trident")) return "bolt";
            if (id.Contains("aura") || id.Contains("totem")) return "shield";
            return "sliders";
        }
        private static void Icon(Graphics g, string id, RectangleF bounds, Color color)
        {
            GraphicsState saved = g.Save(); g.TranslateTransform(bounds.X, bounds.Y); g.ScaleTransform(bounds.Width / 24, bounds.Height / 24);
            using (Pen p = new Pen(color, 1.7f))
            {
                p.StartCap = LineCap.Round; p.EndCap = LineCap.Round; p.LineJoin = LineJoin.Round;
                if (id == "book")
                { g.DrawLines(p, new PointF[] { new PointF(12, 4), new PointF(12, 22) }); g.DrawPolygon(p, new PointF[] { new PointF(2, 3), new PointF(9, 3), new PointF(12, 5), new PointF(15, 3), new PointF(22, 3), new PointF(22, 20), new PointF(15, 20), new PointF(12, 22), new PointF(9, 20), new PointF(2, 20) }); g.DrawLine(p, 5, 8, 9, 8); g.DrawLine(p, 15, 8, 19, 8); }
                else if (id == "home")
                { g.DrawLines(p, new PointF[] { new PointF(2, 11), new PointF(12, 2), new PointF(22, 11) }); g.DrawLines(p, new PointF[] { new PointF(5, 9), new PointF(5, 22), new PointF(19, 22), new PointF(19, 9) }); g.DrawRectangle(p, 10, 15, 4, 7); }
                else if (id == "cube")
                { g.DrawPolygon(p, new PointF[] { new PointF(12, 2), new PointF(22, 7.5f), new PointF(22, 17), new PointF(12, 23), new PointF(2, 17), new PointF(2, 7.5f) }); g.DrawLines(p, new PointF[] { new PointF(2, 7.5f), new PointF(12, 13), new PointF(22, 7.5f) }); g.DrawLine(p, 12, 13, 12, 23); g.DrawLine(p, 7, 5, 17, 10); }
                else if (id == "eye")
                { using (GraphicsPath eye = new GraphicsPath()) { eye.AddBezier(1, 12, 7, 2, 17, 2, 23, 12); eye.AddBezier(23, 12, 17, 22, 7, 22, 1, 12); g.DrawPath(p, eye); } g.DrawEllipse(p, 8, 8, 8, 8); }
                else if (id == "scan")
                { g.DrawArc(p, 3, 3, 18, 18, 0, 360); g.DrawArc(p, 8, 8, 8, 8, 0, 360); g.DrawLine(p, 12, 1, 12, 6); g.DrawLine(p, 12, 18, 12, 23); g.DrawLine(p, 1, 12, 6, 12); g.DrawLine(p, 18, 12, 23, 12); }
                else if (id == "search") { g.DrawEllipse(p, 3, 2, 13, 13); g.DrawLine(p, 14, 14, 22, 22); }
                else if (id == "camera")
                { g.DrawPolygon(p, new PointF[] { new PointF(2, 7), new PointF(7, 7), new PointF(9, 4), new PointF(15, 4), new PointF(17, 7), new PointF(22, 7), new PointF(22, 21), new PointF(2, 21) }); g.DrawEllipse(p, 8, 10, 8, 8); }
                else if (id == "bolt") g.DrawPolygon(p, new PointF[] { new PointF(14, 1), new PointF(4, 13), new PointF(11, 13), new PointF(9, 23), new PointF(21, 9), new PointF(14, 9) });
                else if (id == "shield") { g.DrawPolygon(p, new PointF[] { new PointF(12, 2), new PointF(22, 6), new PointF(19, 18), new PointF(12, 23), new PointF(5, 18), new PointF(2, 6) }); g.DrawLine(p, 12, 7, 12, 16); g.DrawLine(p, 8, 11, 16, 11); }
                else if (id == "crystal")
                { g.DrawPolygon(p, new PointF[] { new PointF(12, 1), new PointF(21, 10), new PointF(16, 23), new PointF(7, 23), new PointF(3, 10) }); g.DrawLine(p, 3, 10, 21, 10); g.DrawLines(p, new PointF[] { new PointF(12, 1), new PointF(8, 10), new PointF(10, 23) }); g.DrawLines(p, new PointF[] { new PointF(12, 1), new PointF(16, 10), new PointF(14, 23) }); }
                else if (id == "anchor")
                { g.DrawEllipse(p, 9, 1, 6, 6); g.DrawLine(p, 12, 7, 12, 22); g.DrawLine(p, 5, 10, 19, 10); g.DrawBezier(p, 2, 15, 4, 21, 7, 23, 12, 22); g.DrawBezier(p, 22, 15, 20, 21, 17, 23, 12, 22); g.DrawLine(p, 2, 15, 2, 19); g.DrawLine(p, 22, 15, 22, 19); }
                else if (id == "sword")
                { g.DrawPolygon(p, new PointF[] { new PointF(22, 2), new PointF(21, 9), new PointF(9, 19), new PointF(5, 15), new PointF(15, 3) }); g.DrawLine(p, 4, 12, 12, 20); g.DrawLine(p, 7, 18, 2, 23); }
                else if (id == "apple")
                { using (GraphicsPath apple = new GraphicsPath()) { apple.AddBezier(12, 8, 2, 2, 1, 12, 4, 19); apple.AddBezier(4, 19, 7, 24, 9, 23, 12, 22); apple.AddBezier(12, 22, 15, 23, 18, 24, 21, 18); apple.AddBezier(21, 18, 24, 9, 20, 3, 12, 8); g.DrawPath(p, apple); } g.DrawLine(p, 12, 8, 12, 3); g.DrawBezier(p, 12, 3, 14, 1, 17, 1, 18, 2); }
                else if (id == "brand") { g.DrawPolygon(p, new PointF[] { new PointF(12, 1), new PointF(23, 12), new PointF(12, 23), new PointF(1, 12) }); g.DrawLines(p, new PointF[] { new PointF(7, 8), new PointF(17, 8), new PointF(12, 8), new PointF(12, 18) }); }
                else { g.DrawLine(p, 3, 6, 21, 6); g.DrawLine(p, 3, 12, 21, 12); g.DrawLine(p, 3, 18, 21, 18); g.DrawEllipse(p, 6, 3, 6, 6); g.DrawEllipse(p, 14, 9, 6, 6); g.DrawEllipse(p, 8, 15, 6, 6); }
            }
            g.Restore(saved);
        }
        private void WoodenMascot(Graphics g, RectangleF r)
        {
            // A vector fallback keeps the app complete if the optional portrait is missing.
            using (LinearGradientBrush wood = new LinearGradientBrush(r, Color.FromArgb(152, 83, 38), Color.FromArgb(52, 29, 23), 0f))
            {
                Box(g, new RectangleF(r.X + 48, r.Y + 14, 115, 132), Color.FromArgb(117, 64, 32), Color.FromArgb(174, 98, 43), 40);
                g.FillEllipse(wood, r.X + 43, r.Y + 116, 127, 151);
            }
            using (Pen grain = new Pen(Color.FromArgb(95, 65, 29, 14), 2)) for (int i = 0; i < 9; i++) g.DrawBezier(grain, r.X + 59 + i * 11, r.Y + 24, r.X + 43 + i * 11, r.Y + 88, r.X + 80 + i * 7, r.Y + 170, r.X + 51 + i * 12, r.Y + 248);
            using (SolidBrush shadow = new SolidBrush(Color.FromArgb(23, 11, 12))) { g.FillEllipse(shadow, r.X + 67, r.Y + 60, 35, 30); g.FillEllipse(shadow, r.X + 121, r.Y + 60, 35, 30); g.FillEllipse(shadow, r.X + 91, r.Y + 105, 34, 17); }
            int glowAlpha = (int)(100 + 45 * Math.Sin(pulse));
            for (int i = 4; i >= 1; i--) using (SolidBrush glow = new SolidBrush(Color.FromArgb(glowAlpha / (i + 1), 255, 28, 61))) g.FillEllipse(glow, r.X + 126 - i * 5, r.Y + 66 - i * 5, 22 + i * 10, 15 + i * 10);
            using (SolidBrush eye = new SolidBrush(Color.FromArgb(255, 58, 74))) g.FillEllipse(eye, r.X + 126, r.Y + 66, 22, 15);
            using (SolidBrush shine = new SolidBrush(Color.FromArgb(255, 243, 230))) g.FillEllipse(shine, r.X + 130, r.Y + 68, 5, 5);
            using (Pen bat = new Pen(Color.FromArgb(126, 76, 37), 16)) { bat.StartCap = LineCap.Round; bat.EndCap = LineCap.Round; g.DrawLine(bat, r.X + 183, r.Y + 120, r.X + 186, r.Y + 249); }
        }
    }
}
