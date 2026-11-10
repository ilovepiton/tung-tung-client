"""Verify the Chess desktop package; never launches a game or modifies installed profiles."""
from pathlib import Path
import hashlib
import json
import plistlib
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'dist/Chess-1.29.7/Tung Tung Client.app'
QA = ROOT / 'artifacts/chess-auto-next'
EXPECTED_CORE = 'f1d1a4af8bef53147fa986a6f05b289e5d4f4b70f8e81a4dacdc153dc39d632a'

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024*1024), b''): h.update(chunk)
    return h.hexdigest()

def main():
    resources = APP / 'Contents/Resources'
    info = plistlib.loads((APP/'Contents/Info.plist').read_bytes())
    assert info['CFBundleShortVersionString']=='1.29.7' and info['CFBundleVersion']=='1297'
    subprocess.run(['codesign','--verify','--deep','--strict',str(APP)],check=True)
    architectures = subprocess.check_output(['xcrun','lipo','-archs',str(APP/'Contents/MacOS/TungTungClient')],text=True).split()
    assert set(architectures)=={'arm64','x86_64'}
    payload = json.loads((resources/'payload/manifest.json').read_text())
    assert payload['version']=='1.29.5' and payload['desktopVersion']=='1.29.7'
    for row in payload['files']: assert sha(resources/'payload'/row['path'])==row['sha256'], row['path']
    assert sha(resources/'payload/mods/tung-tung-bridge-1.29.5.jar')==EXPECTED_CORE
    for name in ('engine.js','native.js'): assert sha(ROOT/'chess'/name)==sha(resources/'chess'/name)
    for path in (ROOT/'chess/extension').iterdir():
        if path.is_file(): assert sha(path)==sha(resources/'chess-extension'/path.name), path.name
    assert sha(ROOT/'chess/engine.js')==sha(resources/'chess-extension/engine.js')
    for path in (ROOT/'macos/ui').rglob('*'):
        if path.is_file() and path.suffix in ('.js','.css','.html'):
            assert sha(path)==sha(resources/'ui'/path.relative_to(ROOT/'macos/ui')), path.name
    manifest = json.loads((resources/'chess-extension/manifest.json').read_text())
    assert set(manifest['permissions'])=={'storage','scripting'}
    assert set(manifest['host_permissions'])=={'https://www.chess.com/*','https://chess.com/*','http://127.0.0.1/*'}
    tests = {}
    for name, expected in [('engine-checks',49),('adapter-checks',8),('session-checks',25),('transport-checks',13),('native-plan-checks',27),('background-checks',12),('ui/report',32),('public-clock-check',4)]:
        result=json.loads((QA/(name+'.json')).read_text())
        assert result['ok'] and result['checks']==expected, name
        tests[name]=expected
    extension = ROOT/'dist/Chess-1.29.7/chess-extension'
    if extension.exists(): shutil.rmtree(extension)
    shutil.copytree(resources/'chess-extension',extension)
    extension_zip = ROOT/'dist/Tung-Chess-Chrome-1.0.0.zip'
    with zipfile.ZipFile(extension_zip,'w',compression=zipfile.ZIP_DEFLATED) as z:
        for path in sorted(extension.iterdir()): z.write(path,'chess-extension/'+path.name)
    archive=ROOT/'dist/Tung-Tung-Client-Mac-1.29.7-Voice-Chat.zip'
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None
        assert hashlib.sha256(z.read('Tung Tung Client.app/Contents/Resources/payload/mods/tung-tung-bridge-1.29.5.jar')).hexdigest()==EXPECTED_CORE
        assert not any('/tests/' in p and '/chess' in p for p in z.namelist())
    report={'ok':True,'desktopVersion':'1.29.7','minecraftCoreVersion':'1.29.5','coreSHA256':EXPECTED_CORE,
        'architectures':architectures,'testChecks':tests,'checksTotal':sum(tests.values()),
        'app':str(APP),'executableSHA256':sha(APP/'Contents/MacOS/TungTungClient'),
        'archive':str(archive),'archiveSHA256':sha(archive),'extensionArchive':str(extension_zip),
        'extensionSHA256':sha(extension_zip),'extensionId':'kbaaahmkgknhojdafppilohndobfpdci',
        'liveGamesStarted':False,'nativePermissionGranted':False,'extensionInstalled':False,
        'verificationScope':'Production UI fixtures, state machines, real local HTTP transport, MV3 boundaries, native AX plan fixtures, anonymous public web clock selection. No end-to-end live result or native AX action test.',
        'pendingAutoTotemCandidateUnchanged':sha(ROOT/'build/libs/tung-tung-bridge-1.29.6.jar')=='72f94036687007607bd9b1c55c172b3a07d83bb7f7a298787d7871395372e6f3'}
    assert report['pendingAutoTotemCandidateUnchanged']
    (QA/'release-report.json').write_text(json.dumps(report,indent=2)+'\n')
    for path in (archive,extension_zip): (path.with_name(path.name+'.sha256')).write_text(sha(path)+'  '+path.name+'\n')
    print(json.dumps(report,indent=2))

if __name__=='__main__': main()
