"""Load the real mod under Fabric, verify injected handlers, exit before opening Minecraft."""
from pathlib import Path
import os,json,subprocess,zipfile,shutil
root=Path(__file__).resolve().parents[1]
work=root/'build/bms-mixin-smoke';work.mkdir(parents=True,exist_ok=True)
java_home=os.environ.get('JAVA_HOME')
javac=str(Path(java_home)/'bin/javac') if java_home else shutil.which('javac')
loader=next((Path.home()/'.gradle/caches/modules-2/files-2.1/net.fabricmc/fabric-loader/0.19.5').glob('*/*.jar'))
classes=work/'classes';classes.mkdir(exist_ok=True)
subprocess.run([javac,'-cp',str(loader),'-d',str(classes),str(root/'src/qa/java/qa/BetterMaceSmoke.java')],check=True)
with zipfile.ZipFile(work/'smoke.jar','w') as z:
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'tung_bms_smoke','version':'1.0.0','environment':'client','entrypoints':{'preLaunch':['qa.BetterMaceSmoke']}}))
 z.write(classes/'qa/BetterMaceSmoke.class','qa/BetterMaceSmoke.class')
init=work/'init.gradle';init.write_text("allprojects { afterEvaluate { dependencies.add('runtimeOnly', files('build/bms-mixin-smoke/smoke.jar')) } }\n")
gradle=root/('gradlew.bat' if os.name=='nt' else 'gradlew')
subprocess.run([str(gradle),'--init-script',str(init),'runClient','--no-daemon','--console=plain'],cwd=root,check=True)
