using System;
using System.IO;
using System.Threading;
using System.Windows.Forms;

namespace TunTun
{
    public static class DesktopEntry
    {
        [STAThread]
        public static void Main(string[] args)
        {
            bool previewRequested = false;
            string outputDirectory = null;
            try
            {
                string bundle = AppDomain.CurrentDomain.BaseDirectory;
                bool bundleSpecified = false;
                for (int index = 0; index < args.Length; index++)
                {
                    if (String.Equals(args[index], "--render-previews", StringComparison.OrdinalIgnoreCase))
                    {
                        previewRequested = true;
                        if (++index >= args.Length || String.IsNullOrWhiteSpace(args[index]))
                            throw new ArgumentException("--render-previews requires an output folder.");
                        outputDirectory = Path.GetFullPath(args[index]);
                    }
                    else if (!bundleSpecified && !args[index].StartsWith("--", StringComparison.Ordinal))
                    {
                        bundle = args[index]; bundleSpecified = true;
                    }
                    else throw new ArgumentException("Unknown command argument: " + args[index]);
                }
                bundle = Path.GetFullPath(bundle);
                if (!File.Exists(Path.Combine(bundle, "desktop", "Install-Core.ps1")))
                    throw new InvalidOperationException("Extract the complete ZIP and keep Tung-Tung-Client.exe beside the desktop and payload folders.");
                Environment.CurrentDirectory = bundle;
                if (previewRequested)
                {
                    Directory.CreateDirectory(outputDirectory);
                    ClientApp.RenderPreviews(bundle, outputDirectory);
                    return;
                }

                // Preview rendering is independent of the single interactive
                // client and does not start its game bridge, sound, or tray.
                using (Mutex instance = new Mutex(false, "Local\\TunTunCrimsonClient"))
                {
                    bool first;
                    try { first = instance.WaitOne(0, false); }
                    catch (AbandonedMutexException) { first = true; }
                    if (!first) { MessageBox.Show("Tung Tung Client is already running. Open it from the system tray.", "Tung Tung Client"); return; }
                    try { ClientApp.Run(bundle); }
                    finally { instance.ReleaseMutex(); }
                }
            }
            catch (Exception error)
            {
                Environment.ExitCode = 1;
                if (previewRequested)
                {
                    try
                    {
                        if (!String.IsNullOrEmpty(outputDirectory))
                        {
                            Directory.CreateDirectory(outputDirectory);
                            File.WriteAllText(Path.Combine(outputDirectory, "preview-error.txt"), error.ToString());
                        }
                    }
                    catch { }
                    Console.Error.WriteLine(error.Message);
                }
                else MessageBox.Show(error.Message, "Tung Tung Client — could not open", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }
}
