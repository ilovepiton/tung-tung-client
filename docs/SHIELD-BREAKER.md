# Auto Shield Breaker — 1.35.4

Open **Arsenal → Auto Shield Breaker** and enable it. Put a mace and a usable axe in your hotbar. Hold the mace, aim at an opponent blocking with a raised shield, and click your mouse use button once (right-click by default).

The helper selects the axe and immediately sends **one axe attack**, then immediately restores your original mace slot and finishes. **It does not attack with the mace.** Your next mace hit stays manual. This also applies during a Wind Burst descent; normal reach and visibility still apply.

No shield means no axe selection. An ordinary left-click does not start the helper; it cancels a pending axe action. Holding use does not repeat. A new physical click is required for each attempt. If the shield lowers before the axe attack, the target leaves the crosshair/reach, you change slots, open a menu, lose focus or change worlds, the action is cancelled. Your manual slot choice is preserved. A usable axe must be in the hotbar; no inventory rearrangement is performed. Listed friends are excluded.

The helper is a separate Arsenal module, with its own keybind. It is not another control inside the autonomous Mace PvP bot. That bot yields during the pending manual action. Server shield disable and attack acceptance are not guaranteed.

## Update

**UPDATE** in the top bar or on any module card updates the whole desktop/core package from `ilovepiton/tung-tung-client` GitHub Releases. Modules are bundled, so they do not have separate version downloads. Close Minecraft and Launcher first. The updater checks the release version, verifies the matching SHA256 archive, prepares the new core, backs up replaced application files and restarts the desktop app. It never force-closes Minecraft. Existing downloaded/verified archives can be reused on retry. Backups and update logs are in the platform's TungTungClient application-data Updates folder.

Windows runtime and a future-release end-to-end self-update are not yet verified. Automated core and updater contract checks are separate from live gameplay validation.
