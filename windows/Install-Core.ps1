[CmdletBinding()]
param(
    [string]$MinecraftPath,
    [string]$BundlePath = (Split-Path -Parent $PSScriptRoot)
)

Set-StrictMode -Version 2.0

function Install-TunTun {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$MinecraftPath,
        [Parameter(Mandatory = $true)][string]$BundlePath
    )
    $ErrorActionPreference = 'Stop'
    $MinecraftPath = [IO.Path]::GetFullPath($MinecraftPath)
    $BundlePath = [IO.Path]::GetFullPath($BundlePath)
    $profileId = 'tun-tun-crimson-26-3'
    $profileName = 'Tung Tung Client 26.3'
    $versionId = 'fabric-loader-0.19.5-26.3'
    $gameDir = Join-Path $MinecraftPath 'tun-tun-26.3'

    if (Get-Process -Name 'MinecraftLauncher' -ErrorAction SilentlyContinue) {
        throw 'Close Minecraft Launcher before installing, then click Install again.'
    }
    if ($env:OS -eq 'Windows_NT') {
        $games = @(Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe' OR Name = 'java.exe'" -ErrorAction Stop | Where-Object {
            $_.CommandLine -and ($_.CommandLine.IndexOf($gameDir, [StringComparison]::OrdinalIgnoreCase) -ge 0 -or $_.CommandLine -match 'net\.minecraft\.client|fabricmc\.loader.*KnotClient')
        })
        if ($games.Count -gt 0) { throw 'Close Minecraft before installing the new core, then click Install again.' }
    }
    $profiles = @(@('launcher_profiles.json', 'launcher_profiles_microsoft_store.json') |
        ForEach-Object { Join-Path $MinecraftPath $_ } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Leaf })
    if ($profiles.Count -eq 0) {
        throw 'Minecraft Launcher profiles were not found. Open the official Launcher once, close it, and select its .minecraft folder.'
    }
    $manifest = Get-Content -LiteralPath (Join-Path $BundlePath 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    if (!$manifest -or !$manifest.PSObject.Properties['files'] -or @($manifest.files).Count -eq 0 -or !$manifest.PSObject.Properties['version'] -or $manifest.version -ne '1.30.1' -or !$manifest.PSObject.Properties['gameCoreVersion'] -or $manifest.gameCoreVersion -ne '1.30.1' -or !$manifest.PSObject.Properties['minecraft'] -or $manifest.minecraft -ne '26.3') { throw 'The ZIP manifest must describe desktop and Fabric Core 1.30.1 for Minecraft 26.3. Extract a fresh complete copy.' }
    $prefix = $BundlePath.TrimEnd([char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)) + [IO.Path]::DirectorySeparatorChar
    foreach ($asset in $manifest.files) {
        if ([IO.Path]::IsPathRooted($asset.path)) { throw 'Invalid absolute path in bundle manifest.' }
        $source = [IO.Path]::GetFullPath((Join-Path $BundlePath $asset.path))
        if (!$source.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid path outside the bundle in manifest.' }
        if (!(Test-Path -LiteralPath $source -PathType Leaf)) { throw "Bundle file missing: $($asset.path). Extract the entire ZIP first." }
        $actual = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne $asset.sha256.ToLowerInvariant()) { throw "Bundle checksum mismatch: $($asset.path). Extract a fresh copy of the ZIP." }
    }
    $modAssets = @($manifest.files | Where-Object { $_.kind -eq 'mod' })
    $bridgeAssets = @($modAssets | Where-Object { [IO.Path]::GetFileName($_.path) -eq 'tung-tung-bridge-1.30.1.jar' })
    $fabricAssets = @($modAssets | Where-Object { [IO.Path]::GetFileName($_.path) -eq 'fabric-api-0.162.0+26.3.jar' })
    $voiceAssets = @($modAssets | Where-Object { [IO.Path]::GetFileName($_.path) -eq 'voicechat-fabric-2.6.24+26.3.jar' })
    if ($bridgeAssets.Count -ne 1 -or $fabricAssets.Count -ne 1 -or $voiceAssets.Count -ne 1 -or $modAssets.Count -ne 3) { throw 'The bundle must contain the Tung Tung Client bridge, pinned Fabric API and Simple Voice Chat.' }
    $fabricJson = Get-Content -LiteralPath (Join-Path $BundlePath 'payload/fabric-profile.json') -Raw -Encoding UTF8
    $fabricProfile = $fabricJson | ConvertFrom-Json
    if ($fabricProfile.id -ne $versionId -or $fabricProfile.inheritsFrom -ne '26.3') { throw 'Unexpected Fabric version in bundle.' }

    # Prepare every Launcher JSON before changing files. Unrelated profiles,
    # account metadata, and settings remain in the original JSON object.
    $profilePlans = @()
    foreach ($path in $profiles) {
        $originalJson = Get-Content -LiteralPath $path -Raw -Encoding UTF8
        $data = $originalJson | ConvertFrom-Json
        if (!$data -or !$data.PSObject.Properties['profiles'] -or !$data.profiles) { throw "Invalid Launcher profiles JSON: $path" }
        $entry = [PSCustomObject]@{
            name = $profileName
            type = 'custom'
            created = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
            lastVersionId = $versionId
            gameDir = $gameDir
            icon = 'Chest'
        }
        if ($data.profiles.PSObject.Properties[$profileId]) {
            $entry = $data.profiles.$profileId
            foreach ($pair in @{ name = $profileName; type = 'custom'; lastVersionId = $versionId; gameDir = $gameDir }.GetEnumerator()) {
                $entry | Add-Member -NotePropertyName $pair.Key -NotePropertyValue $pair.Value -Force
            }
        }
        $data.profiles | Add-Member -NotePropertyName $profileId -NotePropertyValue $entry -Force
        $profilePlans += [PSCustomObject]@{ Path = $path; OriginalJson = $originalJson; Json = ($data | ConvertTo-Json -Depth 100) }
    }

    $backupDir = Join-Path $MinecraftPath ('tun-tun-install-backups/' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 6))
    $undo = New-Object System.Collections.ArrayList
    $temporaryFiles = New-Object System.Collections.ArrayList
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    function Save-Original([string]$target) {
        if (Test-Path -LiteralPath $target -PathType Container) { throw "A directory occupies an expected file path: $target" }
        $exists = Test-Path -LiteralPath $target -PathType Leaf
        $saved = $null
        if ($exists) {
            [IO.Directory]::CreateDirectory($backupDir) | Out-Null
            $saved = Join-Path $backupDir ([Guid]::NewGuid().ToString('N') + '-' + [IO.Path]::GetFileName($target))
            Copy-Item -LiteralPath $target -Destination $saved
        }
        [void]$undo.Add([PSCustomObject]@{ Target = $target; Saved = $saved; Existed = $exists })
    }
    function Write-Atomic([string]$target, [string]$source, [string]$json) {
        Save-Original $target
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
        $tmp = $target + '.' + [Guid]::NewGuid().ToString('N') + '.tmp'
        [void]$temporaryFiles.Add($tmp)
        if ($source) { Copy-Item -LiteralPath $source -Destination $tmp }
        else { [IO.File]::WriteAllText($tmp, $json, $utf8) }
        if ([IO.File]::Exists($target)) { [IO.File]::Replace($tmp, $target, [System.Management.Automation.Language.NullString]::Value) }
        else { [IO.File]::Move($tmp, $target) }
    }
    try {
        $retired = Join-Path $gameDir 'resourcepacks/TUNG-White-Glint.zip'
        if (Test-Path -LiteralPath $retired -PathType Leaf) { Save-Original $retired; Remove-Item -LiteralPath $retired }
        $options = Join-Path $gameDir 'options.txt'
        if (Test-Path -LiteralPath $options -PathType Leaf) {
            $text = [IO.File]::ReadAllText($options)
            $lines = $text.Split([char]10)
            $changed = $false
            for ($i = 0; $i -lt $lines.Length; $i++) {
                if ($lines[$i] -match '^(resourcePacks|incompatibleResourcePacks):') {
                    $colon = $lines[$i].IndexOf(':')
                    $ids = @($lines[$i].Substring($colon + 1) | ConvertFrom-Json)
                    $next = @($ids | Where-Object { $_ -cne 'file/TUNG-White-Glint.zip' })
                    if ($next.Count -ne $ids.Count) {
                        $suffix = if ($lines[$i].EndsWith("`r")) { "`r" } else { '' }
                        $lines[$i] = $lines[$i].Substring(0, $colon + 1) + (ConvertTo-Json -InputObject $next -Compress) + $suffix
                        $changed = $true
                    }
                }
            }
            if ($changed) { Write-Atomic $options $null ([string]::Join("`n", $lines)) }
        }
        $versionPath = Join-Path $MinecraftPath ("versions/$versionId/$versionId.json")
        Write-Atomic $versionPath $null $fabricJson
        $modsDir = Join-Path $gameDir 'mods'
        [IO.Directory]::CreateDirectory($modsDir) | Out-Null
        $expected = @($modAssets | ForEach-Object { [IO.Path]::GetFileName($_.path) })
        # Replace old releases only inside this client's isolated game folder.
        Get-ChildItem -LiteralPath $modsDir -File | Where-Object {
            ($_.Name -like 'tung-tung-bridge-*.jar' -or $_.Name -like 'tun-tun-bridge-*.jar' -or $_.Name -like 'amethyst-chunk-finder-*.jar' -or $_.Name -like 'fabric-api-*.jar' -or $_.Name -like 'voicechat-fabric-*.jar' -or $_.Name -like 'ViaFabricPlus-*.jar' -or $_.Name -like 'client_maps-*.jar' -or $_.Name -like 'map-in-slot-*.jar') -and $expected -notcontains $_.Name -and $_.Name -ne 'ViaFabricPlus-5.1.3.jar'
        } | ForEach-Object { Save-Original $_.FullName; Remove-Item -LiteralPath $_.FullName }
        foreach ($asset in $modAssets) {
            $source = Join-Path $BundlePath $asset.path
            Write-Atomic (Join-Path $modsDir ([IO.Path]::GetFileName($source))) $source $null
        }
        if ($manifest.PSObject.Properties['downloads']) {
            foreach ($asset in $manifest.downloads) {
                $uri = [Uri]$asset.url
                $isPack = $asset.PSObject.Properties['kind'] -and $asset.kind -eq 'resourcepack'
                $extension = if ($isPack) { 'zip' } else { 'jar' }
                $trustedSource = $uri.Host -eq 'cdn.modrinth.com' -or ($isPack -and $uri.Host -eq 'packsmc.com' -and $uri.AbsolutePath.StartsWith('/api/packs/') -and $uri.AbsolutePath.EndsWith('/download-file'))
                if ($uri.Scheme -ne 'https' -or !$trustedSource -or $asset.file -notmatch ('^[A-Za-z0-9_.+-]+[.]' + $extension + '$')) { throw 'Invalid optional download.' }
                $tmp = Join-Path ([IO.Path]::GetTempPath()) ([Guid]::NewGuid().ToString() + '.jar')
                [void]$temporaryFiles.Add($tmp)
                Invoke-WebRequest -UseBasicParsing -Uri $uri -OutFile $tmp -TimeoutSec 45
                if ((Get-FileHash -LiteralPath $tmp -Algorithm SHA512).Hash.ToLowerInvariant() -ne $asset.sha512) { throw 'Optional mod checksum mismatch.' }
                $maximumBytes = if ($isPack) { 83886080 } else { 33554432 }
                if ((Get-Item -LiteralPath $tmp).Length -gt $maximumBytes) { throw 'Optional download is too large.' }
                $targetDir = if ($isPack) { Join-Path $gameDir 'resourcepacks' } else { $modsDir }
                Write-Atomic (Join-Path $targetDir $asset.file) $tmp $null
            }
        }
        foreach ($asset in @($manifest.files | Where-Object { $_.kind -eq 'resourcepack' })) {
            $source = Join-Path $BundlePath $asset.path
            Write-Atomic (Join-Path $gameDir ('resourcepacks/' + [IO.Path]::GetFileName($source))) $source $null
        }
        if (Get-Process -Name 'MinecraftLauncher' -ErrorAction SilentlyContinue) {
            throw 'Minecraft Launcher opened during installation. Close it before installing again.'
        }
        foreach ($plan in $profilePlans) {
            $currentJson = Get-Content -LiteralPath $plan.Path -Raw -Encoding UTF8
            if ($currentJson -cne $plan.OriginalJson) { throw 'Launcher profiles changed during installation. Close the Launcher and install again.' }
            Write-Atomic $plan.Path $null $plan.Json
        }
        [IO.Directory]::CreateDirectory($backupDir) | Out-Null
        [IO.File]::WriteAllText((Join-Path $backupDir 'restore-map.json'), ($undo | ConvertTo-Json -Depth 10), $utf8)
        return [PSCustomObject]@{ GameDir = $gameDir; BackupDir = $backupDir; ProfileName = $profileName; VersionId = $versionId }
    } catch {
        $failure = $_
        $rollbackErrors = New-Object System.Collections.ArrayList
        for ($i = $undo.Count - 1; $i -ge 0; $i--) {
            $item = $undo[$i]
            try {
                if ($item.Existed) { Copy-Item -LiteralPath $item.Saved -Destination $item.Target -Force }
                elseif (Test-Path -LiteralPath $item.Target -PathType Leaf) { Remove-Item -LiteralPath $item.Target -Force }
            } catch { [void]$rollbackErrors.Add($_.Exception.Message) }
        }
        if ($rollbackErrors.Count -gt 0) {
            throw ($failure.Exception.Message + ' Recovery could not finish: ' + ($rollbackErrors -join '; ') + '. Original files are in ' + $backupDir)
        }
        throw $failure
    } finally {
        foreach ($tmp in $temporaryFiles) {
            if (Test-Path -LiteralPath $tmp -PathType Leaf) { Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue }
        }
    }
}

# The app invokes this script explicitly after its Install button is clicked.
# Dot-sourcing also exposes Install-TunTun without installing anything.
if ($MyInvocation.InvocationName -ne '.') {
    try {
        [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
        if ([string]::IsNullOrWhiteSpace($MinecraftPath)) { throw 'Choose the Minecraft Launcher .minecraft folder in the client before installing.' }
        $ErrorActionPreference = 'Stop'
        $result = Install-TunTun -MinecraftPath $MinecraftPath -BundlePath $BundlePath
        $result | ConvertTo-Json -Compress
        exit 0
    } catch {
        [Console]::Error.WriteLine($_.Exception.Message)
        exit 1
    }
}
