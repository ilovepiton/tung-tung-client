#!/usr/bin/env python3
"""Create an organized, local distribution from already-built release archives."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = '1.30.4'
CORE_VERSION = '1.30.3'
DEST = ROOT / 'dist' / f'Tung-Tung-Client-{VERSION}-Share'


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def main():
    DEST.mkdir(parents=True, exist_ok=True)
    packages = {
        'Mac': f'Tung-Tung-Client-Mac-{VERSION}-Voice-Chat.zip',
        'Windows': f'Tung-Tung-Client-Windows-{VERSION}-Voice-Chat.zip',
        'Prism': f'Tung-Tung-Client-{CORE_VERSION}-Voice-Chat-Prism.zip',
    }
    for folder, name in packages.items():
        target = DEST / folder
        target.mkdir(exist_ok=True)
        source = ROOT / 'dist' / name
        with zipfile.ZipFile(source) as archive:
            if archive.testzip():
                raise ValueError(f'Broken ZIP: {name}')
        shutil.copy2(source, target / name)
    instructions = DEST / 'Instructions'
    instructions.mkdir(exist_ok=True)
    for name in ('EASY-SETUP-RU.md', 'UPDATES.md', 'CHANGELOG.md'):
        shutil.copy2(ROOT / 'docs' / name, instructions / name)
    shutil.copy2(ROOT / 'windows/START-HERE.txt', DEST / 'Windows/START-HERE.txt')
    shutil.copy2(ROOT / 'macos/README.txt', DEST / 'Mac/README.txt')
    shutil.copy2(ROOT / 'LICENSE', DEST / 'LICENSE.txt')
    (DEST / 'README.md').write_text('''# Tung Tung Client 1.30.4

## Начни здесь

1. Выбери папку **Mac** или **Windows** и распакуй ZIP полностью.
2. Следуй [простой инструкции](Instructions/EASY-SETUP-RU.md).
3. После установки ядра перезапусти Minecraft, выбери Tung Tung Client 26.3.
4. Client Maps и Map in Slot: **Texture Packs → Map Art**.

## Что в папках

- **Mac/** — приложение для Apple Silicon и Intel, с Simple Voice Chat.
- **Windows/** — Windows x64, с Simple Voice Chat и инструкцией.
- **Prism/** — профиль для импорта в Prism Launcher вместо официального лаунчера.
- **Instructions/** — установка, журнал изменений и план будущих обновлений.
- **Source/** — исходники и материалы для разработчика.
- **SHA256SUMS** — контрольные суммы всех файлов пакета.
- **release.json** — версия, совместимость и статус проверки платформ.

Mac прошёл native self-test и изолированный запуск Minecraft 26.3.
Windows собран без ошибок компиляции; запуск на реальном Windows ещё не проверен.
Текущий релиз обновляется вручную. Автообновление — будущая задача, без даты.
Для запуска готовых приложений собирать исходники не требуется.
''', encoding='utf-8')
    srcdir = DEST / 'Source'
    srcdir.mkdir(exist_ok=True)
    sourcezip = srcdir / f'Tung-Tung-Source-{VERSION}.zip'
    roots = ['src', 'macos', 'windows', 'desktop', 'roblox', 'chess', 'gradle', 'packaging', 'scripts', 'docs']
    files = [ROOT / name for name in ['README.md', 'LICENSE', 'build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat', '.gitignore']]
    excluded = {'screenshots', '__pycache__', 'node_modules', '.git', '.tools', 'luau-0.744', 'logs', 'test-results'}
    for name in roots:
        files.extend(p for p in (ROOT / name).rglob('*') if p.is_file()
                     and not any(part in excluded for part in p.relative_to(ROOT).parts)
                     and p.name != '.DS_Store' and not p.name.startswith('._')
                     and p.suffix not in {'.pyc', '.log', '.tmp'})
    # Exact redistribution inputs only; no launcher/account/config directories.
    vendor = ['.tools/map-art', '.tools/anti-leak-1.28.0/library']
    for name in vendor:
        files.extend(p for p in (ROOT / name).rglob('*') if p.is_file() and p.name != '.DS_Store')
    for name in ['fabric-api-0.162.0+26.3.jar', 'FABRIC-API-LICENSE.txt',
                 'voicechat-fabric-2.6.24+26.3.jar', 'fabric-profile-26.3.json',
                 'anti-leak-1.28.0/optional-mods.json']:
        files.append(ROOT / '.tools' / name)
    with zipfile.ZipFile(sourcezip, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(set(files)):
            if path.is_file():
                archive.write(path, path.relative_to(ROOT))
    (srcdir / 'BUILD-NOTES.txt').write_text('''SOURCE SNAPSHOT 1.30.4

Includes production sources, UI assets, tests, Gradle wrapper, map-art inputs
and texture library. No accounts, saved worlds, game logs or pairing data.

Core: Java 25, then ./gradlew build. Gradle downloads its declared dependencies.
Mac: Xcode command-line tools, then python3 macos/build.py.
Voice Chat Mac packaging also needs the matching Prism ZIP under dist/.
Windows: python3 windows/build.py uses the local .tools/windows-build SDK
cache (PowerShell, net48 reference assemblies, WebView2 and Newtonsoft).
That machine-specific SDK cache is NOT bundled; provision those dependencies
before rebuilding Windows. Prebuilt Windows EXE and DLLs are in Windows/.
No promise of an offline one-command build on a fresh machine is made.
''')
    manifest = {'version': VERSION, 'gameCoreVersion': CORE_VERSION, 'minecraft': '26.3', 'fabricLoader': '0.19.5',
                'java': 25, 'updateMode': 'manual', 'updateFeed': None,
                'validation': {'mac': 'native self-test and isolated game startup passed',
                               'windows': 'cross-compilation passed; Windows runtime not tested'},
                'packages': packages}
    (DEST / 'release.json').write_text(json.dumps(manifest, indent=2) + '\n')
    sums = DEST / 'SHA256SUMS'
    sums.write_text(''.join(f'{sha256(p)}  {p.relative_to(DEST).as_posix()}\n'
                           for p in sorted(DEST.rglob('*')) if p.is_file() and p != sums))
    output = DEST.with_name(DEST.name + '.zip')
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_STORED) as archive:
        for path in sorted(DEST.rglob('*')):
            if path.is_file():
                archive.write(path, Path(DEST.name) / path.relative_to(DEST))
    output.with_suffix('.zip.sha256').write_text(f'{sha256(output)}  {output.name}\n')
    print(json.dumps({'folder': str(DEST), 'archive': str(output), 'bytes': output.stat().st_size}, indent=2))


if __name__ == '__main__':
    main()
