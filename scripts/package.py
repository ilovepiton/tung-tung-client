"""Bundle the Windows client, pinned Fabric bridge, source, and English instructions."""

from pathlib import Path
import hashlib
import json
import re
import shutil
import zipfile


ROOT = Path(__file__).resolve().parents[1]
VERSION = '1.8.0'
MINECRAFT_VERSION = '26.3'
LOADER_VERSION = '0.19.5'
FABRIC_API_VERSION = '0.162.0+26.3'
DIST = ROOT / 'dist'
BUNDLE = DIST / 'Tung-Tung-Client'

# Observed build and runtime results; packaging refuses unresolved validation markers.
BUILD_INFO = f"""Tung Tung Client {VERSION}
Minecraft Java 26.3 / Fabric Loader 0.19.5 / Fabric API 0.162.0+26.3

Desktop: existing 1.7 Windows EXE, source, assets, layout, navigation and preview files retained.
The EXE previously passed C# 5 / .NET Framework 4.8 Roslyn static compilation; it was not recompiled for this gameplay-only release.
Native Windows execution, installer behavior and focus transfer remain untested here.
Desktop integrity: all 81 desktop file SHA-256 hashes match the pre-upgrade snapshot exactly; no source, EXE, image, audio, preview, installer or layout changes.
Bridge validation: all 59 production Java sources compiled with Java 25 against the complete actual official Minecraft 26.3 / Fabric API classpath; 116 own classes, 11 Mixins. Actual Fabric loading, dirty-block updates and GUI pipelines ran successfully. No mock API classes or QA classes are included in the production JAR.
Unit validation: 72/72 JUnit tests passed, 0 failures. Includes 27 previous/core tests, 7 structure tests, 13 persistence tests and 25 selection/projection/category/current-player tests.
Runtime validation: 56/56 actual Minecraft checks passed across three normal process runs, all exit 0. Verified real storage/spawners/construction, Small-to-Medium observed growth, J/Esc/K/P and scroll/pinning, cache unload/reload, normal shutdown/restart persistence, profile/Overworld/Nether isolation, and current server-owned test-player spawn/movement/removal via normal packets. Stress world: 56 real fixture chunks, nearest 48 rendered; 200 samples each OFF/ON, matched inactive window, FPS medians 117/114, scanner-work ON P95 0.045375 ms. Eleven genuine GPU PNG captures and JSON reports are in gameplay-checks. Temporary game/SDK/assets/QA worlds were removed after validation. Native Windows and public-server behavior were not tested.

Gameplay: J opens Chunk analysis without redesigning the desktop. Existing P/Esc/K controls remain.
Six game controls: Chest chunks, Amethyst chunks, Growth tracking, Structures, Player chunks and Spawner chunks.
Chest Chunks red #FF2D2D; Amethyst purple #B66CFF; Structures amber #FFB020; Players pink #FF55CC; Spawners cyan #35C9FF.
Storage observations include chests/trapped chests, barrels, shulkers, ender chests, hoppers, furnace-family blocks, dispensers and droppers.
Chunk boundaries use exact 16-by-16 X/Z coordinates and the current dimension's vertical bounds, with low-alpha fill, thin edges, restrained glow and eased transitions.
Historical/pending observations are labeled and use subdued dashed geometry. Current players use received positions only and have no stale-position fade or persisted player history.
Default chunk display limit/range: 48 nearest chunks / 128 blocks; configurable 16-64 / 16-256. Frame geometry and fades are bounded; no universal FPS claim is made.
Relevant received block changes invalidate completed scan generations. Unchanged chunks are not repeatedly rescanned on a timer.
The client scans normally received full chunks without requesting additional data. Time-sliced scanning targets a 2 ms client-tick budget; actual runtime work is reported separately.

Persistence: separate SHA-256-named scopes for server address or local world directory, dimension and manual history profile.
An unknown server identity is ephemeral per connection. Same-address resets/backend worlds require a changed profile or explicit scope clear; the client cannot infer them automatically.
Saved block facts have no current/live verification flag or player positions. Current/Verifying/Historical states are assigned from current received data and completed scans.
Background loading and coalesced atomic writes preserve files on failure. Normal shutdown flushes queued writes with a 30-second timeout.
A failed history read protects that source from autosave overwrite until successful reload or explicit scope clear.
Limits: 2048 stored chunks, 4096 positions/chunk, 65536 total stage baselines, 32 recent growth events/chunk; cumulative continuous/gap counts retained.
Small/Medium/Large/Cluster stages are compared at the same previously observed position. A first-seen mature cluster and absent baseline produce no invented event.
A skipped stage is one observed advance, not an invented number of ticks. Unload/restart advances remain labeled observation-gap events.
Growth implies possible simulation only; the local player and server settings may cause it. Authorized server diagnostics are unavailable.
Structure evidence rates observed storage/crafting/redstone and repeated block rows as None/Weak/Moderate/Strong, without asserting who built them.

Existing model highlights retain solid type colors, texture alpha, geometry shading and view-center tracers. Individual chests stay orange; chunk storage categories are red.
The camera-projected through-terrain meshes do not share the exact world depth buffer; complex translucent/custom submissions and affine alpha UVs retain their existing limitations.
X-ray retains the vanilla Fabric terrain renderer, hides common terrain and fluids, and cannot recover withheld/obfuscated blocks or extend render distance.
No Sodium/replacement-renderer adapter or authorized server diagnostics adapter is included.
Existing PvP/camera modules and disabled movement/auction cards remain unchanged.
The desktop's old offline Chrome page previews are not screenshots of this new gameplay release or a running Windows application.
Test-only qa classes, gametest mod, test world, Minecraft installation and compilers are excluded from payload and packaged source.
Distribution payload contains only the production 1.8 bridge, pinned Fabric API and loader profile. SHA-256 manifest and ZIP CRC checks are generated when packaged.
Runtime checks do not establish compatibility on every server, custom renderer or Windows installation, or anti-cheat detection outcomes.
"""

bridge_jar = ROOT / 'build' / 'libs' / f'tung-tung-bridge-{VERSION}.jar'
fabric_jar = ROOT / '.tools' / f'fabric-api-{FABRIC_API_VERSION}.jar'
fabric_profile = ROOT / '.tools' / f'fabric-profile-{MINECRAFT_VERSION}.json'
required_files = [
    ROOT / 'desktop' / 'Tung-Tung-Client.exe',
    ROOT / 'desktop' / 'START-TUNG-TUNG-CLIENT.cmd',
    ROOT / 'desktop' / 'TungTungClient.cs',
    ROOT / 'desktop' / 'Launch.ps1',
    ROOT / 'desktop' / 'DesktopEntry.cs',
    ROOT / 'desktop' / 'Install-Core.ps1',
    bridge_jar,
    fabric_jar,
    fabric_profile,
    ROOT / '.tools' / 'FABRIC-API-LICENSE.txt',
    ROOT / 'README.md',
    ROOT / 'ASSET-PROMPT.txt',
    ROOT / 'VISUAL-ASSETS.txt',
    ROOT / 'desktop' / 'assets' / 'logo.png',
    ROOT / 'desktop' / 'assets' / 'world-mascot.png',
    ROOT / 'desktop' / 'assets' / 'pvp-mascot.png',
    ROOT / 'desktop' / 'assets' / 'findings-mascot.png',
    ROOT / 'desktop' / 'assets' / 'dancer.png',
    ROOT / 'desktop' / 'assets' / 'startup-meow.wav',
    ROOT / 'desktop' / 'assets' / 'ui-click.wav',
    ROOT / 'desktop' / 'assets' / 'items' / 'sources.json',
    ROOT / 'desktop' / 'preview' / 'screenshots' / 'finder-settings.png',
]
finder_keys = ('chest', 'spawner', 'barrel', 'shulker', 'ender_chest', 'hopper',
               'furnace', 'crafting', 'enchanting', 'anvil', 'beacon', 'armor_stand')
required_files.extend(ROOT / 'desktop' / 'assets' / 'items' / f'finder_{key}.png' for key in finder_keys)
ui_source = ROOT / 'desktop' / 'TungTungClient.cs'
if ui_source.is_file():
    module_ids = sorted(set(re.findall(
        r'^\s*Add\("([a-z0-9_]+)",\s*"[^"]+",\s*"[^"]+",\s*(?:true|false),',
        ui_source.read_text(encoding='utf-8-sig'), re.MULTILINE,
    )))
    if not module_ids or 'xray' not in module_ids:
        raise ValueError('The desktop module catalog must include the X-ray module for this release.')
    required_files.extend(ROOT / 'desktop' / 'assets' / 'items' / f'{module_id}.png' for module_id in module_ids)
for required in required_files:
    if not required.is_file():
        raise FileNotFoundError(f'Build input is missing: {required}')

validation_markers = ('DESKTOP_INTEGRITY_PENDING:', 'BRIDGE_VALIDATION_PENDING:', 'CORE_TESTS_PENDING:', 'RUNTIME_QA_PENDING:')
if any(marker in BUILD_INFO or marker in (ROOT / 'README.md').read_text(encoding='utf-8') for marker in validation_markers):
    raise ValueError('Finalize the explicit validation results in README.md and package.py BUILD_INFO before packaging.')
with zipfile.ZipFile(bridge_jar) as production_bridge:
    metadata = json.loads(production_bridge.read('fabric.mod.json'))
    if metadata.get('id') != 'amethyst_chunk_finder' or metadata.get('version') != VERSION:
        raise ValueError('The production bridge identity/version does not match this release.')
    if any(name.startswith('qa/') or name.startswith('dev/amethyst/finder/FinderClientTest')
           for name in production_bridge.namelist()):
        raise ValueError('Test-only classes must not be included in the production bridge.')

DIST.mkdir(exist_ok=True)
if BUNDLE.exists():
    shutil.rmtree(BUNDLE)
BUNDLE.mkdir()

shutil.copytree(
    ROOT / 'desktop',
    BUNDLE / 'desktop',
    ignore=shutil.ignore_patterns('*.exe', '*.cmd', 'tun-tun.png', '__pycache__', '*.tmp'),
)
shutil.copy2(ROOT / 'desktop' / 'Tung-Tung-Client.exe', BUNDLE / 'Tung-Tung-Client.exe')
# Windows PowerShell 5.1 recognizes UTF-8 reliably when scripts have a BOM.
for script in (BUNDLE / 'desktop').glob('*.ps1'):
    script.write_text(script.read_text(encoding='utf-8-sig'), encoding='utf-8-sig')
(BUNDLE / 'START-TUNG-TUNG-CLIENT.cmd').write_bytes(
    (ROOT / 'desktop' / 'START-TUNG-TUNG-CLIENT.cmd').read_text().replace('\n', '\r\n').encode('ascii')
)

payload = BUNDLE / 'payload'
mods = payload / 'mods'
mods.mkdir(parents=True)
shutil.copy2(bridge_jar, mods)
shutil.copy2(fabric_jar, mods)
shutil.copy2(fabric_profile, payload / 'fabric-profile.json')

source = BUNDLE / 'source'
source.mkdir()
for name in ('src', 'gradle', 'scripts'):
    shutil.copytree(ROOT / name, source / name, ignore=shutil.ignore_patterns('gametest', 'testmod', 'runtime-qa', '__pycache__', '*.tmp'))
for name in ('README.md', 'LICENSE', 'settings.gradle', 'build.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat', '.gitignore'):
    shutil.copy2(ROOT / name, source / name)
for name in ('README.md', 'ASSET-PROMPT.txt', 'VISUAL-ASSETS.txt'):
    shutil.copy2(ROOT / name, BUNDLE / name)
shutil.copy2(ROOT / 'LICENSE', BUNDLE / 'LICENSE.txt')
shutil.copy2(ROOT / '.tools' / 'FABRIC-API-LICENSE.txt', BUNDLE / 'FABRIC-API-LICENSE.txt')

# Genuine gameplay evidence, separate from the frozen desktop previews.
evidence = ROOT / 'artifacts' / 'gameplay-1.8'
if evidence.is_dir():
    shutil.copytree(evidence, BUNDLE / 'gameplay-checks',
                    ignore=shutil.ignore_patterns('*.log', 'phase1-ready.json'))


(BUNDLE / 'START-HERE.txt').write_text(
    f"""Tung Tung Client {VERSION} / WINDOWS

1. Extract the entire ZIP.
2. Open Tung-Tung-Client.exe. Keep desktop and payload beside the EXE.
3. Close Minecraft and its Launcher, open Settings, then click INSTALL FABRIC CORE.
4. Open Minecraft Launcher and choose Tung Tung Client 26.3.
5. P opens the desktop client; P or Esc in its window returns to Minecraft.
6. K toggles Chunk Finder. J opens the new in-game Chunk analysis panel; J/Esc returns to gameplay.

The existing desktop application and previews are unchanged from 1.7.
In J, use Chest chunks, Amethyst chunks, Growth tracking, Structures, Player chunks and Spawner chunks.
Chest/Amethyst/Growth initially follow K until explicitly changed in this game panel.
Chunk boundaries span the actual received world's height at exact 16-by-16 coordinates.
Chest Chunks: red #FF2D2D, counting all supported storage including dispensers/droppers.
Amethyst Chunks: purple #B66CFF, with distinct recorded growth indication.
Structures: amber #FFB020, with weak/moderate/strong observed construction evidence.
Player Chunks: pink #FF55CC, current received players only; old player positions are removed.
Spawner Chunks: cyan #35C9FF, actual standard/trial spawners, independent of mob type.
Individual orange chest models and other object colors are separate from the chunk-category colors.
Tracers from view center and the existing object highlights, X-ray, Freecam/Freelook and PvP modules remain.

Aim at a highlighted chunk for its compact HUD; J shows counts, status, timestamps and growth stages.
Select a row to pin its details; Auto select restores crosshair selection.
CURRENT/LIVE means a completed scan of a received chunk.
VERIFYING means previously observed facts awaiting a fresh scan.
HISTORICAL means saved facts, not a current verification.
History outlines are off by default; J can enable their subdued dashed borders within visualization range.
Default nearest chunk limit: 48; default chunk range: 128 blocks. JSON settings allow 16-64 / 16-256.

Block observations persist across unloads and game restarts, separated by server/world, dimension and profile.
Saved history is in config/tun-tun-history inside the isolated game profile.
Growth records only same-position advances: Small -> Medium -> Large -> Cluster.
First-seen mature clusters do not establish growth.
Continuous advances and advances across unload/restart observation gaps are labeled separately.
Your own nearby player or server settings can cause random ticking; growth does not prove another player.
Authorized server diagnostics are UNAVAILABLE; no verified remote-activity result is invented.
Use History profile / Apply profile in J when a server resets or changes backend worlds at the same address.
An unavailable server identity uses an ephemeral connection scope and cannot be reliably matched after reconnecting.
Clear scope history requires a second click and affects only the current world/server, dimension and profile.
A failed history read protects that source file from autosaves until explicit clear or successful reload.
History limits: 2048 chunks, 4096 bud positions/chunk, 65536 total baselines, 32 recent events/chunk.
Older baselines may be evicted; missing baselines cannot invent growth.

The first game launch needs internet access. Minecraft and Java are not included.
Received data only: scanning, ESP and X-ray do not load distant chunks or recover server-hidden blocks.
A natural chest, spawner or repeated structure is not proof of a player base, raid history or container contents.
Native Windows execution/focus behavior is untested here; use Alt+Tab or the tray if focus does not transfer.
Exit is in the tray menu. Disabled desktop cards remain unavailable.
The Donut SMP label and donut are cosmetic; anti-cheat detection outcomes have not been measured.
The temporary runtime QA mod, test world, compiler and game installation are not included.

See README.md for usage details and BUILD-INFO.txt for the final observed validation results.
""",
    encoding='utf-8-sig',
)
(BUNDLE / 'BUILD-INFO.txt').write_text(BUILD_INFO, encoding='utf-8')

manifest = {
    'name': 'Tung Tung Client',
    'version': VERSION,
    'minecraft': MINECRAFT_VERSION,
    'loader': LOADER_VERSION,
    'files': [],
}
for asset in sorted(payload.rglob('*')):
    if asset.is_file():
        manifest['files'].append({
            'path': asset.relative_to(BUNDLE).as_posix(),
            'kind': 'mod' if asset.suffix == '.jar' else 'profile',
            'sha256': hashlib.sha256(asset.read_bytes()).hexdigest(),
        })
(BUNDLE / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')

archive = DIST / f'Tung-Tung-Client-Windows-{MINECRAFT_VERSION}.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as zipped:
    for asset in sorted(BUNDLE.rglob('*')):
        if asset.is_file():
            zipped.write(asset, asset.relative_to(DIST))
with zipfile.ZipFile(archive) as zipped:
    assert zipped.testzip() is None
(DIST / (archive.name + '.sha256')).write_text(
    hashlib.sha256(archive.read_bytes()).hexdigest() + '  ' + archive.name + '\n', encoding='utf-8'
)
print(f'{archive}\n{archive.stat().st_size / 1024**2:.2f} MiB')
