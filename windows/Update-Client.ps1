param([string]$Source,[string]$Target,[string]$MinecraftPath,[int]$ParentPid,[string]$Work)
$ErrorActionPreference='Stop'
$backup=Join-Path $Work ('backup-'+[guid]::NewGuid().ToString('N'))
$changed=New-Object System.Collections.Generic.List[object]
try {
    $parent=Get-Process -Id $ParentPid -ErrorAction SilentlyContinue
    if($parent -and !$parent.WaitForExit(30000)){throw 'The client did not close; nothing replaced.'}
    $exe=Join-Path $Target 'Tung-Tung-Client.exe'
    if(!(Test-Path -LiteralPath $exe)){throw 'Installed client not found.'}
    $output = & (Join-Path $PSHOME 'powershell.exe') -NoLogo -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Source 'desktop/Install-Core.ps1') -MinecraftPath $MinecraftPath -BundlePath $Source 2>&1 | Out-String
    if($LASTEXITCODE -ne 0){throw ('Fabric core update failed: '+$output)}
    New-Item -ItemType Directory -Path $backup -Force|Out-Null
    $prefix=$Source.TrimEnd('\','/')+[IO.Path]::DirectorySeparatorChar
    foreach($file in Get-ChildItem -LiteralPath $Source -Recurse -File) {
        $relative=$file.FullName.Substring($prefix.Length)
        $dest=Join-Path $Target $relative
        $saved=Join-Path $backup $relative
        $existed=Test-Path -LiteralPath $dest
        if($existed){New-Item -ItemType Directory -Path (Split-Path $saved) -Force|Out-Null;Copy-Item -LiteralPath $dest -Destination $saved}
        New-Item -ItemType Directory -Path (Split-Path $dest) -Force|Out-Null
        $changed.Add(@{dest=$dest;saved=$saved;existed=$existed})
        $copied=$false
        for($attempt=0;$attempt -lt 30;$attempt++) {
            try {Copy-Item -LiteralPath $file.FullName -Destination $dest -Force;$copied=$true;break} catch {Start-Sleep -Milliseconds 500}
        }
        if(!$copied){throw "Could not replace $relative"}
    }
    Start-Process -FilePath $exe
} catch {
    for($i=$changed.Count-1;$i -ge 0;$i--){$entry=$changed[$i];if($entry.existed){Copy-Item -LiteralPath $entry.saved -Destination $entry.dest -Force -ErrorAction SilentlyContinue}else{Remove-Item -LiteralPath $entry.dest -Force -ErrorAction SilentlyContinue}}
    $_.Exception.Message|Set-Content -LiteralPath (Join-Path $Work 'apply-error.txt')
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show($_.Exception.Message,'Tung Tung update')|Out-Null
    if(Test-Path -LiteralPath (Join-Path $Target 'Tung-Tung-Client.exe')){Start-Process -FilePath (Join-Path $Target 'Tung-Tung-Client.exe')}
    exit 1
}
