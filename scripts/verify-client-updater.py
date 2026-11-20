"""Verify updater contracts and the actual Mac replacement function on isolated tiny apps."""
from pathlib import Path
import tempfile,plistlib,subprocess,json
ROOT=Path(__file__).resolve().parents[1]
WORK=Path(tempfile.mkdtemp(prefix='updater-helper-',dir=ROOT/'build'))
# Only the two installer/error dependencies are stubbed; test the production parser/downloader source.
checks=(ROOT/'src/test/swift/ClientUpdaterContractTests.swift').read_text()
(WORK/'checks.swift').write_text(checks)
subprocess.run(['xcrun','swiftc','-parse-as-library','-swift-version','5',str(ROOT/'macos/Sources/ClientUpdater.swift'),str(WORK/'checks.swift'),'-o',str(WORK/'checks')],check=True)
subprocess.run([str(WORK/'checks')],check=True)
for name,ver in [('source.app','2.0.0'),('target.app','1.0.0')]:
 p=WORK/name/'Contents';(p/'MacOS').mkdir(parents=True)
 (p/'Info.plist').write_bytes(plistlib.dumps({'CFBundleIdentifier':'dev.tungtung.client','CFBundleExecutable':'Test','CFBundleName':'Update fixture','CFBundlePackageType':'APPL','CFBundleShortVersionString':ver,'CFBundleVersion':'1'}))
 subprocess.run(['cp','/usr/bin/true',str(p/'MacOS/Test')],check=True)
 (p/'Resources').mkdir();(p/'Resources/marker').write_text(ver)
 subprocess.run(['codesign','--force','--deep','--sign','-',str(p.parent)],check=True)
# Exercise only the real replacement function, never the real-app discovery or launch portion.
script=(ROOT/'macos/update-apply.sh').read_text().split('apply_one "$active_app" active')[0]+'apply_one "$active_app" active\n'
path=WORK/'test-helper.sh';path.write_text(script)
(WORK/'work').mkdir()
subprocess.run(['bash',str(path),str(WORK/'source.app'),str(WORK/'target.app'),'99999999',str(WORK/'work')],check=True)
assert (WORK/'target.app/Contents/Resources/marker').read_text()=='2.0.0'
assert list((WORK/'work').glob('backup-active-*/Contents/Resources/marker'))[0].read_text()=='1.0.0'
report={'passed':True,'scope':'version / checksum / source contracts; real Mac helper replacement and backup on isolated fixtures','liveSelfUpdateTested':False,'windowsRuntimeTested':False}
( ROOT/'artifacts/shield-single-1.35.3').mkdir(exist_ok=True)
(ROOT/'artifacts/shield-single-1.35.3/updater-checks.json').write_text(json.dumps(report,indent=2)+'\n')
print('PASS: actual Mac helper replaced fixture app and retained original backup')
