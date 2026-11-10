# Changelog

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
