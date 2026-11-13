# Changelog

## Desktop and Core 1.32.0 — 2026-11-13

- Search Chunk: red full-height columns and mature-cluster models for current completed scans, adjustable mature-cluster threshold (4 default). Natural growth is not proof of a player base.
- Auto Relog: one reconnect to the same server after one second at Y <= 0 by default; 5-second cancellable countdown, adjustable Y/delay and independent keybind. No auto-Freecam, movement, trident use or retry loop. Manual/nearby-player departures do not trigger reconnects.
- Fixed model budgets being consumed by off-screen geometry; storage range is horizontal so depth does not hide otherwise received containers. Increased and position-sampled bounded markers avoid the old first-512/low-Y bias.
- Added Storage ESP model-limit controls; default visible limit is 512. Rendering and marker storage stay bounded for performance.
- Replaced the round homemade CS icon with Valve's original CS2 SVG wordmark.
- Validation: 123 JUnit tests, explicit relog/marker/above-below geometry checks, farm regression checks and UI interaction/keybind checks. Live multiplayer reconnect still needs an in-game check.

## Desktop and Core 1.31.0 — 2026-11-12

- Auto Sell: 24 selectable farm resources; cobblestone only by default. Only plain full stacks are transferred. Named/enchanted items and partial stacks stay untouched.
- Uses /sell → deposit menu → authoritative stack receipts → one named green Confirm/Sell glass click. No Sell-all command or blind confirmation clicks. Unknown menus, partial acceptance and timeouts stop it.
- Nearby Player Logout: adjustable body distance (32 blocks default), includes friends, turns itself and Auto Sell off before disconnecting. Received player entities only; no automatic reconnect.
- Added a locked Counter-Strike sidebar entry with a round CS mark.
- Added settings, keybinds and tutorials for both modules. Automated policy/UI checks cover selections, receipts and sidebar layout. Live server-menu compatibility still needs an in-game check; payment is confirmed by the server.

## Desktop 1.30.9 — 2026-11-11

- Windows core installation uses only bundled files: five core mods and 39 packs. The former 15 optional downloads no longer block setup.
- Added a separate Optional Extras button with bounded downloads, caching and nonfatal failure reporting.
- Added live installation progress and a local `%LOCALAPPDATA%/TungTungClient/install.log`.
- Added installation locks, an installer deadline and a desktop process timeout; quitting the app stops its own installer.
- Tested the full production payload offline, plus CLI spaced paths and optional-download failures.
- Minecraft core remains 1.30.3 and Voice Chat remains 2.6.27. DonutSMP voice negotiation remains unresolved.

## Desktop 1.30.8 — 2026-11-11

- Fixed Windows installation failing with `Split-Path: Path is an empty string`.
- Desktop passes the bundle folder explicitly; script resolves its fallback only after parameter binding.
- Added installer CLI checks for explicit/inferred bundle paths, spaces and a different working directory.
- Replacement Install-Core.ps1 also supports the existing 1.30.7 Windows bundle.
- Minecraft core remains 1.30.3; Simple Voice Chat remains 2.6.27+26.3. DonutSMP voice negotiation is still unresolved.

## Desktop 1.30.7 — 2026-11-11

- Ordinary Tung Tung Client bundles official Simple Voice Chat 2.6.27+26.3 on Mac, Windows and Prism.
- Installers recognize the new voice mod and replace the older managed version.
- Fixed Windows installation rejecting the desktop version and the five-mod payload containing both map-art mods.
- No diagnostic Voice Test profile is included in any release. Game core remains 1.30.3.
- The upgraded voice mod starts successfully on Mac. DonutSMP voice negotiation remains unresolved; this release does not claim to fix it.

## Desktop 1.30.6 — 2026-11-10

- Invis Mode now shows a translucent white-gray Steve PNG on a transparent background.
- All other module images and behavior remain unchanged.
- Game core remains 1.30.3.

## Desktop 1.30.5 — 2026-11-10

- Night Vision uses the original Minecraft night-vision effect icon.
- Invis Mode uses the vanilla ghast face instead of a potion.
- World Tools and all other module images remain unchanged.
- Game core remains 1.30.3.

## Desktop 1.30.4 — 2026-11-10

- Restored Minecraft PNG item images for all 43 modules.
- Replaced duplicate images with distinct, appropriate vanilla textures.
- Freecam uses phantom membrane; Elytra Finder keeps Elytra.
- Keyboard illustrations, description dialogs, player faces and workspace locks are retained.
- Game core remains 1.30.3.


## 1.30.3 — 2026-11-10

- Original icons for all 43 Minecraft modules and 20 keyboard profiles.
- Larger module description dialogs with instructions and settings links.
- Find Player faces cropped from actual advertised Minecraft skins, cached off the game thread.
- Chess.com, Roblox and Stream remain visible but their entry points are locked.
- Game core 1.30.3 supplies face thumbnails; older cores show an unavailable-skin placeholder.


## Desktop 1.30.2 — 2026-11-10

- Moved Client Maps and Map in Slot into Texture Packs → Map Art.
- Preserved independent module switches and keybind settings, with Back returning to Texture Packs.
- Replaced the sidebar Roblox approximation with the official white Tilt icon.
- Stream now shows only one Twitch logo.
- Minecraft game core remains 1.30.1; no gameplay changes.
- Browser UI checks passed for relocation, navigation, icons and JavaScript errors.

## 1.30.1 — 2026-11-10

- Bundled Client Maps 1.3.4 and Map in Slot 3.5.0.
- Added separate World Tools → Visual cards with switches and keybind settings.
- Adapted Map in Slot to obey its runtime switch without removing other item decorations.
- Kept original mod licenses; documented the Map in Slot modification.
- Built Mac archives with and without Simple Voice Chat and the Prism profile.
- Added shared installation and future-update documentation for distribution.

Validation: Gradle build/tests passed; an isolated Minecraft 26.3 client loaded
both map mods; transformed render code contains the Map in Slot switch; Mac
native self-test, code signature and archive checks passed. Map-art rendering
with a real server map was not visually verified in that isolated test.

Windows compilation and packaging checks are reported separately in the
distribution manifest. Windows gameplay requires validation on Windows.
