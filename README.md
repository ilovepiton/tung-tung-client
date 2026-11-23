## BetterMaceSwap — 1.36.0

Original BetterMaceSwap 5.0.0 for Minecraft 26.3 is bundled in Mac and Windows downloads. **Arsenal** has separate switches, native settings, keybinds, three Gear Up kits and an Elytra Boost action. Aim Assist, TriggerBot and Shield Breaker now use the original mod; Shield Breaker is named **Auto Stun Slam**. See [setup and behavior](docs/BETTER-MACE.md).

# Tung Tung Client

Current desktop: **1.35.4** · Minecraft core: **1.35.4** · Minecraft Java **26.3**.

1.30.3 adds 20 original keyboard illustrations, 43 distinct Minecraft PNG item icons, large module
description dialogs and Find Player skin faces. Chess.com, Roblox and Stream
are visible but locked. Player faces require the 1.30.3 game core.

## Auto Shield Breaker — 1.35.4

In Arsenal, hold a mace and right-click a blocking crosshair opponent once. One hotbar axe attack, immediate return to the mace, then stop. No automatic mace attack and no axe selection for non-blocking targets. [Instructions](docs/SHIELD-BREAKER.md).

UPDATE in the header or on module cards checks GitHub Releases and updates the whole package after Minecraft and Launcher are closed. Archives are checksum-verified; replaced app files are backed up. Future-release self-update and Windows runtime remain unverified.

## Auto Builder — 1.35.0

Open **World Tools → Auto Builder**. Import a downloaded `.schem` (Sponge 1–3),
`.litematic` (4–7), or vanilla structure `.nbt`. The orbitable 3D preview shows
block colors, dimensions and a material checklist; large previews are limited
to 2,000 blocks. Move the origin, rotate by 90°, choose a layer, then **Apply
Preview**. **Build myself** supplies in-game outlines without taking actions;
**Automatic → Start Building** uses the connected character’s inventory and
walks over received ground to place blocks with normal reach and line of sight.

**Mine Area** takes two corners from your position or exact X/Y/Z coordinates.
Preview the bounds, then press **Start Mining**. It works from the top down,
selects a hotbar tool and preserves blocks outside the selection. Containers,
fluids, unbreakable blocks, nearly broken tools and the block under your feet
pause the task. Pause, menus, manual controls, low health and changing worlds
stop or suspend automatic actions. Block actions wait for server receipts.

**Connect Bot…** chooses another running Minecraft instance with this same
core installed and a bot account already logged in through its launcher. No
account credentials are requested or stored by the client. It controls that
instance’s character; it does not spawn a player on a server. Select **Background
bot instance**, apply the preview, start, and return to that Minecraft instance
once to close its menu. It can then work while another app is active. The prior
pause-on-lost-focus preference is restored when the task stops.

Use existing access or add scaffolding for higher floors. Unsupported block
placements need manual help; this version does not copy entities, chest
contents or other block-entity data. Legacy numeric MCEdit `.schematic` files
show a conversion message instead of guessing modern block IDs. Limits:
16 MB compressed, 64 MB decoded NBT, 200,000 non-air blocks / mining cells,
1,000,000 voxels, 512 blocks per side. No live Donut SMP construction was run.

Verification: `python3 scripts/verify-builder.py --game` runs parser checks and
real Survival building, mining, movement, material and container checks in a
new offline world under `build/builder-qa`, never in a user save. Format
references: [Sponge specification](https://github.com/SpongePowered/Schematic-Specification)
and [Litemapy format implementation](https://github.com/SmylerMC/litemapy).

## Downloads and installation

Download website: https://ilovepiton.github.io/tung-tung-client/


The shareable distribution uses separate `Mac/`, `Windows/`, `Prism/`,
`Instructions/` and `Source/` folders. Choose the archive for your OS, extract
it completely, install the bundled Fabric core and restart Minecraft.

- [Простая инструкция на русском](docs/EASY-SETUP-RU.md)
- [Update steps and future plans](docs/UPDATES.md)
- [Release changelog and verification scope](docs/CHANGELOG.md)
- [Mac details](macos/README.txt)
- [Windows quick start](windows/START-HERE.txt)

Client Maps and Map in Slot are in **Texture Packs → Map Art**. Both default to
enabled in the game core and support separate switches/keybinds. Client Maps
saves received map pixels per server; Map in Slot previews them inside item
slots. The latter includes a small MIT-licensed runtime-toggle adaptation.

If the app still says **1.29.7**, an older copy is open. Replace the installed
app, then update the game core too. `CORE DISCONNECTED` means the desktop app
has not connected to the running Fabric game profile.

## Release status

Mac has passed the native self-test and isolated Minecraft 26.3 startup.
Windows is cross-compiled on Mac and needs runtime validation on Windows.
Automatic future updates are planned; this release uses manual installation.
Public repository: https://github.com/ilovepiton/tung-tung-client
Downloads: https://github.com/ilovepiton/tung-tung-client/releases/latest
The curated publication checkout is `build/public-release-1.30.1`; the main
development tree retains its existing uncommitted changes.

## Earlier development notes

The sections below record previous versions and their historical test state;
their installation status does not describe the current 1.30.1 package.

## Chess desktop 1.29.7

Chess.com now has an Auto Next Game workspace with common and custom time
controls, a cancellable post-game pause, a separate board window, Chrome
extension connection and an adapter for the installed iPhone Chess app on Mac.
The desktop update preserves stable Minecraft core 1.29.5. See
[Chess setup and test scope](chess/README.md) and
[the current handoff](HANDOFF-CHESS-2026-11-03.md).
Chrome extension installation and native Accessibility access are separate
connection steps. No live games were started during validation.

## In progress: 1.29.6 Auto Totem timing fix

The user reported failures with spare totems in ordinary inventory slots.
The candidate now requests a refill on the player's actual totem activation,
before a delayed empty-hand inventory update, and tracks spent sources so late
updates cannot reverse a refill. Auto Totem settings also show spare count,
server wait and a bounded last-death diagnostic.

Core compilation, 20 headless packet-order checks, 29 UI fixture checks and
Windows source compilation passed. The updated real-game QA suite compiled,
including 400 ms delayed inventory echoes, main-hand activation and rapid pops.
It has **not run in Minecraft yet**. The user deferred closing the running game;
the official game profile/core remains on 1.29.5 (Chess desktop is 1.29.7). Candidate
files are staged separately under `dist/Totem-1.29.6/`.

Run without Minecraft: `python3 scripts/verify-totem-headless.py` and
`node scripts/verify-loot-ui.mjs --version 1.29.6`.
See the current section of `HANDOFF-2026-10-30.md` for remaining gameplay checks
and installation steps. Existing release audit/installer scripts still target
the verified 1.29.5 release and must be updated after candidate gameplay passes.

## Release 1.29.5 — Auto Totem, Invis Mode and anchor placement

Auto Totem always equips a totem whenever the offhand has none, regardless of
health or absorption. Enabling it acts immediately; received inventory updates
and the start of each client tick also refill it. It prefers main-inventory
slots, preserves displaced items and the selected weapon, and supports player
slots in ordinary open containers without changing chest contents or the cursor.
Anchor/Mace transactions reserve only their affected slots instead of blocking
all inventory access. Server-rejected swaps retry on a later tick without a
fixed four-tick delay after genuine consumption.

Invis Mode in Texture Packs → Map Art reveals the avatars of invisible players
already received by the client. A red **Invisible** header appears above their
Player Info; it also works as a compact tag when Player Info is off. The module
changes render state only, preserving actual invisibility, armor and held items.
Disabling it immediately restores vanilla rendering and clears the header.

Auto Totem and Kill Aura are on the first Arsenal row. Kill Aura attacks nearby
eligible players/mobs in any direction while the player's body may stand still;
normal weapon reach, line of sight, friend filters and server cooldowns apply.
Its own settings expose target types, range and attack timing. Other combat
helpers may temporarily own attacks, in which case its live status says so.

Loot Protection now permits harmless anchor placement and glowstone charging
in both hands, including with Auto Anchor enabled. It still blocks anchor
explosions and crystal use without nearby opponents. Safe building does not
start or latch an automated Anchor combo. This fixes the overbroad 1.29.4 check
that rejected every click while holding a respawn anchor.

Verified in an isolated real game: 40 utility checks, 105 loot/anchor checks,
and all 35 existing combat checks (zero skips), plus 26 desktop UI checks and
76 release checks. Run `python3 scripts/verify-loot.py --suite utilities`,
`python3 scripts/verify-loot.py`, `node scripts/verify-loot-ui.mjs` and
`python3 scripts/audit-combat-utilities.py` against the frozen 1.29.5 core.

Installed on 2026-11-02 in both desktop app copies and the `tun-tun-26.3`
profile, with post-install version/signature/checksum verification. Evidence
and rollback path: `artifacts/loot-1.29.5/installation.json` and
`artifacts/loot-1.29.5/post-install-verification.json`.

## Release 1.29.4 — Loot Protection and Item Finder

Loot Protection defaults to enabled. When no living non-friend opponent is
received within 32 blocks, manual and automated crystal placement/attacks and
explosive anchor use are blocked before prediction and outgoing interaction
packets. A received death event stops follow-up blasts immediately; a totem pop
does not. New nearby opponents resume normal combat. Configure the switch and
8–128 block opponent radius in Arsenal → PvP settings → right-click assist.
Normal block interactions and Nether anchor respawn-point use remain available.
This controls this client's crystal/anchor interactions, not external damage.

Item Finder in World Tools selects 50 useful dropped item types, including
totems, crystals, anchors, glowstone, diamond/netherite gear, Elytra and rare
materials. All colored shulker boxes share a filter. Live labels show names,
stack counts and distance; gold outlines mark their current chunks. Pickups,
despawns, deselection and world changes clear those markers. It scans received
item entities, without opening containers or requesting unloaded terrain.
Item prices depend on the server.

Verification: `python3 scripts/verify-loot.py` exercises actual game entities,
production mixins and outgoing packets in an isolated offline world;
`node scripts/verify-loot-ui.mjs` exercises the production desktop controls.
These use the existing local dependency cache.

Release verification passed: 85 actual-game loot/guard checks, 35 existing
Crystal/Anchor regression checks with zero skips, and 17 desktop UI checks.
The tested core SHA-256 is
`3c39d0fe51c324118d8342a73970ad24d64a7a85bf2e5e4d6d7a48c805befd6e`.
The signed Mac app is staged separately at
`dist/Loot-1.29.4/Tung Tung Client.app`; `scripts/install-loot.py --apply`
requires normal game/Launcher/client shutdown and retains previous app/core
contents for rollback. It installs from local verified files.
Installed on 2026-10-31 in both desktop app copies and the `tun-tun-26.3`
profile; post-install versions, signatures and core checksums passed.
See `artifacts/loot-1.29.4/installation.json` for the rollback backup path.

## Release 1.29.3 — Night Vision

Night Vision in Texture Packs → Map Art makes unlit caves and nights fully bright
and suppresses Darkness lightmap dimming. Terrain remains visible. The switch
uses the normal module controls and supports a custom keybind. Disabling it
refreshes vanilla lighting; gamma settings and actual potion effects are left
untouched. X-ray retains its own full-bright lighting when enabled separately.

Verified in a disposable real Minecraft 26.3 client: 19 checks, including GPU
captures of a sealed unlit cave, immediate restoration between ticks, real
potion preservation, Darkness dimming, X-ray interaction and disconnect cleanup.
Ten desktop UI checks cover command acknowledgement and older-core handling.
Run `python3 scripts/verify-night-vision.py` and
`node scripts/verify-night-vision-ui.mjs` with the existing local dependency cache.
The separate full-installer self-test encountered an existing external optional
cosmetic download failure; the dedicated Night Vision updater installs the
verified core and app copies from local files without that download.

## Release 1.29.2 — Auto Anchor hand restoration

Auto Anchor keeps borrowed inventory glowstone out of the original anchor slot,
including when every hotbar slot is occupied. After a confirmed detonation, it
selects an actual remaining hotbar anchor before waiting for inventory cleanup.
Continuing an already placed anchor with glowstone also returns to an anchor.
Manual slot changes retain priority, and an empty anchor stack is not fabricated.

Auto Crystal's existing Fast Assist path already has zero additional stage-delay
ticks and responds to received spawn/removal packets. Its code, retries, settings
and server acknowledgements are unchanged. There is no claimed universal 10×
speedup. All other game-core archive entries are byte-identical to Core 1.28.0,
apart from mod-version metadata and the focused ManualCombatAssist changes.
The future separate Roblox application name is **TungTung Roblox**; this update
does not create that application or add stock Roblox execution support.

## Release 1.29.1 — Sidebar branding

Roblox uses its recognizable tilted-square logo. Roblox and Stream no longer
show secondary labels on the right. The Stream selector groups transparent PNG
marks for Twitch, YouTube, TikTok and Kick. NoClip is the first module and the
initial details selection in the Roblox workspace.

This is a desktop presentation update. The runtime and native game connection
are unchanged: loading NoClip into stock Roblox Player or arbitrary third-party
games is still not implemented. No separate modified Roblox Player or alternate
login client is included. Minecraft Core remains 1.28.0.

## Release 1.29.0 — Roblox workspace

The existing desktop client now includes a separate Roblox lobby, 24-module
catalog, visual presets, players and connection setup with original transparent
PNG artwork. Minecraft, Chess.com and Stream retain their existing integrations.
The desktop release keeps Minecraft Core 1.28.0 and Simple Voice Chat unchanged.

`roblox/dist/TungTungRobloxRuntime.luau` implements 22 local/self tools using
standard Roblox APIs. Friend Fling and Respawn require a trusted game adapter.
Native Mac/Windows controls use separate authenticated loopback sessions,
acknowledgements and heartbeat expiry. Controls stay disabled without a runtime;
being on a private server does not provide an execution environment.

This release does not include a way to inject or load the module into stock
Roblox Player or arbitrary third-party games. A compatible in-game Luau host and
transport adapter are still required. It cannot be described as working cheats
for any private server. Camera/lighting are local; the game server can correct
character movement. See roblox/README.md and roblox/HOST.md for the exact boundary.

Verification includes official Luau compilation and mocked lifecycle checks,
production native transport/validation checks, and desktop UI checks. These
checks do not establish live Roblox compatibility, physics or visual quality.

## Release 1.27.0

Seven Stars texture collection for Minecraft 26.3: Cold White, ZansWhite V1,
MonoChrome, Monster White, Black Knight, LUMEXS Tung Tung and ZansBlack.
These are third-party packs with original credits, not original TUNG textures.
TUNG artwork is navigation branding. Exactly one managed main pack is selected;
older managed add-ons that replace the incoming pack's files are disabled.
Explicitly enabling an add-on later intentionally overlays the main pack.
Unrelated user/server packs and personal/totem priority remain intact.

ZansWhiteCustom was rejected after its converted animation failed a real
resource reload. Old/invalid saved pack IDs now fall back safely at startup.
All former mouse samples are replaced by seven CC0 physical recordings:
Orochi V2 left/right, a middle button, NEC ECM-S5002, Raspberry Pi, Studio Mouse,
and DeathAdder Essential. Each physical press plays once. Per-button volume,
local preview and voice-chat mute/PTT rules remain unchanged. Removed mouse IDs
fall back to the new base click, without resetting unrelated volume choices.
See the bundled MOUSE-SOUNDS notice and .tools/seven-stars-1.27.0/audio-manifest.json.

The installer pins Sodium 0.9.2, Lithium 0.26.2, FerriteCore 9.0.0,
ImmediatelyFast 1.17.1, Entity Culling 1.11.3, Dynamic FPS 3.11.10 and Krypton
0.3.2 to official Minecraft 26.3 Fabric downloads, verified with SHA512.
Dynamic FPS saves resources while the game is in the background; it is not a
foreground FPS multiplier. No guaranteed 2x FPS or removal of network lag.
No combat, movement, camera, world or microphone-runtime code changed.

Personal-only texture packs download directly from their publisher during
installation, rather than being redistributed in portable bundles. Their
original archives are not rewritten. The installed game must restart to load
new core/audio/mod files. Never replace a running profile through the installer.

## Release 1.26.0

Maximum FPS replaces the old Smooth PvP preset in the same panel. It removes
the frame cap and disables VSync, without reducing render/simulation distance,
clouds, particles or entity distance. Restore Limits returns the saved frame
limit and VSync; applying twice does not overwrite that backup. Disabling VSync
can permit tearing, and uncapped rendering can use more power.

Resource-highlight selection and block-marker ordering reuse exact matching
inputs instead of filtering/sorting the same data on every rendered frame.
Movement, scanner revisions, mineral/range/limit changes, chunk lifecycle and
neighbor occlusion changes invalidate the relevant result. Geometry, marker
budgets, colors, camera interpolation and live block validation are retained.

White Glint is a separate, initially disabled add-on in Texture Packs. It adds
a restrained white sweeping shine to enchanted equipment using Minecraft's
normal moving glint, replacing only the two glint textures. Existing resource
packs, audio and native microphone binaries are preserved.

The installer already supplies official pinned Sodium and Lithium downloads.
Lunar's public articles describe Sodium plus its own rendering optimizations;
no Lunar code or binaries were copied. Sources:
https://www.lunarclient.com/news/sodium-and-iris
https://support.lunarclient.com/solutions/how-to-fix-fps-issues-in-lunar-client

Evidence is in artifacts/game-effects-1.26.0 and artifacts/desktop-1.26.0.
The repeated-selection microbenchmark is not an overall FPS multiplier.
Hardware, scene, existing mods and the GPU determine the actual gain; neither
double FPS nor zero network/server lag is promised. Windows is compiled and
packaged on Mac, not executed in a native Windows window.

## Release 1.21.1

The client no longer replaces every totem item model with a generated vanilla model. Existing selections drop the obsolete TUNG size overlay and keep the selected pack's transforms, including TUNG Black's small 0.3 pop animation and original 0.4 first-person transform. Resource-pack ZIPs and all bundled game artwork/audio remain byte-identical to 1.21.0.

Shared keyboard and mouse recordings have their own output level, independent of local playback volume. The voice mixer trims leading silence and normalizes recorded levels with peak headroom; per-key/per-button volume, share off, microphone mute and push-to-talk still apply. Microphone shows a separate shared-input meter. Actual Voice Chat event dispatch and native Opus encode/decode are checked in a fake-account world, without microphone capture or sending audio to other players. Remote listener audibility requires an in-game check.

## Release 1.21.0

Texture Pack Collection restores the original TUNG Black, White, Purple, Classic and Error character badges. Pixel Perfection Legacy, Pixel Perfection Fidelity, Faithless PvP Edit and Pastelcraft receive matching character variants. The original creator icons return to Add-ons. Preview changes also appear in Minecraft's resource-pack list; existing gameplay textures are preserved.

Five downloaded totems join the collection: Tux Penguin, Duck Totem, Hamster of Undying, Furry Fox and Blinking Totem. Five recorded kbsim switch sets (Durock Alpaca, Alps SKCM Blue, Gateron Ink Black, Gateron Ink Red and Kailh Box Navy) and five recorded Kenney clicks bring each input sound collection to twenty. Space, Enter and Backspace use their dedicated recordings. All choices support existing per-key/per-button customization and the Simple Voice Chat sound mix.

Sources, licenses, hashes and image prompts are recorded in `.tools/collection-1.21.0/`. Test evidence is in `artifacts/game-effects-1.21.0/` and `artifacts/desktop-1.21.0/`. The combat implementation, previous recordings and frozen macOS microphone runtime are preserved. Native Windows window execution still requires a Windows machine; packaged UI, installer and rollback are checked with isolated fixtures on Mac.

## Release 1.20.0

Input studio adds fifteen recorded mouse clicks, fifteen recorded keyboards, five independently mapped mouse buttons and per-key / per-button volume. Sound previews stay local; physical presses play once and held keys/buttons do not repeat. Quiet, Balanced and Crisp presets preserve individual mappings. Mute input sounds leaves the microphone alone.

The optional Simple Voice Chat plugin mixes the selected recordings through the public MergeClientSoundEvent hook and applies optional 80 Hz high-pass speech filtering, gentle compression and a peak limiter before encoding. Microphone mute, disabled/disconnected Voice Chat and push-to-talk release discard queued input audio. No typed text is sent. Voice gain, shared sound level and an outgoing level meter are in Microphone. Hardware, acoustics and the server codec still determine final voice quality; no remote listener test was performed.

Eight original compact totems join the existing collection. A separate model-only overlay keeps all totems at vanilla item proportions while retaining the chosen appearance and sounds, including the separately installed Dr Donut Meow pack. Existing creator texture contents are unchanged; pack preview logos use new TUNG terrain branding. Find Player uses a new transparent PNG. Custom Sky uses the same neutral panel surface as other tools and a landscape PNG preview.

Combat Bot now routes down up to six clear blocks when health permits, gives height differences priority over strafing, resets navigation after observed target teleportation and can place an anchor against an accessible side face. In an isolated actual game it descended a four-block platform and damaged the lower target with an anchor. Server-authorized flight, normal reach, pearl cooldown and damage immunity still apply; this release does not grant arbitrary teleports or remove server attack timing.

Validation is in artifacts/game-effects-1.20.0 and artifacts/desktop-1.20.0. All runtime profiles use fake accounts and separate worlds. The macOS microphone host/shim remain byte-identical to the previously authorized runtime. Windows is compiled and its packaged UI and installer are checked on Mac; a native Windows window has not been run.

# Mineral picker — 1.12.0

Resource highlights now has twelve independent mineral selections: diamond, emerald, lapis, iron, gold, copper, redstone, coal, Nether quartz, ancient debris, amethyst and sulfur. The desktop picker offers All, None and Only per mineral. Press O for the corresponding in-game grid. Normal and deepslate variants share their mineral; Nether gold follows Gold. Amethyst includes buds, clusters and budding source blocks, while sulfur includes natural sulfur, potent sulfur and spikes. Lava remains independent.

Selection changes update the actual scanner, immediately remove disabled markers, invalidate the scan cache and persist through restart. Existing Ore Highlight settings retain their selection. Each mineral has its own tint and bounded per-chunk budget, and scans still use received chunks only. The existing Mac microphone runtime and official Simple Voice Chat mod are retained.

Validation: `artifacts/desktop-1.12.0/` and `artifacts/game-effects-1.12.0/`. Package: `dist/Tung-Tung-Client-Mac-1.12.0-Voice-Chat.zip`.

# Mac microphone fix — desktop 1.11.1

The client now includes a native **Microphone** setup page and a microphone-enabled Java handoff for its existing official Launcher profile. The Launcher retains authentication; the bundled `Tung Tung Minecraft.app` starts the official Java runtime through LaunchServices and requests normal AVFoundation microphone permission. No vendor app, voice mod, permission database, account file or server is patched. The game core remains 1.11.0.

Use Microphone → Apply microphone fix with Minecraft Launcher closed, then relaunch Tung Tung Client 26.3 and allow the Mac prompt. The original profile JSON is backed up under `~/Library/Application Support/Tung Tung Client/voice-backups/`. The runtime passes Launcher arguments through a private 0600 request that is deleted before Java starts; stdout/stderr use private FIFOs. Exit status and cancellation propagate back to Launcher.

Build: `python3 macos/build.py --voice-chat`. Evidence: `artifacts/desktop-1.11.1/`.

# Tung Tung Client

## Mac release 1.11.0

Find Player adds a searchable server player list to the Mac client and to the in-game discovery panel (O). Click an observable player, or use `/findplayer <name>`, to save a fixed XYZ waypoint. `/findplayer` opens the panel and `/findplayer clear` removes the saved marker. The position is captured once, saved atomically and scoped to the actual world/server, dimension and history profile. Moving, disconnecting or reloading the source player does not move it. Tab-list names outside the received client world have no location and cannot be pinned.

Resource highlights have independent Diamonds, Other ores and Lava switches, a 16–128 block range and a 32–192 marker budget. Other ores include emerald, gold, iron, copper, redstone, lapis, coal, quartz and ancient debris. Ore models retain their received geometry; lava uses its received fluid height. Incremental palette-filtered scans read only already loaded chunks. Server-hidden or substituted block data cannot be reconstructed.

The official Minecraft Launcher remains the normal Launch Minecraft route. The combined package retains official Simple Voice Chat 2.6.24+26.3. Its macOS microphone limitation with the official launcher remains: use the dedicated Mac microphone setup / supported Prism launch path and grant microphone access when prompted. A client visual update does not grant OS microphone permission.

Artifacts: `dist/Tung-Tung-Client-Mac-1.11.0-Voice-Chat.zip` and `dist/Tung-Tung-Client-1.11.0-Voice-Chat-Prism.zip`. Validation: `artifacts/game-effects-1.11.0/` and `artifacts/desktop-1.11.0/`. Previous releases are preserved below.

## Mac release 1.10.2

Movement cards distinguish active actions from waiting conditions, paused controls and unavailable server permissions. Boat descent uses the configured Sprint key (Ctrl by default), because Shift dismounts the boat. Freelook permits body controls; detached Freecam pauses them. Fast Swim retains stronger momentum and yields to item use and Riptide. Super Trident respects manual item input and owned inventory actions.

Flight and NoClip still require the relevant server-granted abilities; Boat Fly requires the integrated singleplayer server. These controls do not provide a general public-server bypass. See `artifacts/game-effects-1.10.2/` and `artifacts/desktop-1.10.2/` for focused validation.

## Mac release 1.10.1

The player overlay now uses compact two-row tags with a red heart, red health, gold absorption and a vanilla Totem icon with yellow observed counts. Orange direction arrows retain the players’ original armor colors. **PvP → Player Controls** changes the indicator color, received-player visual range (16–1024), tag size (0.45–1.0×, default 0.70×), and Aim Cursor viewing range (3–256 blocks, default 64). Aim viewing is independent of normal attack reach.

The universal macOS app and official Simple Voice Chat combination are in `dist/Tung-Tung-Client-Mac-1.10.1-Voice-Chat.zip`; the combined Prism profile is `dist/Tung-Tung-Client-1.10.1-Voice-Chat-Prism.zip`. Build and visual evidence is in `artifacts/game-effects-1.10.1/` and `artifacts/desktop-1.10.1/`. Previous release evidence remains preserved below.

## Mac release 1.10.0

The universal macOS 13+ release is `dist/Tung-Tung-Client-Mac-1.10.0.zip`; `dist/Tung-Tung-Client-Mac-1.10.0-Voice-Chat.zip` additionally contains official Simple Voice Chat 2.6.24+26.3 (upstream beta) and a combined Prism profile. Minecraft Java 26.3, Fabric Loader 0.19.5 and Fabric API 0.162.0+26.3 remain pinned. See [Mac controls](macos/README.txt) and [voice setup](macos/VOICE-CHAT-SETUP.txt).

The app is now an ordinary persistent window. P and Escape do not open, hide, resume or close it, and the game no longer registers a P desktop binding. Explicit Hide, Return to Minecraft and Quit controls remain. The sidebar selects Minecraft; Chess.com is marked Coming later without an implemented chess feature.

Aim Cursor on Player, Auto Punch and Auto Kill Steal use real normal aim/attack actions, shared targeting/friends, visibility, reach and cooldown. Auto Totem uses the vanilla predicted SWAP with the normal changed-slot hashes. Received inventory updates can refill the offhand immediately; optional matching slot echoes are not required and do not lock combat. Anchor reserves its inventory transaction so Totem cannot steal a temporary offhand slot; Glowstone right-clicks can continue a previously placed anchor, and missing cover/items produce actionable status. Server-denied item consumption can reconcile before owned-inventory restoration.

Player Direction shows cyan cursor-to-player arrows/tracers and edge indicators without filling the player or armor model. Player Info displays received hearts and absorption, observed totem uses and visible totems in the player's hands. Hidden inventory totals are shown as unknown. Shadow Byte X-ray has an exposed-ore filter enabled by default: it uses original received neighboring blocks to hide enclosed ore candidates and reduce buried decoys. It does not reconstruct server-hidden data or guarantee that every received ore is real; the same terrain filter applies while Freecam is active.

Freecam retains 0.5–2560 blocks/s and sprint ×3, with the player's body left in place. Flight and NoClip operate in singleplayer or with server-granted flying/spectator permissions. Boat Fly requires the integrated server. A remote server can correct movement; no universal server flight, collision or Anti-Xray bypass is claimed. Anti Kick, Deepslate Bypass, Donut Flight Bypass and Auction Sniper remain unavailable.

Updating the desktop alone does not change a running game. Restart with the updated core or import the new combined Prism profile. The local bridge still accepts only authenticated, current-session commands; credentials, user worlds and account data are never included in the packages.

Release validation and captures are saved under `artifacts/game-effects-1.10.0/` and `artifacts/desktop-1.10.0/`. Previous 1.9.1 gameplay evidence remains separately under `artifacts/game-effects-1.9.1/`.

## Windows release 1.8.0

The following documentation describes the unchanged Windows 1.8 package. Its desktop files and ZIP have not been rebuilt for the Mac 1.9 release.

A portable Windows client and Fabric bridge for **Minecraft Java 26.3**. Release 1.8 upgrades actual in-game chunk analysis, world-height visualization and persistent observations. The existing desktop application, executable, layout, navigation, assets and page previews are unchanged from 1.7. The ZIP is `dist/Tung-Tung-Client-Windows-26.3.zip`.

## Setup

1. Transfer the ZIP to Windows 10/11 and **extract the entire archive**. Keep the `desktop` and `payload` folders beside the EXE.
2. Open **Tung-Tung-Client.exe**. No App Store or developer SDK is required; the app uses Windows .NET Framework.
3. Open the official Minecraft Launcher once if it has never been opened, then close the Launcher and Minecraft.
4. Open **Settings** and click **INSTALL FABRIC CORE**. The default Launcher folder is `%APPDATA%\.minecraft`; select another folder there if needed.
5. Open Minecraft Launcher → Installations → **Tung Tung Client 26.3** → Play. Enable the modded-installations filter. The first launch needs internet access to obtain Minecraft and Fabric dependencies.
6. Join a world or server while leaving Tung Tung Client running. **P** in Minecraft opens the client; **P / Esc** in its window returns to the game. **K** toggles Chunk Finder. **J** opens the new in-game chunk analysis panel; **J / Esc** closes it. The tray icon also opens the desktop client.

Closing or hiding the window leaves it running in the tray. Use **Exit** in the tray menu to quit. If Windows does not move keyboard focus to the app, use Alt+Tab or the tray icon. Windowed Minecraft is easier to switch from than exclusive fullscreen. Native Windows focus behavior has not been tested on this Mac.

The game bridge releases the mouse and held movement keys when P opens the client. P does not open it while Minecraft chat or another game menu is active, and it is not intercepted in other applications. Change game shortcuts in Controls → Key Binds → Tung Tung Client. `START-TUNG-TUNG-CLIENT.cmd` also opens the EXE; `desktop/Launch.ps1` provides a fallback build and launch through Windows PowerShell 5.1.

## Desktop screens

Startup shows a wooden logo on black with a procedurally dancing cutout and a short generated meow. The intro holds for 1.6 seconds, then fades to **Home** over 0.5 seconds. P or Esc can skip it. The dancer moves through bob/sway transforms of one transparent PNG; it is not an extracted animated GIF. Buttons use a short click sound. Settings can mute client sounds, including the startup meow and clicks. Reopening the client uses a short window fade and a 0.5-second black veil transition.

- **Home:** the landing lobby and routes into the client.
- **World Tools:** the ordinary visual, finder, camera, and utility modules, with the supplied transparent world mascot.
- **Findings:** loaded-chunk signals, copying, and JSON export, with the newly supplied transparent avatar exclusive to this page.
- **Chunk Finder settings:** twelve selectable evidence types and scan controls.
- **PvP arsenal:** the seven existing combat/inventory switches with the supplied angel mascot.
- **Tutorials:** local guides for each implemented module, with how it works, quick-start steps, and limitations. Select a module from the guide list and scroll to the remaining entries.
- **Settings:** connection, installation, scan/visual settings, and client sounds.
- **PvP settings:** target/friend filters, range, timing, and damage estimates.

Module cards and their bundled Minecraft item images remain unchanged. Existing solid model highlights, view-center tracers, twelve finder filters, X-ray, cameras and seven PvP modules remain available. Individual chest models keep their orange highlight; the new **Chest Chunks boundaries are red**. Category colors describe chunk observations separately from the existing per-object palette. The Findings avatar and other mascots stay in their existing pages.

## Implemented modules

- **Chunk Finder:** selectable received-chunk signals from storage, spawners, workstations, beacons and current armor stands, alongside amethyst observations. Selected signals feed scoring and first-person model highlights/labels. **J** provides real chunk counts, categories, observation status, timestamps, growth transitions and scoped history directly in Minecraft. The desktop finder settings, copying and export remain available.
- **Storage ESP:** solid type-color model highlights for received chests, barrels, hoppers, furnaces, ender chests, and shulkers. Chest highlights are orange. Container lids, latches, and poses come from their models. Highlights are visible through terrain. Tracers can be enabled separately from the view center.
- **Player ESP / Player Chunks:** highlighted received player models and equipment, names, and distances; Player Chunks separately enables chunk boundaries for those players.
- **Amethyst ESP:** purple solid model highlights for received budding amethyst and mature clusters, retaining transparent crystal planes.
- **Shadow Byte X-ray:** changes Minecraft's actual rendering of received chunks. It hides common terrain blocks and all fluids, including lava, while retaining ores, amethyst, building blocks, containers, and entities. Enable its World Tools card or the shortcut on Findings. It uses the normal received render distance and cannot reveal blocks withheld or obfuscated by the server.
- **Base Chunks / Structures:** amber world-height boundaries and NONE/WEAK/MODERATE/STRONG construction-evidence ratings from observed storage, crafting, redstone and repeated block arrangements. Weak evidence remains labeled weak; natural structures may share these patterns. A geode or spawner alone is not construction evidence, and no rating proves a player base.
- **Spawner Chunks:** cyan world-height chunk boundaries for actual observed standard or trial spawners. Existing spawner model highlights remain available.
- **Freecam:** an independent camera controlled with WASD / Space / Shift. Your body stays in the world; gravity and damage still apply. Mining, building, and attacks are disabled from the detached camera. Highlights and view-center tracers use the active camera; moving it does not teleport your body.
- **Freelook:** independent third-person viewing without turning the player's body. Camera modes switch one at a time and restore the previous perspective when disabled.
- **Player Detection:** notifications for other players actually loaded in the client world.

**Unavailable in this release:** Flight, NoClip, Boat Fly, Fast Swim, Super Trident, Anti Kick, Deepslate Bypass, Donut Flight Bypass, and Auction Sniper. Universal server movement bypasses are not implemented, and Auction Sniper has no server-specific auction adapter. These cards stay disabled.

The mascot and controls appear in the Windows client. Minecraft displays the model highlights, tracer lines, and a small blue **Donut SMP** label with a donut graphic. This is a cosmetic overlay. The old main radar HUD is disabled.

## In-game model highlights

The bridge captures Minecraft's block and block-entity model faces, including chest lids/latches and shulker poses. Received armor stands and players use their model and equipment submissions. Highlight rendering replaces texture RGB with the signal's solid color while retaining texture alpha and geometry shading, so spawner-cage holes and transparent crystal areas stay open. Object highlights do not fall back to an axis-aligned bounding box when model capture is unavailable.

Enable **Tracers from view center** in Settings for lines from the crosshair/view center to highlighted objects. Projection follows the current camera, including Freecam; camera movement does not teleport the player's body. The old per-item chunk bands are replaced by the world-height category system below. Individual object highlights still follow the configured object range and marker budget.

The overlay draws captured faces through terrain and sorts them by depth on the CPU. It uses affine UV mapping for texture-alpha masks rather than the exact world depth buffer, so complex overlapping or translucent custom geometry may differ from normal world rendering. It targets the vanilla Fabric 26.3 renderer; there is no replacement-renderer adapter. Received chunk/entity data and range limits still apply.

Rendering has an 8,192-face frame budget, capture limits of 256 faces per block and 1,024 per entity, and the configured object marker limit. Unsupported custom submissions, missing textures, or exhausted detail budgets can omit geometry. The game HUD and bridge `visualStatus` report these conditions.


## In-game chunk system

Press **J** during gameplay to open **Chunk analysis**. The desktop window does not need to be open. Its six controls are:

| Gameplay control | Chunk color | Actual observations |
| --- | --- | --- |
| Chest chunks | Red `#FF2D2D` | Chests, trapped chests, barrels, all shulker boxes, ender chests, hoppers, furnace-family blocks, dispensers and droppers. Counts distinguish one storage block from a concentration. |
| Amethyst chunks | Purple `#B66CFF` | Amethyst, budding amethyst, small/medium/large buds and mature clusters. Presence alone is not growth. |
| Growth tracking | Purple, brighter when growth is recorded | Actual stage advances at the same previously observed bud position; a short restrained pulse follows a new recorded transition. |
| Structures / Base Chunks | Amber `#FFB020` | Storage, crafting, redstone, construction blocks and repeated rows, with an explicit evidence level. |
| Player chunks | Pink `#FF55CC` | Current, received positions of other observable players. Unobservable positions are removed immediately and are never saved as player history. |
| Spawner chunks | Cyan `#35C9FF` | Received standard and trial spawner blocks, independent of mob type. |

**K** retains its Chunk Finder shortcut. Chest chunks, Amethyst chunks and Growth tracking initially follow K until explicitly changed in the J panel. Structures, Player chunks and Spawner chunks keep their existing independent module switches. The twelve desktop finder filters control selected object matches; the new Chest Chunks category counts all supported observed storage types independently of those filters.

Chunk prisms use exact `chunkX * 16` / `chunkZ * 16` horizontal boundaries and the current dimension's actual vertical bounds. They span world height rather than floating at a detected block's Y. Category borders use thin edges, low-opacity interior fill, restrained glow and eased appearance/disappearance. Mixed categories use small visual edge offsets while retaining the same world coordinates. Historical or pending verification outlines use a subdued dashed style. This is a camera-projected through-terrain overlay, not a server-provided depth or visibility result.

Aim toward a highlighted chunk to select its compact HUD. It reports chunk coordinates, category, block/player counts, status, observation age and relevant construction/growth evidence. **J** opens full details; select a row to pin it, or **Auto select** to restore crosshair selection. The panel can filter all observations, currently verified observations, or history/pending observations. **HUD** controls the compact game information card. **History outlines** adds historical observations when they are within the configured visualization range. The default nearest-chunk limit is 48, configurable to 16–64, and the default chunk visualization range is 128 blocks, configurable to 16–256 in the JSON settings.

Observation states are deliberately separate:

| State | Meaning |
| --- | --- |
| CURRENT / LIVE | A completed scan of the currently received chunk; the displayed timestamp states when it was observed. Relevant received changes invalidate verification and schedule a fresh scan. |
| VERIFYING | A chunk is currently available, but its previous stored observation has not yet been freshly verified. |
| HISTORICAL | A saved observation that is not currently verified in a received chunk. It is not presented as a live finding. |

## Persistent observations and growth

Raw block observations are stored under `config/tun-tun-history/` in the isolated Minecraft profile. Hashed file names separate actual server address or local world directory, dimension and the chosen history profile. Settings remain in `config/tun-tun-client.json`. Leaving an area, unloading a chunk, switching dimensions and restarting the game preserve stored observations, while clearing current verification and player positions.

Use the **History profile** field and **Apply profile** in J to separate seasons or worlds that reuse the same server address. The Minecraft client cannot identify a server reset or a different backend world behind the same address automatically; change the profile or clear that scope when appropriate. When the server identity is unavailable, an ephemeral connection identity isolates the observations; these cannot be matched reliably after reconnecting or restarting. No unknown identity is invented as a verified server/world name.

**Clear scope history** requires a second click and removes only the current server/world + dimension + profile. New real scans can repopulate it. Loading/saving happens on a background worker, with coalesced atomic writes and normal shutdown flushing. A failed history read is reported and its source file is protected from autosave replacement until that scope is explicitly cleared or successfully reloaded.

Memory is bounded to 2,048 stored chunks, 4,096 bud positions per chunk, 65,536 total retained position baselines and 32 recent growth events per chunk; cumulative continuous/gap event counts are retained. Older chunks or position baselines may be evicted by these limits. An evicted baseline can prevent a comparison, but cannot produce an invented growth event.

The growth stages are **Small Amethyst Bud → Medium Amethyst Bud → Large Amethyst Bud → Amethyst Cluster**. Only a later stage at the same previously observed position creates a recorded advance. First-seen mature clusters have unknown history; skipped stages count as one observed advance rather than an invented number of random ticks. Disappeared or regressed buds start a new baseline.

- **Continuous growth:** the same chunk remained observable between the compared completed scans.
- **Advance across an observation gap:** a saved stage was lower before an unload, restart or other observation gap. This remains explicitly labeled as a gap comparison.
- **Simulation activity possible:** growth may reflect random ticking, including simulation caused by the local player or server settings.
- **Authorized server diagnostics:** unavailable in this release. Growth does not independently establish another player, a player base or verified remote activity.

## Chunk Finder settings

Click the **Chunk Finder** card/title in World Tools or **FINDER OPTIONS** on Findings to open the dedicated finder page. The card's switch still enables or disables the module. This page controls twelve evidence types, all enabled by default, with **ALL** and **NONE** actions. Each toggle uses its own Minecraft item image:

| Signal | Filter key | Received evidence | Individual model color |
| --- | --- | --- | --- |
| Chests | `chest` | Chest and trapped-chest blocks. | Orange `#FF931F` |
| Spawners | `spawner` | Standard or trial spawner blocks, regardless of mob type. | Cyan `#42DCE6` |
| Barrels | `barrel` | Barrel blocks. | Amber `#DDA251` |
| Shulker boxes | `shulker` | Shulker-box blocks of any color. | Violet `#BB69FF` |
| Ender chests | `ender_chest` | Ender-chest blocks. | Teal `#3BE7B0` |
| Hoppers | `hopper` | Hopper blocks. | Blue-gray `#86B9DE` |
| Furnaces | `furnace` | Received furnace-family blocks. | Coral `#FF6A49` |
| Crafting tables | `crafting` | Crafting-table blocks. | Brown `#D3A16A` |
| Enchanting tables | `enchanting` | Enchanting-table blocks. | Pink `#F46DB5` |
| Anvils | `anvil` | Anvil-family blocks. | Pale gray `#B6C7D4` |
| Beacons | `beacon` | Beacon blocks. | Aqua `#60FFD6` |
| Armor stands | `armor_stand` | Armor-stand entities already tracked by the client. | Gold `#FFD66A` |

These are base palette values; geometry shading changes face brightness. The separate amethyst family uses purple `#C575FF`.

Enable Chunk Finder and select the signals to include. Selected weak hits can appear as matches and in-game highlights without reaching the stronger base-candidate sensitivity threshold, so a single chest or spawner remains findable. Armor stands use current received entities in received chunks and normal entity tracking; they cannot be found beyond the entities the server has sent. Their current counts refresh rather than becoming stored entity history. These highlights are visible in first person inside Minecraft, rather than solely on the desktop Findings page.

The page also has sensitivity and scan-radius controls. Filters are saved as the `finderTargets` map in `config/tun-tun-client.json`; this map controls all twelve evidence types. The old `storage` and `builds` JSON fields remain for compatibility and do not silently disable explicit finder choices. `amethyst` retains its separate geode/growth method and is unaffected by the twelve-filter map, so **NONE** can still leave separate GEODE findings. Independently enabled Amethyst ESP can still display purple models. Its individual-block rendering is separate from the J-panel Amethyst Chunks and Growth tracking controls. Legacy redstone, bed, portal, and torch signals do not bypass this map. Selected-signal matches require Chunk Finder to be enabled; the independent Structures control uses its construction-evidence classification for amber chunk outlines.

| Finding kind | Meaning |
| --- | --- |
| MATCH | A selected received signal, including a weak hit below the base-candidate threshold. |
| ACTIVITY | A stronger heuristic build candidate that reaches the configured threshold. |
| GEODE | A separate amethyst-geode observation; it is not a base claim. |

Findings are candidate chunks supported by received evidence. Natural structures can contain chests and spawners, so these signals can be false positives for a player base. [Armor stands can also generate in taiga villages](https://www.minecraft.net/en-us/article/taking-inventory--armor-stand). Detection does not read unopened container contents or establish whether a location was raided. A strong score does not establish the location's history or current contents.

## PvP arsenal

Open **PvP arsenal** in the sidebar for combat and inventory module switches, then click **PVP SETTINGS** to tune the shared controls. The client must be connected to Minecraft before changes can be applied.

Seven modules have individual switches:

- **Auto Totem:** searches the whole inventory and uses a normal swap to equip a totem when the offhand is empty or health plus absorption reaches the configured threshold. Set the threshold to 20 for always equip, including with absorption. The player inventory may remain open with an empty cursor; other containers wait.
- **KillAura:** the existing module attacks visible hostile mobs within three blocks using the normal attack cooldown. It keeps its separate hostile-mob targeting rather than the shared player/friend filters.
- **Auto Crystal:** requests crystal placement on suitable loaded obsidian/bedrock bases and attacks loaded crystals when reach, visibility, space, target, and estimated blast limits allow it. Put end crystals in the hotbar.
- **Auto Anchor:** places, charges, and attempts to detonate respawn anchors through normal block interactions. Put anchors and glowstone in the hotbar, with an empty or non-anchor/non-glowstone slot for detonation. Remove glowstone from the offhand before detonation. It refuses the Nether and locations where anchors set spawn.
- **Auto Gapple:** uses a hotbar golden apple when health plus absorption reaches the shared health threshold. Normal apples are preferred over enchanted apples. It holds normal item use and restores the slot/key state it owns after consumption or interruption.
- **Trigger Bot:** attacks an eligible entity under the crosshair with line of sight, normal weapon reach, and full attack cooldown. It does not aim for you.
- **Auto Weapon:** selects a supported hotbar sword/axe while the crosshair points at an eligible target. Stock material tier determines priority; swords precede axes of the same tier. Enchantments and custom item attributes are not ranked. The previous slot is restored when targeting or the module ends, unless you have changed slots manually.

The five PvP automation modules use hotbar resources only; Auto Totem retains its whole-inventory search. Auto Armor is not included in this release. These automation modules pause for game screens, lost game mouse focus, detached cameras, unavailable players, or hands already occupied with item use. Missing interaction APIs produce an explicit paused status.

Auto Anchor waits to observe each placed/charged block state before continuing, with action delays and timeouts. Observed state can include local client prediction; it is not an explicit server acknowledgement. Crystal and anchor candidates use loaded data and normal interaction limits.

| Control | Range / input | Default |
| --- | --- | --- |
| Combat range | 2–6 blocks | 4 blocks |
| Action delay | 2–20 ticks | 6 ticks |
| Maximum self damage | 1–12 HP | 6 HP |
| Minimum target damage | 1–12 HP | 4 HP |
| Totem / Gapple health threshold | 1–20 HP | 10 HP |
| Target players | On / off | On |
| Target hostile mobs | On / off | Off |
| Friends | Comma-separated player names | Empty |

Friend names are case-insensitive; UUIDs are also accepted, with a 512-character limit for the comma-separated list. Press Enter or **APPLY FRIENDS** to save it. Target and friend filters apply to Auto Crystal, Auto Anchor, Trigger Bot, and Auto Weapon. Auto Gapple uses your own health threshold; the existing KillAura keeps its separate hostile-mob targeting within three blocks. Combat range is a selection limit and does not grant extra game reach. HP values use Minecraft health points: two HP equal one heart.

Explosion limits use estimates. Self damage uses vanilla visibility and a conservative hard-difficulty, unarmored upper bound, and requires a further two-HP survival margin. Default limits intentionally pause open-ground blasts and usually require cover. Candidates predicting more than one HP of unarmored damage to a known friend are skipped. Minimum target damage is an unarmored hint, not an armor-aware prediction. These limits do not guarantee the damage result in the game.

## Detection labels

Card labels describe what a module changes or exposes technically:

| Label | Meaning |
| --- | --- |
| CLIENT VISUALS · DETECTION UNKNOWN | Rendering and analysis of data already received by the client. |
| LOCAL CAMERA · DETECTION UNKNOWN | Local camera behavior; no remote detection result has been measured. |
| SERVER-VISIBLE INVENTORY | Auto Totem and Auto Weapon change equipment or hotbar selection. |
| SERVER-VISIBLE COMBAT | KillAura and Trigger Bot send combat actions the server can observe. |
| SERVER-VISIBLE CRYSTAL / ANCHOR ACTIONS | Explosive modules request ordinary placements, uses, or attacks. |
| SERVER-VISIBLE ITEM USE | Auto Gapple uses an item through the game. |
| HIGH EXPOSURE · TIMING UNVERIFIED | An unavailable movement feature would affect server-observed movement; detection timing is unverified. |
| SERVER ADAPTER / SERVER CONTROL / HIDDEN DATA | A disabled feature depends on a server adapter, server-controlled behavior, or block data the client has not received. |

These labels describe technical exposure; Donut SMP detection results have not been measured. The blue Donut SMP graphic does not change module behavior.

## Data limits

The bridge sees only chunks and entities already sent by the server. It does not request additional chunks, change simulation distance, or reconstruct blocks hidden by server-side Anti-Xray. Scan radius is 2–32 chunks; ESP range is 16–256 blocks. The default scan budget is about 2 ms per tick, with a default shared object limit of 192 block/entity highlights per frame, further limited by the model-face budget.

A score of 0–100 is heuristic signal strength, not the probability of a base. Natural structures can also contain chests and spawners. Amethyst alone remains a geode signal. Finding X/Z coordinates mark the chunk center, while Y is the height of a detected block or tracked entity. Stored block history remains separated by world/server, dimension and profile; current verification and player locations reset when the world changes or disconnects.

Amethyst presence, continuous growth and stage advances across observation gaps remain distinct. Stored baselines survive unloads/restarts within the documented memory limits; an unload changes comparison status rather than erasing history. Random ticking can result from the local player. Missing crystals indicate a change without establishing its cause.

X-ray changes the view of blocks already received by Minecraft. It removes common terrain from the chunk mesh, exposes faces next to hidden terrain, skips fluid rendering, disables section occlusion, brightens the lightmap, and clears fog. World blocks and collisions remain unchanged. It does not load additional chunks, extend render distance, or reconstruct server-hidden data. Turning it on or off, or changing worlds, rebuilds chunk meshes and may briefly cause stutter. Switch it off to restore normal rendering. This implementation targets the vanilla Fabric terrain renderer; no Sodium or other replacement-renderer adapter is included.

## Bridge installation and files

INSTALL CORE creates the isolated game folder `.minecraft/tun-tun-26.3`, backs up changed files in `.minecraft/tun-tun-install-backups/<timestamp>/`, and rolls back writes if installation fails. Other profiles' worlds and mods remain in their existing folders. Keep the Launcher closed during installation.

The bundle contains **tung-tung-bridge-1.8.0.jar**, Fabric API **0.162.0+26.3**, and the Fabric Loader **0.19.5** profile. The bridge is installed on your Minecraft client; implemented modules do not require a server plugin. Minecraft and a Java runtime are not included in the ZIP; the official Launcher provides them. This release targets **26.3**, not every future Minecraft release.

The app communicates with the bridge through localhost using a random port and a token stored in `config/tun-tun-connection.json`. It does not expose the bridge to the local network, inject into Minecraft, or read game-process memory. Without a running bridge the dashboard shows **OFFLINE** and game toggles are unavailable.

Game settings are in `config/tun-tun-client.json` inside the isolated profile. Windows preferences are in `%LOCALAPPDATA%\TunTunClient\preferences.json`. JSON export writes actual findings to a file selected in the save dialog.

## Build status and source

The existing Windows EXE and all desktop files are retained from 1.7; native Windows execution and focus behavior remain untested here. The 1.8 game bridge is compiled against the official Minecraft 26.3 client/API. Its test-only runtime harness is excluded from the distribution and packaged source. Existing model highlight shader checks and desktop previews describe the prior unchanged assets; they are not new runtime results for 1.8.

Actual isolated Minecraft gameplay checks passed: 72 unit tests and 56 runtime assertions, including GPU rendering, normal restart persistence, profile/dimension isolation, J/Esc/K/P, and current player tracking through normal integrated-server test-entity packets. A 56-fixture stress world rendered the nearest 48 chunks: median FPS was 117 with modules off and 114 with them on, using 200 samples each in the same inactive-window state on Apple M1 Pro; scanner work P95 was 0.045375 ms. These are local measurements, not a Windows/foreground/public-server guarantee. Eleven genuine captures and detailed reports are in `gameplay-checks` in the ZIP and `artifacts/gameplay-1.8` in the workspace. The temporary game, compiler, assets and test worlds were removed. See `BUILD-INFO.txt` for build details. Authorized server diagnostics remain unavailable; same-address server resets require a separate history profile.

Minecraft bridge source is in `source/src`; build files and the Gradle wrapper are in `source/`. Windows source is `desktop/TungTungClient.cs`, its shared entry point is `desktop/DesktopEntry.cs`, and the fallback compiler/launcher is `desktop/Launch.ps1`. A fresh full Java build requires Java 25 and `./gradlew build` or `gradlew.bat build` from `source/`; it downloads build dependencies. The supplied EXE/JAR does not require a developer SDK.

## Page previews

The viewers and page PNGs in `desktop/preview/` are offline design previews rendered on this Mac from the desktop layout and visual assets. They are labeled previews, not captures of a running Windows application or Minecraft. Open `gallery.html` for twelve page states, including a second PvP scroll position showing Auto Weapon and additional Amethyst/X-ray tutorials; `index.html` provides the interactive preview and intro replay. `screenshots/overview.png` is the contact sheet. The screenshots cover the nine pages: intro, Home, World Tools, Findings, Chunk Finder settings, PvP, Tutorials, Settings, and PvP Settings. Finder settings is `screenshots/finder-settings.png`.

The EXE also provides an optional Windows-only renderer for the actual controls. From the extracted bundle folder, run:

```bat
start "" /wait ".\Tung-Tung-Client.exe" --render-previews ".\desktop\preview\windows"
```

Or use the fallback launcher:

```powershell
powershell.exe -NoProfile -STA -ExecutionPolicy Bypass -File ".\desktop\Launch.ps1" -RenderPreviews ".\desktop\preview\windows"
```

It writes `intro.png`, `home.png`, `world-tools.png`, `findings.png`, `finder-settings.png`, `pvp.png`, `tutorials.png`, `settings.png`, and `pvp-settings.png` in offline mode. It does not start bridge polling, the tray, audio, or preferences reads/writes. This Windows rendering mode has not been executed on this Mac; a rendering error is recorded in `preview-error.txt`.

## Visual assets

`desktop/assets/world-mascot.png` and `pvp-mascot.png` are the supplied transparent portraits. `findings-mascot.png` is the newly supplied PNG copied unchanged with its original alpha, used exclusively on Findings. `dancer.png` is a background-removal edit of the supplied character, and `logo.png` is a generated transparent wooden wordmark. `startup-meow.wav` and `ui-click.wav` are locally synthesized sound effects, with no external audio sample. Module and finder images are unchanged Minecraft 26.3 textures owned by Mojang/Microsoft, in `desktop/assets/items/<module-id>.png` and `items/finder_<filter-key>.png`; `items/sources.json` records their source paths and hashes. They were read through bounded ranges from the official client archive without downloading or installing the full game. Active asset provenance is recorded in `VISUAL-ASSETS.txt`; `ASSET-PROMPT.txt` preserves the earlier illustration prompt as historical provenance. The old generated `tun-tun.png` is not used or included in this bundle.

## Research sources

Primary mechanics references and open-source client designs were reviewed for comparison; Meteor implementation code was not copied into this client.

- [Minecraft: Amethyst Shard](https://www.minecraft.net/en-us/article/taking-inventory--amethyst-shard) — budding amethyst and renewable clusters.
- [Fabric Yarn: BuddingAmethystBlock](https://maven.fabricmc.net/docs/yarn-23w16a%2Bbuild.6/net/minecraft/block/BuddingAmethystBlock.html) — a historical random-tick API reference, not the current 26.3 API. Current growth stages were confirmed from official 26.3 bytecode.
- [Minecraft: Totem of Undying](https://www.minecraft.net/en-us/article/taking-inventory--totem-undying) — using a totem in the offhand.
- [Minecraft: Armor Stand](https://www.minecraft.net/en-us/article/taking-inventory--armor-stand) — armor stands can appear naturally in taiga villages.
- [Minecraft Snapshot 20w12a](https://www.minecraft.net/en-us/article/minecraft-snapshot-20w12a) — the original respawn-anchor and glowstone mechanics.
- Meteor's [Xray](https://github.com/MeteorDevelopment/meteor-client/blob/master/src/main/java/meteordevelopment/meteorclient/systems/modules/render/Xray.java), [CrystalAura](https://github.com/MeteorDevelopment/meteor-client/blob/master/src/main/java/meteordevelopment/meteorclient/systems/modules/combat/CrystalAura.java), and [Freecam](https://github.com/MeteorDevelopment/meteor-client/blob/master/src/main/java/meteordevelopment/meteorclient/systems/modules/render/Freecam.java) — primary design references for rendering, combat settings, and detached-camera behavior.

1.13.1 combat fixes: Auto Totem uses vanilla predicted SWAP with correct changed-slot hashes, refills on inventory packets and does not wait for optional slot echoes. Totem threshold 20 means always equip, including with absorption. Your own inventory screen is supported with an empty cursor; other containers wait. Crystal retries received live entities at most once per client tick and continues held RMB on confirmed removal. Server reach, latency and plugin decisions still apply.

## Farm automation

World Tools → Utility → **Auto Sell**: choose farm resources in the gear menu. Cobblestone is the only default. A complete plain stack triggers `/sell`; the client deposits selected full stacks, waits for server inventory receipts, then clicks a named green Confirm/Sell glass button. Partial, named and enchanted stacks stay untouched. A menu-title filter is optional. The server must close the menu after confirmation. Unknown menus or missing receipts stop the module; server payment is not inferred from client prediction.

**Nearby Player Logout** disconnects at the selected body distance (32 blocks by default), including friends. It and Auto Sell turn off before disconnecting and must be armed again after reconnecting. Only received player entities are observable. Counter-Strike is a locked placeholder in the sidebar.
