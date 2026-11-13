#!/usr/bin/env python3
"""Update the ordinary Prism profile snapshot; never include local diagnostic data."""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = '1.33.0'
VOICE = 'voicechat-fabric-2.6.27+26.3.jar'
VOICE_SHA512 = 'f2de66761c0f249bb0f9a4b348f98b97ab4e89d3ed3fd37638420364b7431b757933aa08c0cc3e7ba3dd85a602d6c2de313c2309fc587e93cabf31fc5f6df27e'


def main():
    voice = (ROOT / '.tools' / VOICE).read_bytes()
    core=(ROOT / f'build/libs/tung-tung-bridge-{VERSION}.jar').read_bytes()
    assert hashlib.sha512(voice).hexdigest() == VOICE_SHA512
    baseline = ROOT / 'dist/Tung-Tung-Client-1.30.3-Voice-Chat-Prism.zip'
    output = ROOT / 'dist' / f'Tung-Tung-Client-{VERSION}-Voice-Chat-Prism.zip'
    with zipfile.ZipFile(baseline) as source, zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as target:
        manifest = json.loads(source.read('PACK-MANIFEST.json'))
        manifest.update(profile='Tung Tung Client 26.3', desktopVersion=VERSION, gameCoreVersion=VERSION)
        for mod in manifest['mods']:
            if mod['id']=='amethyst_chunk_finder':
                mod.update(path=f'minecraft/mods/tung-tung-bridge-{VERSION}.jar',version=VERSION,bytes=len(core),sha256=hashlib.sha256(core).hexdigest())
            if mod['id'] == 'voicechat':
                mod.update(path='minecraft/mods/' + VOICE, version='2.6.27+26.3', bytes=len(voice), sha256=hashlib.sha256(voice).hexdigest())
        for entry in source.infolist():
            name = entry.filename
            if name.startswith(('minecraft/mods/voicechat-','minecraft/mods/tung-tung-bridge-')):
                continue
            data = source.read(name)
            if name == 'PACK-MANIFEST.json':
                data = (json.dumps(manifest, indent=2) + '\n').encode()
            elif name == 'instance.cfg':
                data = data.decode().replace('name=Tung Tung Client 1.30.3 + Voice Chat', 'name=Tung Tung Client 26.3').encode()
            elif name == 'README.txt':
                text = data.decode().replace('TUNG TUNG CLIENT 1.30.3 +', f'TUNG TUNG CLIENT {VERSION} +').replace('2.6.24', '2.6.27')
                text = text.replace('Exactly four mods', 'Six mods').replace('- ViaFabricPlus 5.1.3\n', '').replace('"Tung Tung Client 1.30.3 + Voice Chat"', '"Tung Tung Client 26.3"')
                text += '\nVoice Chat 2.6.27 starts successfully on Mac. DonutSMP voice negotiation is still unresolved; this package does not claim to fix that connection.\n'
                data = text.encode()
            elif name == 'licenses/SIMPLE-VOICE-CHAT-NOTICE.txt':
                data = (ROOT / 'macos/SIMPLE-VOICE-CHAT-NOTICE.txt').read_bytes()
            elif name.startswith('Instructions/') and (ROOT / 'docs' / Path(name).name).is_file():
                data = (ROOT / 'docs' / Path(name).name).read_bytes()
            target.writestr(name, data)
        target.writestr('minecraft/mods/' + VOICE, voice)
        target.writestr(f'minecraft/mods/tung-tung-bridge-{VERSION}.jar',core)
    with zipfile.ZipFile(output) as archive:
        assert archive.testzip() is None
    with output.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    output.with_suffix('.zip.sha256').write_text(f'{digest}  {output.name}\n')
    print(output)


if __name__ == '__main__':
    main()
