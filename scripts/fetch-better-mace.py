"""Fetch exact original mod and dependencies from their pinned official URLs."""
from pathlib import Path
import hashlib,json,subprocess,os
root=Path(__file__).resolve().parents[1]
folder=root/'.tools/bettermaceswap';folder.mkdir(parents=True,exist_ok=True)
for mod in json.loads((root/'packaging/better-mace-mods.json').read_text()):
 target=folder/mod['file']
 if target.is_file() and hashlib.sha256(target.read_bytes()).hexdigest()==mod['sha256']:continue
 pending=target.with_suffix('.download')
 try:
  subprocess.run(['curl','--fail','--location','--retry','2','--connect-timeout','20','--max-time','180','--output',str(pending),mod['url']],check=True)
  if hashlib.sha256(pending.read_bytes()).hexdigest()!=mod['sha256']:raise RuntimeError('Checksum mismatch: '+mod['file'])
  os.replace(pending,target)
 finally:pending.unlink(missing_ok=True)
print('BetterMaceSwap and both dependencies verified.')
