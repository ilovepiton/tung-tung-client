"""Install 1.36.3 apps and pinned BetterMaceSwap with rollback; never change launcher accounts."""
from pathlib import Path
import hashlib,json,os,plistlib,shutil,subprocess,uuid,zipfile
import importlib.util,argparse
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('guard',ROOT/'scripts/install-shield-fall.py');guard=importlib.util.module_from_spec(spec);spec.loader.exec_module(guard)
VERSION='1.36.3';SOURCE=ROOT/f'dist/Tung-Tung-Client-Mac-{VERSION}/Tung Tung Client.app';PROFILE=Path.home()/'Library/Application Support/minecraft/tun-tun-26.3'
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def verify(app):
 assert plistlib.loads((app/'Contents/Info.plist').read_bytes())['CFBundleShortVersionString']==VERSION
 subprocess.run(['codesign','--verify','--deep','--strict',str(app)],check=True)
def install():
 running=guard.blockers()
 if running:return {'installed':False,'reason':'Close Minecraft and Tung Tung Client first.','runningProcesses':running}
 verify(SOURCE)
 manifest=json.loads((SOURCE/'Contents/Resources/payload/manifest.json').read_text())
 pins=json.loads((ROOT/'packaging/better-mace-mods.json').read_text())
 selected={m['file']:m['sha256'] for m in pins}
 core=ROOT/f'build/libs/tung-tung-bridge-{VERSION}.jar';selected[core.name]=digest(core)
 payload=SOURCE/'Contents/Resources/payload/mods'
 for name,sha in selected.items():
  assert digest(payload/name)==sha
  assert any(f['path']=='mods/'+name and f['sha256']==sha for f in manifest['files'])
 mods=PROFILE/'mods';assert mods.is_dir()
 apps=[p for p in [Path('/Applications/Tung Tung Client.app'),Path.home()/'Applications/Tung Tung Client.app'] if p.is_dir()];assert apps
 backup=ROOT/'build/install-backups/better-mace-1.36.3'/uuid.uuid4().hex
 (backup/'mods').mkdir(parents=True);staged=[];replaced=[];saved=[];written=[]
 try:
  for i,app in enumerate(apps):
   tmp=backup/'staging'/str(i)/app.name;tmp.parent.mkdir(parents=True)
   subprocess.run(['/bin/cp','-cR',str(SOURCE),str(tmp)],check=True);verify(tmp);staged.append(tmp)
  assert not guard.blockers(),'Game restarted; installation cancelled.'
  for i,(app,tmp) in enumerate(zip(apps,staged)):
   store=backup/f'application-{i}';store.mkdir();(app/'Contents').rename(store/'Contents');replaced.append((app,store));(tmp/'Contents').rename(app/'Contents');verify(app)
  for old in mods.glob('*.jar'):
   managed=old.name.startswith(('tung-tung-bridge-','tun-tun-bridge-','amethyst-chunk-finder-'))
   try:
    with zipfile.ZipFile(old) as z:managed=managed or json.loads(z.read('fabric.mod.json')).get('id') in {'bettermaceswap','cloth-config','modmenu'}
   except (OSError,KeyError,ValueError,zipfile.BadZipFile):pass
   if managed:
    store=backup/'mods'/old.name;old.rename(store);saved.append((old,store))
  for name,sha in selected.items():
   target=mods/name
   if target.exists():
    store=backup/'mods'/name;assert not store.exists();target.rename(store);saved.append((target,store))
   pending=target.with_suffix('.pending');shutil.copy2(payload/name,pending);assert digest(pending)==sha;os.replace(pending,target);written.append(target)
  report={'installed':True,'version':VERSION,'applications':list(map(str,apps)),'profile':str(PROFILE),'backup':str(backup),'mods':selected,'launcherMetadataChanged':False,'accountFilesAccessed':False}
  out=ROOT/'artifacts/better-mace-1.36.3/installation.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(report,indent=2)+'\n');return report
 except BaseException:
  for p in written:p.unlink(missing_ok=True)
  for target,store in reversed(saved):store.rename(target)
  for app,store in reversed(replaced):
   if (app/'Contents').exists():shutil.rmtree(app/'Contents')
   (store/'Contents').rename(app/'Contents')
  raise
 finally:
  for tmp in staged:
   if tmp.exists():shutil.rmtree(tmp)
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--apply',action='store_true');args=parser.parse_args()
 print(json.dumps(install() if args.apply else {'runningProcesses':guard.blockers()},indent=2))
