param([Parameter(Mandatory=$true)][string]$BundlePath)
$ErrorActionPreference = 'Stop'
$productionBundle = (Resolve-Path $BundlePath).Path
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $root 'windows/Install-Core.ps1')
$fixture = Join-Path $root ('build/windows-installer-qa-' + [Guid]::NewGuid().ToString('N'))
$bundle = Join-Path $fixture 'bundle'
$minecraft = Join-Path $fixture 'minecraft'
[IO.Directory]::CreateDirectory($bundle) | Out-Null
[IO.Directory]::CreateDirectory($minecraft) | Out-Null
$manifest = Get-Content (Join-Path $productionBundle 'manifest.json') -Raw | ConvertFrom-Json
# Exercise the real five-mod payload, without optional network downloads or textures.
$manifest.files = @($manifest.files | Where-Object { $_.kind -ne 'resourcepack' })
$manifest.downloads = @()
foreach ($file in $manifest.files) {
    $destination = Join-Path $bundle $file.path
    [IO.Directory]::CreateDirectory((Split-Path -Parent $destination)) | Out-Null
    Copy-Item (Join-Path $productionBundle $file.path) $destination
}
$manifestPath = Join-Path $bundle 'manifest.json'
$manifest | ConvertTo-Json -Depth 100 | Set-Content $manifestPath
$profiles = Join-Path $minecraft 'launcher_profiles.json'
@{profiles=@{unrelated=@{name='Keep this profile';type='custom'}};settings=@{marker='unchanged'}} | ConvertTo-Json -Depth 20 | Set-Content $profiles
$mods = Join-Path $minecraft 'tun-tun-26.3/mods'
[IO.Directory]::CreateDirectory($mods) | Out-Null
[IO.File]::WriteAllText((Join-Path $mods 'voicechat-fabric-2.6.24+26.3.jar'), 'old managed mod')
[IO.File]::WriteAllText((Join-Path $mods 'my-unrelated-mod.jar'), 'keep user mod')
$result = Install-TunTun -MinecraftPath $minecraft -BundlePath $bundle
if ($result.ProfileName -ne 'Tung Tung Client 26.3') { throw 'Wrong ordinary profile name' }
$after = Get-Content $profiles -Raw | ConvertFrom-Json
if ($after.profiles.unrelated.name -ne 'Keep this profile' -or $after.settings.marker -ne 'unchanged') { throw 'Unrelated Launcher data changed' }
foreach ($file in @($manifest.files | Where-Object {$_.kind -eq 'mod'})) {
    $installed = Join-Path $mods ([IO.Path]::GetFileName($file.path))
    if ((Get-FileHash $installed -Algorithm SHA256).Hash.ToLowerInvariant() -ne $file.sha256) { throw 'Installed mod differs from bundle' }
}
if (Test-Path (Join-Path $mods 'voicechat-fabric-2.6.24+26.3.jar')) { throw 'Old Voice Chat left active' }
if ([IO.File]::ReadAllText((Join-Path $mods 'my-unrelated-mod.jar')) -ne 'keep user mod') { throw 'Unrelated mod changed' }
$before = [IO.File]::ReadAllText($profiles)
$voice = @($manifest.files | Where-Object {$_.path -like '*voicechat*'})[0]
[IO.File]::WriteAllText((Join-Path $bundle $voice.path), 'altered voice jar')
$voice.sha256 = (Get-FileHash (Join-Path $bundle $voice.path) -Algorithm SHA256).Hash.ToLowerInvariant()
$manifest | ConvertTo-Json -Depth 100 | Set-Content $manifestPath
$rejected = $false
try { Install-TunTun -MinecraftPath $minecraft -BundlePath $bundle | Out-Null } catch {
    if ($_.Exception.Message -notlike '*official pinned download*') { throw }
    $rejected = $true
}
if (!$rejected) { throw 'Rehashed altered voice mod accepted' }
if ([IO.File]::ReadAllText($profiles) -cne $before) { throw 'Rejected install mutated profiles' }
Write-Output 'PASS: ordinary profile, five mods, voice upgrade, unrelated data preservation and independent official voice pin'
