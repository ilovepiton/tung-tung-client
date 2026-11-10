param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dependencies = Join-Path $root '.tools/windows-build'
Add-Type -Path (Join-Path $PSHOME 'Microsoft.CodeAnalysis.dll')
Add-Type -Path (Join-Path $PSHOME 'Microsoft.CodeAnalysis.CSharp.dll')
$framework = Join-Path $dependencies 'net48/build/.NETFramework/v4.8'
$referencePaths = @('mscorlib.dll','System.dll','System.Core.dll','System.Drawing.dll','System.Windows.Forms.dll','System.Runtime.Serialization.dll') | ForEach-Object { Join-Path $framework $_ }
$referencePaths += @(
    (Join-Path $dependencies 'webview2/lib/net462/Microsoft.Web.WebView2.Core.dll'),
    (Join-Path $dependencies 'webview2/lib/net462/Microsoft.Web.WebView2.WinForms.dll'),
    (Join-Path $dependencies 'newtonsoft/lib/net45/Newtonsoft.Json.dll')
)
$references = [Microsoft.CodeAnalysis.MetadataReference[]]@($referencePaths | ForEach-Object { [Microsoft.CodeAnalysis.MetadataReference]::CreateFromFile($_) })
$trees = [Microsoft.CodeAnalysis.SyntaxTree[]]@('Client.cs','Bridge.cs','RobloxBridge.cs' | ForEach-Object { [Microsoft.CodeAnalysis.CSharp.CSharpSyntaxTree]::ParseText([IO.File]::ReadAllText((Join-Path $PSScriptRoot $_))) })
$options = [Microsoft.CodeAnalysis.CSharp.CSharpCompilationOptions]::new([Microsoft.CodeAnalysis.OutputKind]::WindowsApplication).WithOptimizationLevel([Microsoft.CodeAnalysis.OptimizationLevel]::Release).WithPlatform([Microsoft.CodeAnalysis.Platform]::X64)
$compilation = [Microsoft.CodeAnalysis.CSharp.CSharpCompilation]::Create('Tung-Tung-Client', $trees, $references, $options)
[IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null
$file = [IO.File]::Create((Join-Path $OutputDirectory 'Tung-Tung-Client.exe'))
try { $result = $compilation.Emit($file) } finally { $file.Dispose() }
$diagnostics = @($result.Diagnostics | ForEach-Object { $_.ToString() })
@{ok=$result.Success;target='.NET Framework 4.8 / Windows x64';diagnostics=$diagnostics} | ConvertTo-Json -Depth 6
if (!$result.Success) { exit 1 }
