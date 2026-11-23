# BetterMaceSwap in Arsenal — 1.36.4

The original BetterMaceSwap 5.0.0 for Minecraft 26.3 is bundled with Cloth Config 26.3.159 and Mod Menu 21.0.1. Pinned URLs and SHA-256 hashes: `packaging/better-mace-mods.json`.

1. Install/update Tung Tung Client and its Fabric Core with Minecraft closed.
2. Start **Tung Tung Client 26.3** in Minecraft Launcher and join your world.
3. Open **Arsenal → BETTER MACE** (button at the top), choose a module and use its switch or settings gear.
4. Assign a separate keybind on each module page. Native BetterMaceSwap default hotkeys are unbound in this managed profile to avoid duplicate commands.

Automatic Aim keeps the original Tung Tung implementation, its range, filters and keybind. BetterMace Aim Assist is a separate module; the two aim helpers cannot run together. TriggerBot and Shield Breaker cards control BetterMaceSwap. Shield Breaker is named **Auto Stun Slam** because the original mod uses an axe/mace/sword sequence; this differs from Tung Tung’s former right-click axe-only helper. Shared Players/Mobs filters are native BetterMaceSwap filters. Tung Tung friend/damage filters do not apply to the original mod.

Every native feature starts off on first managed adoption. Gear Up has three separate cards: arrange your inventory, click **Record Kit**, then use **Equip Kit** or its assigned gameplay key. Recording overwrites that kit’s saved arrangement. Arm Elytra Boost is a one-shot action and requires Elytra Boost enabled.

Settings and switches write the original mod configuration, not simulated UI state. An unsupported or missing mod disables these controls. Auto Crystal, Auto Anchor and other unrelated Tung Tung modules remain separate.

Development: run `python3 scripts/fetch-better-mace.py` before Gradle/platform builds. The adapter is reflective; required mod JARs remain separate in the installed mods folder. Source licenses and attribution are in `packaging/BETTER-MACE-NOTICE.txt`.

Validation: production core build, tests against the actual downloaded ModConfig class, desktop rendering/control checks and installer fixture checks. Live PvP behavior depends on inventory, native trigger settings and the server; automated tests do not establish live-server success.

## Shield Breaker / Auto Stun Slam

Enable Auto Stun Slam in Arsenal, put a usable **axe** and your **mace** in the hotbar, hold the mace and right-click a visibly blocking opponent once at normal axe reach. That gesture sends one axe attack and queues the native mace follow-up. Holding RMB cannot repeat it; release before the next click. The same gesture does not also run the native wind-charge helper.

The upstream left-click trigger remains available. Its axe lookup is corrected to exclude pickaxes and unusable axes. Players targeting must be enabled. Request counters and unmet conditions are shown in the card status; a sent attack does not establish that the server accepted shield damage. Auto Aim is unchanged.

Right-clicks are evaluated at vanilla item use, after hotbar selection is applied. A wind-charge-to-mace switch in the same frame no longer checks the previous item. Short released taps remain valid; holding does not repeat. The card reports the actual held item ID and hotbar slot if a mace is not selected, and normal left-clicks preserve the last use diagnostic.
