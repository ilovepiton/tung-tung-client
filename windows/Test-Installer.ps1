param([Parameter(Mandatory=$true)][string]$BundlePath)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Install-Core.ps1') -BundlePath $BundlePath
$root = Join-Path ([IO.Path]::GetTempPath()) ('tung-tung-windows-qa-' + [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($root) | Out-Null
$checks = [Collections.Generic.List[string]]::new()
function Assert([bool]$value, [string]$label) { if (!$value) { throw $label }; $checks.Add($label) }
function Launcher([string]$name) {
    $folder = Join-Path $root $name
    [IO.Directory]::CreateDirectory($folder) | Out-Null
    $json = '{"profiles":{"vanilla":{"name":"Existing world","lastVersionId":"26.3","javaArgs":"-Xmx4G"}},"settings":{"enableSnapshots":true},"authenticationDatabase":{"example":{"retained":"yes"}}}'
    [IO.File]::WriteAllText((Join-Path $folder 'launcher_profiles.json'), $json)
    [IO.File]::WriteAllText((Join-Path $folder 'launcher_profiles_microsoft_store.json'), $json)
    return $folder
}
try {
    $folder = Launcher 'Fresh install with spaces'
    $original = [IO.File]::ReadAllBytes((Join-Path $folder 'launcher_profiles.json'))
    $result = Install-TunTun -MinecraftPath $folder -BundlePath $BundlePath
    $document = Get-Content -LiteralPath (Join-Path $folder 'launcher_profiles.json') -Raw | ConvertFrom-Json
    Assert ($document.profiles.vanilla.javaArgs -eq '-Xmx4G' -and $document.settings.enableSnapshots) 'Existing profiles and settings survive'
    Assert ($document.authenticationDatabase.example.retained -eq 'yes') 'Account metadata is preserved'
    Assert ($document.profiles.'tun-tun-crimson-26-3'.gameDir -eq $result.GameDir) 'Profile uses its isolated game directory'
    $store = Get-Content -LiteralPath (Join-Path $folder 'launcher_profiles_microsoft_store.json') -Raw | ConvertFrom-Json
    Assert ($store.profiles.'tun-tun-crimson-26-3'.name -eq 'Tung Tung Client 26.3') 'Store Launcher profile is installed too'
    Assert (@(Get-ChildItem (Join-Path $result.GameDir 'mods') -File).Count -eq 3) 'Bridge, Fabric API and voice mod are installed'
    $voice = Join-Path $result.GameDir 'mods/voicechat-fabric-2.6.24+26.3.jar'
    Assert ((Get-FileHash $voice -Algorithm SHA256).Hash.ToLowerInvariant() -eq '97074fa1d094a22f17d5ac11282176965a45137950b66627d3813618810c3d00') 'Voice mod matches the pinned release'
    $backup = Get-ChildItem $result.BackupDir -Filter '*launcher_profiles.json' | Select-Object -First 1
    Assert ([Convert]::ToBase64String([IO.File]::ReadAllBytes($backup.FullName)) -eq [Convert]::ToBase64String($original)) 'Original Launcher file has a byte-identical backup'
    [IO.File]::WriteAllText((Join-Path $result.GameDir 'mods/voicechat-fabric-old.jar'), 'old')
    $again = Install-TunTun -MinecraftPath $folder -BundlePath $BundlePath
    Assert (@(Get-ChildItem (Join-Path $again.GameDir 'mods') -File).Count -eq 3) 'Repeat install removes old voice versions without duplicates'
    $broken = Launcher 'Rollback'
    $before = [IO.File]::ReadAllText((Join-Path $broken 'launcher_profiles.json'))
    [IO.Directory]::CreateDirectory((Join-Path $broken 'tun-tun-26.3/mods/tung-tung-bridge-1.14.0.jar')) | Out-Null
    $failed = $false
    try { Install-TunTun -MinecraftPath $broken -BundlePath $BundlePath | Out-Null } catch { $failed = $true }
    Assert $failed 'A conflicting destination fails safely'
    Assert ([IO.File]::ReadAllText((Join-Path $broken 'launcher_profiles.json')) -ceq $before) 'Failed installation leaves Launcher profiles intact'
    Assert (!(Test-Path (Join-Path $broken 'versions/fabric-loader-0.19.5-26.3/fabric-loader-0.19.5-26.3.json'))) 'Failed installation rolls back the Fabric version'
    $copy = Join-Path $root 'Tampered bundle'; Copy-Item -LiteralPath $BundlePath -Destination $copy -Recurse
    [IO.File]::AppendAllText((Join-Path $copy 'payload/mods/tung-tung-bridge-1.14.0.jar'), 'tamper')
    $target = Launcher 'Checksum rejection'; $failed = $false
    try { Install-TunTun -MinecraftPath $target -BundlePath $copy | Out-Null } catch { $failed = $true }
    Assert $failed 'Tampered payload is rejected'
    Assert (!(Test-Path (Join-Path $target 'tun-tun-26.3/mods'))) 'Checksum rejection happens before any mods are written'
    @{ok=$true;checks=$checks;scope='Installer logic on isolated fixture folders; Windows process checks require Windows'} | ConvertTo-Json -Depth 5
} finally { Remove-Item -LiteralPath $root -Recurse -Force }
