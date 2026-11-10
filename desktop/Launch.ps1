[CmdletBinding()]
param([switch]$PruneCache, [string]$RenderPreviews)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
$bundle = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$logPath = $null

function Write-LaunchLog([string]$message) {
    if ($logPath) {
        try { [IO.File]::AppendAllText($logPath, ([DateTime]::UtcNow.ToString('o') + ' ' + $message + [Environment]::NewLine), [Text.Encoding]::UTF8) }
        catch { } # Logging must not stop the client opening.
    }
}

try {
    if ($env:OS -ne 'Windows_NT') { throw 'Tung Tung Client runs on Windows 10 or 11.' }
    if ([Threading.Thread]::CurrentThread.ApartmentState -ne [Threading.ApartmentState]::STA) {
        throw 'Open START-TUNG-TUNG-CLIENT.cmd to start Tung Tung Client in STA mode.'
    }
    $sourcePath = Join-Path $PSScriptRoot 'TungTungClient.cs'
    if (!(Test-Path -LiteralPath $sourcePath -PathType Leaf)) { throw 'TungTungClient.cs is missing. Extract the whole ZIP, including its desktop folder.' }

    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    Add-Type -AssemblyName System.Web.Extensions
    Add-Type -AssemblyName System.Net.Http
    $env:TUNTUN_BUNDLE_PATH = $bundle
    Set-Location -LiteralPath $bundle
    $source = [IO.File]::ReadAllText($sourcePath, [Text.Encoding]::UTF8)
    $references = @(
        [Uri].Assembly.Location,
        [Linq.Enumerable].Assembly.Location,
        [Windows.Forms.Form].Assembly.Location,
        [Drawing.Color].Assembly.Location,
        [Web.Script.Serialization.JavaScriptSerializer].Assembly.Location,
        [Net.Http.HttpClient].Assembly.Location
    )

    # Compile a persistent Windows application with the built-in .NET Framework
    # compiler. No developer SDK, Minecraft download, or administrator access.
    $entryPath = Join-Path $PSScriptRoot 'DesktopEntry.cs'
    if (!(Test-Path -LiteralPath $entryPath -PathType Leaf)) { throw 'DesktopEntry.cs is missing. Extract the complete ZIP.' }
    $entryPoint = [IO.File]::ReadAllText($entryPath, [Text.Encoding]::UTF8)
    $fullSource = $source + [Environment]::NewLine + $entryPoint
    $runArguments = @($bundle)
    if (![string]::IsNullOrWhiteSpace($RenderPreviews)) {
        $runArguments += '--render-previews'
        $runArguments += [IO.Path]::GetFullPath($RenderPreviews)
    }
    $quotedArguments = ($runArguments | ForEach-Object { '"' + $_.Replace('\', '/') + '"' }) -join ' '
    $compiledPath = $null
    try {
        $cacheDir = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'TunTunClient\runtime'
        [IO.Directory]::CreateDirectory($cacheDir) | Out-Null
        $logPath = Join-Path $cacheDir 'launcher.log'
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $hash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($fullSource))).Replace('-', '').Substring(0, 20).ToLowerInvariant() }
        finally { $sha.Dispose() }
        $compiledPath = Join-Path $cacheDir ('Tung-Tung-Client-' + $hash + '.exe')
        if (!(Test-Path -LiteralPath $compiledPath -PathType Leaf)) {
            $temporaryExe = Join-Path $cacheDir ('compile-' + [Guid]::NewGuid().ToString('N') + '.exe')
            try {
                Add-Type -LiteralPath @($sourcePath, $entryPath) -ReferencedAssemblies $references -OutputAssembly $temporaryExe -OutputType WindowsApplication -IgnoreWarnings -ErrorAction Stop
                if (!(Test-Path -LiteralPath $temporaryExe -PathType Leaf)) { throw 'The .NET compiler did not create the desktop application.' }
                if (!(Test-Path -LiteralPath $compiledPath -PathType Leaf)) { [IO.File]::Move($temporaryExe, $compiledPath) }
            } finally {
                if (Test-Path -LiteralPath $temporaryExe -PathType Leaf) { Remove-Item -LiteralPath $temporaryExe -Force }
            }
        }
        if ($PruneCache) {
            # An explicit maintenance option removes only old client binaries
            # from this application's own cache. A running EXE is left alone.
            $cutoff = [DateTime]::UtcNow.AddDays(-30)
            Get-ChildItem -LiteralPath $cacheDir -File | Where-Object {
                $_.Name -match '^((TunTunClient-|Tung-Tung-Client-)[a-f0-9]{20}|compile-[a-f0-9]{32})\.exe$' -and
                $_.FullName -ne $compiledPath -and $_.LastWriteTimeUtc -lt $cutoff
            } | ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force -ErrorAction SilentlyContinue }
        }
        Write-LaunchLog ('Opening desktop application ' + [IO.Path]::GetFileName($compiledPath))
        # Windows paths cannot contain a double quote. Explicit quoting preserves
        # spaces, Cyrillic names, and a ZIP extracted outside the user profile.
        if (![string]::IsNullOrWhiteSpace($RenderPreviews)) {
            $previewProcess = Start-Process -FilePath $compiledPath -ArgumentList $quotedArguments -WorkingDirectory $bundle -Wait -PassThru -ErrorAction Stop
            if ($previewProcess.ExitCode -ne 0) { throw 'Preview rendering failed. Check preview-error.txt in the output folder.' }
        } else {
            Start-Process -FilePath $compiledPath -ArgumentList $quotedArguments -WorkingDirectory $bundle -ErrorAction Stop | Out-Null
        }
    } catch {
        Write-LaunchLog ('Standalone compilation/start unavailable: ' + $_.Exception.ToString())
        # A read-only bundle or a locked compilation cache still has a usable
        # separate application window, hosted by Windows PowerShell.
        if (!('TunTun.DesktopEntry' -as [type])) {
            Add-Type -LiteralPath @($sourcePath, $entryPath) -ReferencedAssemblies $references -IgnoreWarnings -ErrorAction Stop
        }
        [TunTun.DesktopEntry]::Main([string[]]$runArguments)
        if ($RenderPreviews -and [Environment]::ExitCode -ne 0) { exit 1 }
    }
} catch {
    Write-LaunchLog $_.Exception.ToString()
    try {
        Add-Type -AssemblyName System.Windows.Forms
        [Windows.Forms.MessageBox]::Show(($_.Exception.Message + [Environment]::NewLine + [Environment]::NewLine + 'Extract the complete ZIP and open START-TUNG-TUNG-CLIENT.cmd. Windows 10/11 and built-in .NET Framework are required.'), 'Tung Tung Client - could not open', 'OK', 'Error') | Out-Null
    } catch {
        Write-Error $_
    }
    exit 1
}
