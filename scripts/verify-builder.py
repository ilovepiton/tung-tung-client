"""Build and verify Auto Builder using bounded generated fixtures; never touches user worlds."""
from pathlib import Path
import gzip, struct, subprocess, os, importlib.util, argparse, shutil, json, zipfile
ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT/'build/builder-qa'
JAVA=Path.home()/'Library/Application Support/minecraft/runtime/java-runtime-epsilon/mac-os-arm64/java-runtime-epsilon/jre.bundle/Contents/Home/bin'
VERSION='1.35.0'
def string(s):
 b=s.encode();return struct.pack('>H',len(b))+b
def payload(t,v):
 if t in (1,2,3,4):return struct.pack({1:'>b',2:'>h',3:'>i',4:'>q'}[t],v)
 if t==8:return string(v)
 if t==7:return struct.pack('>i',len(v))+bytes(v)
 if t==12:return struct.pack('>i',len(v))+b''.join(struct.pack('>Q',n&((1<<64)-1)) for n in v)
 if t==9:
  st,items=v;return bytes([st])+struct.pack('>i',len(items))+b''.join(payload(st,x) for x in items)
 if t==10:return b''.join(bytes([st])+string(k)+payload(st,x) for k,st,x in v)+b'\0'
 raise ValueError(t)
def compound(**values):return [(k,3,v) for k,v in values.items()]
def write(name,tags,compressed=True):
 data=b'\x0a\0\0'+payload(10,tags);(WORK/'fixtures'/name).write_bytes(gzip.compress(data) if compressed else data)
def sponge(version=2,**changes):
 palette=[('minecraft:air',3,0),('minecraft:stone',3,1),('minecraft:oak_log[axis=x]',3,2)]
 tags=[('Version',3,version),('Width',2,3),('Height',2,2),('Length',2,2)]
 data=bytes([1,1,0,0,2,0,1,1,0,0,1,0])
 block=[('Palette',10,changes.get('palette',palette)),('Data' if version==3 else 'BlockData',7,changes.get('data',data))]
 if version==3:tags+=[('Blocks',10,block)];return [('Schematic',10,tags)]
 return [(k,t,changes.get(k,v)) for k,t,v in tags]+block
def region(size,pos,states,ids):
 bits=max(2,(len(states)-1).bit_length());words=[0]*((len(ids)*bits+63)//64)
 for i,v in enumerate(ids):
  bit=i*bits;words[bit//64]|=v<<(bit%64)
  if bit%64+bits>64:words[bit//64+1]|=v>>(64-bit%64)
 return [('Size',10,compound(x=size[0],y=size[1],z=size[2])),('Position',10,compound(x=pos[0],y=pos[1],z=pos[2])),('BlockStatePalette',9,(10,[[('Name',8,s)] for s in states])),('BlockStates',12,words)]
def fixtures():
 (WORK/'fixtures').mkdir(parents=True,exist_ok=True)
 write('wall.schem',sponge());write('wrapped.schem',sponge(3))
 base=['minecraft:air','minecraft:stone']
 signed=region((-2,1,-2),(1,0,1),base,[0,1,1,1]);write('signed.litematic',[('Version',3,6),('Regions',10,[('main',10,signed)])])
 cross=region((23,1,1),(0,0,0),['minecraft:air','minecraft:stone','minecraft:oak_planks','minecraft:bricks','minecraft:glass'],[1]*21+[4,2]);write('cross-word.litematic',[('Version',3,6),('Regions',10,[('main',10,cross)])])
 a=region((1,1,1),(0,0,0),base,[1]);b=region((1,1,1),(5,0,0),base,[1]);write('multi.litematic',[('Version',3,6),('Regions',10,[('A',10,a),('B',10,b)])])
 struct_tags=[('size',9,(3,[3,1,1])),('palette',9,(10,[[('Name',8,'minecraft:stone')]])),('blocks',9,(10,[[('pos',9,(3,[0,0,0])),('state',3,0)],[('pos',9,(3,[2,0,0])),('state',3,0),('nbt',10,[('id',8,'minecraft:chest')])]]))]
 write('structure.nbt',struct_tags);write('plain.nbt',struct_tags,False)
 write('legacy.schematic',[('Blocks',7,[1])]);write('bad-index.schem',sponge(data=bytes([3]*12)))
 write('truncated.schem',sponge(data=bytes([1])));write('oversized.schem',sponge(Width=513));write('bad-varint.schem',sponge(data=b'\xff'*12));write('extra-data.schem',sponge(data=b'\x01'*13));write('bad-state.schem',sponge(palette=[('minecraft:stone{Command:bad}',3,0)]));write('empty.schem',sponge(data=bytes(12)))
 write('duplicate-key.nbt',[('Version',3,2),('Version',3,2)])
 (WORK/'fixtures/negative-array.nbt').write_bytes(b'\x0a\0\0\x07'+string('bad')+struct.pack('>i',-1))
 deep=[]
 for _ in range(55):deep=[('nested',10,deep)]
 write('deep.nbt',deep)
 write('invalid-packed.litematic',[('Version',3,6),('Regions',10,[('main',10,[(k,t,[] if k=='BlockStates' else v) for k,t,v in cross])])])
 conflict=region((1,1,1),(0,0,0),['minecraft:air','minecraft:glass'],[1]);write('conflict.litematic',[('Version',3,6),('Regions',10,[('A',10,a),('B',10,conflict)])])
 # A useful small real build fixture for gameplay checks.
 write('qa-wall.schem',[('Version',3,2),('Width',2,2),('Height',2,2),('Length',2,1),('Palette',10,[('minecraft:stone',3,0)]),('BlockData',7,bytes(4))])
def build():
 spec=importlib.util.spec_from_file_location('release_builder',ROOT/'.tools/build-game-1.25.0.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
 m.VERSION=VERSION;m.OUT=ROOT/f'build/game-{VERSION}';m.CLASSES=m.OUT/'classes';m.CLASSES.mkdir(parents=True,exist_ok=True)
 cp=(ROOT/'.tools/release-1.10.0/classpath.txt').read_text().strip().split(os.pathsep)+list(map(str,(ROOT/'.tools/release-1.10.0/compile-fabric').glob('*.jar')))+[str(ROOT/'.tools/voicechat-fabric-2.6.27+26.3.jar')]
 m.build(cp)
 classes=WORK/'classes';classes.mkdir(parents=True,exist_ok=True)
 subprocess.run([str(JAVA/'javac'),'--release','25','-d',str(classes),str(ROOT/'src/main/java/dev/amethyst/finder/builder/SchematicFile.java'),str(ROOT/'src/test/java/dev/amethyst/finder/builder/SchematicChecks.java')],check=True)
 subprocess.run([str(JAVA/'java'),'-ea','-cp',str(classes),'dev.amethyst.finder.builder.SchematicChecks',str(WORK/'fixtures')],check=True)
def game():
 game=WORK/'game';classes=WORK/'game-classes';output=ROOT/'artifacts/builder-1.35.0/engine'
 for p in [game/'mods',game/'config/tuntun-schematics',classes,output]:p.mkdir(parents=True,exist_ok=True)
 if (game/'saves/BuilderQA').exists():shutil.rmtree(game/'saves/BuilderQA')
 (output/'report.json').unlink(missing_ok=True)
 shutil.copy2(WORK/'fixtures/qa-wall.schem',game/'config/tuntun-schematics/00000000000000000000000000000000.schem')
 core=ROOT/f'build/libs/tung-tung-bridge-{VERSION}.jar'
 cp=(ROOT/'.tools/release-1.10.0/classpath.txt').read_text().strip().split(os.pathsep)
 compile_cp=cp+[str(core)]+list(map(str,(ROOT/'.tools/release-1.10.0/compile-fabric').glob('*.jar')))
 sources=['src/qa/java/qa/BuilderQa.java','src/qa/java/qa/mixin/BuilderMouseIsolationMixin.java','src/qa/java/qa/mixin/UtilityFocusMixin.java']
 subprocess.run([str(JAVA/'javac'),'--release','25','-proc:none','-cp',os.pathsep.join(compile_cp),'-d',str(classes),*[str(ROOT/s) for s in sources]],check=True)
 with zipfile.ZipFile(game/'mods/builder-qa.jar','w',zipfile.ZIP_DEFLATED) as z:
  z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'builder_qa','version':'1','environment':'client','entrypoints':{'client':['qa.BuilderQa']},'mixins':['builder-qa.mixins.json'],'depends':{'amethyst_chunk_finder':'*'}}))
  z.writestr('builder-qa.mixins.json',json.dumps({'required':True,'package':'qa.mixin','compatibilityLevel':'JAVA_25','client':['BuilderMouseIsolationMixin','UtilityFocusMixin'],'injectors':{'defaultRequire':1}}))
  for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
 for p in game.glob('mods/tung-tung-bridge-*.jar'):p.unlink()
 for p in [core,ROOT/'.tools/fabric-api-0.162.0+26.3.jar']:shutil.copy2(p,game/'mods'/p.name)
 (game/'options.txt').write_text('resourcePacks:[]\nenableVsync:true\nmaxFps:40\nrenderDistance:2\nsimulationDistance:5\nfullscreen:false\n')
 natives=ROOT/'.tools/combat-1.13/natives';meta=json.loads((ROOT/'.tools/minecraft-26.3.json').read_text())
 args=[str(JAVA/'java'),'-XstartOnFirstThread','-Xmx1200M','-Dtung.builder.debug=true',f'-Dtung.qa.output={output}','--enable-native-access=ALL-UNNAMED',f'-Djava.library.path={natives}',f'-Dorg.lwjgl.system.SharedLibraryExtractPath={natives}','-cp',os.pathsep.join(cp),'net.fabricmc.loader.impl.launch.knot.KnotClient','--username','BuilderQA','--uuid','11223344556677889900112233445566','--accessToken','0','--userType','msa','--version','26.3','--versionType','release','--gameDir',str(game),'--assetsDir',str(Path.home()/'Library/Application Support/minecraft/assets'),'--assetIndex',meta['assetIndex']['id'],'--width','960','--height','640']
 with (output/'launch.log').open('w') as log:
  child=subprocess.Popen(args,cwd=game,stdout=log,stderr=subprocess.STDOUT)
  print(f'Disposable offline Builder QA started (PID {child.pid}).',flush=True)
  try:code=child.wait(timeout=300)
  except subprocess.TimeoutExpired:child.terminate();child.wait(timeout=20);raise
 report=json.loads((output/'report.json').read_text()) if (output/'report.json').exists() else {}
 passed=code==0 and report.get('stage')==10 and not report.get('error') and all(c['passed'] for c in report.get('checks',[]))
 print(json.dumps({'ok':passed,'exit':code,'stage':report.get('stage'),'checks':len(report.get('checks',[])),'error':report.get('error')},indent=2),flush=True)
 if not passed:raise SystemExit(1)
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--game',action='store_true');args=parser.parse_args()
 fixtures();build()
 if args.game:game()
