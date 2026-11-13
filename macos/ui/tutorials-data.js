const GUIDES = {
  "cave_detector": {
    "how": "Automatically scans all received underground blocks in each nearby chunk. Green full-height columns mean no air, water or lava cavities were detected in the scanned band.",
    "start": "World Tools → Finder → Auto Cave Detector. Enable and wait for complete scans. Range and visible chunk limit use the chunk highlight controls. Assign its own key in the gear menu.",
    "limit": "All 256 columns must contain a valid underground band, from world floor + 5 to five blocks below the opaque surface. Void, unloaded, incomplete and changed chunks are excluded. Cavities hidden by the server cannot be checked. Green does not prove a base."
  },
  "search_chunk": {
    "how": "Marks current scanned chunks red when they contain the selected minimum of mature amethyst clusters. The red column spans the whole world height and mature crystals use red model highlights.",
    "start": "World Tools → Finder → Sus Chunk. Choose the minimum cluster count (1 for new profiles) and the separate 16–512-block range, assign a key if desired, then enable. Newly received chunks are scanned automatically, including after a normal relog. The live status shows checked/pending chunks and received mature crystals.",
    "limit": "A heuristic, not proof of a base or another player. Clusters grow naturally under simulation, including your own presence. Historical and still-verifying chunks do not create current red Sus Chunk signals."
  },
  "auto_relog": {
    "how": "One reconnect to the same multiplayer server after your body stays at or below the chosen Y for one second. The default is Y 0 with a 5-second cancellable countdown.",
    "start": "World Tools → Utility → Auto Relog. Set Y and delay, assign a key if desired, then arm once. Minecraft shows Cancel reconnect; Escape cancels too. Arm again for another relog.",
    "limit": "No automatic movement, item use or camera changes. Unexpected disconnects do not start reconnects. No retry loop if connecting fails, and manual navigation cancels the countdown."
  },
  "auto_sell": {
    "how": "Sends /sell when selected plain farm items form full stacks. Deposits one stack at a time, checks authoritative inventory updates, then clicks the named green Confirm/Sell glass button once.",
    "start": "World Tools → Utility → Auto Sell settings. Choose resources and optionally the exact menu title. Leave the title blank to recognize the green button automatically. Enable Auto Sell, close other menus and collect full stacks.",
    "limit": "Requires a chest-style /sell deposit menu with a named green Confirm/Sell glass button that the server closes after confirmation. Unknown menus and timeouts stop automation. Server prices and payment are not controlled by the client. Re-arm after reconnecting."
  },
  "auto_logout": {
    "how": "Disconnects when a received non-spectator player comes within the configured distance of your body. Friends are included. Both Auto Sell and this module are turned off before disconnecting.",
    "start": "World Tools → Utility → Nearby Player Logout. Set 8–256 blocks (32 default), optionally assign a key, then enable. Re-enable manually for a new session.",
    "limit": "Only player entities actually sent to your client can be detected. Server-hidden or unloaded players remain unknown. This module never reconnects automatically."
  },
  "item_finder": {
    "how": "Finds selected dropped item entities in received chunks, with names, stack counts, distance and gold chunk outlines. Includes 50 item types; colored shulker boxes share one filter.",
    "start": "Open World Tools > Item Finder, choose item types and range, then enable it. ALL and NONE set the whole list. Pickups and despawns remove markers.",
    "limit": "Only currently received dropped entities are searched. Container contents, unloaded chunks and the items inside shulker boxes are not scanned. Prices vary by server."
  },
  "invis_mode": {
    "how": "Reveals the avatars of invisible players already received from the server and draws Invisible in red above Player Info. Real invisibility flags and equipment are preserved.",
    "start": "Open World Tools → Visual, enable Invis Mode and return to Minecraft. Its module settings support an optional keybind.",
    "limit": "Only received player entities can be shown. Turning it off restores vanilla invisibility rendering; your own model and other Player Info settings are unchanged."
  },
  "night_vision": {
    "how": "Brightens the local lightmap so unlit caves and night scenes stay visible, including during the Darkness effect. Terrain stays fully visible.",
    "start": "Open World Tools, choose Visual and enable Night Vision. You can assign a keyboard shortcut in its module settings.",
    "limit": "Lighting changes only on your screen. Disable to restore normal lighting. Your brightness setting and real potion effects are preserved; this does not stop mobs from spawning."
  },
  "chunk_finder": {
    "how": "Scores selected received blocks and tracked armor stands. Individual chest models are orange. Separate red storage, purple amethyst, amber structure and cyan spawner categories cover the full world height.",
    "start": "Choose signals and enable Chunk Finder. Press J inside Minecraft for category controls, current observations, saved history and growth details.",
    "limit": "Saved block observations are separate for each server, world, dimension and history profile. Scores and growth do not establish player presence."
  },
  "storage_esp": {
    "how": "Fills received container models with their type color: chests are orange. Model details and texture cutouts remain intact; contents are not revealed.",
    "start": "Enable Storage ESP in World Tools \u2192 Visual. Adjust ESP range and tracers in Control center, then view received objects in Minecraft.",
    "limit": "Storage contents require normal server interaction. Unloaded chunks and server-hidden blocks remain unknown."
  },
  "player_esp": {
    "how": "Draws a direction arrow and thin view-center tracer toward each received player. It leaves player models and armor unfilled.",
    "start": "Open Player Controls from its card. Choose the direction color and Player visual range, then enable Player Direction. Orange is the default.",
    "limit": "Server tracking range still applies. An arrow cannot reveal a player the server has not sent."
  },
  "amethyst_esp": {
    "how": "Fills received budding amethyst and crystal models with purple. Budding grows through random ticks; visible crystals do not confirm nearby players.",
    "start": "Enable Amethyst ESP in World Tools \u2192 Visual. Explore loaded chunks and adjust range or tracers in Control center.",
    "limit": "Existing crystals may be old or natural. Amethyst Growth records actual same-position stage changes; saved history survives unload/reload and restart, with observation gaps identified separately. Your own presence can cause growth."
  },
  "xray": {
    "how": "Hides common terrain, water and lava from received chunks. Exposed ores only uses actual received neighboring blocks to filter buried ore decoys.",
    "start": "Enable Shadow Byte X-ray, then keep Exposed ores only on in Control center. This filter also applies when using Freecam with X-ray.",
    "limit": "A server may replace hidden ores with misleading blocks. This filter reduces buried decoys; exposed ore identity is still unverified until the server reveals it. Turning the filter off shows all received ore candidates."
  },
  "freecam": {
    "how": "Moves a local camera independently while the player's body stays in place. Camera movement is limited to terrain already received by the client.",
    "start": "Click the Freecam card for its speed controls: 0.5\u20132560 blocks/s, exact input and four presets. Enable Freecam, return to Minecraft and use WASD, Space and Shift. Hold the sprint key (Ctrl by default) for 3\u00d7 speed.",
    "limit": "Your body remains vulnerable. Freecam and Freelook are mutually exclusive; neither loads distant terrain on its own."
  },
  "freelook": {
    "how": "Provides an independent third-person camera without changing your player's aim. The player's normal movement continues while you look around.",
    "start": "Enable Freelook, return to Minecraft and move your mouse. Disable it to restore your previous camera mode.",
    "limit": "Freelook and Freecam are mutually exclusive. A local camera feature does not provide a detection guarantee."
  },
  "base_chunks": {
    "how": "Shows amber full-world-height boundaries for observed construction-related counts and patterns. Evidence is rated weak, moderate or strong.",
    "start": "Enable Base Chunks in World Tools. Press J in Minecraft for evidence details and historical versus current observations.",
    "limit": "Natural structures can share construction characteristics. An evidence rating does not establish that a player created or owns the area."
  },
  "player_chunks": {
    "how": "Shows pink full-world-height boundaries for player positions actually received by the client in loaded chunks. Current positions are never stored as history.",
    "start": "Enable Player Chunks in World Tools \u2192 Finder. Use its boundaries alongside Player ESP to inspect received player positions.",
    "limit": "Players outside the server's tracking range remain unknown. This module does not infer hidden or unloaded players."
  },
  "player_detection": {
    "how": "Notifies you when another player enters the received client-world player list. It uses tracked entities, rather than a hidden-player search.",
    "start": "Enable Player Detection in World Tools \u2192 Finder, then watch in-game notifications as loaded players change.",
    "limit": "No player data exists beyond tracking range. Disconnected or unloaded players are not retained as live detections."
  },
  "spawner_chunks": {
    "how": "Shows cyan full-world-height boundaries for observed normal and trial spawners. Current and historical block observations are distinguished.",
    "start": "Enable Spawner Chunks for its explicit chunk boundaries. Chunk Finder selections control solid-color models, matching chunk colors and candidate scoring.",
    "limit": "Only received spawner blocks qualify. A natural dungeon or trial chamber can create the same signal."
  },
  "auto_totem": {
    "how": "Immediately equips a totem from the main inventory or hotbar whenever the offhand has none. Health and absorption do not delay it; displaced items are preserved.",
    "start": "Carry spare totems, enable Auto Totem and return to Minecraft. It also refills from player slots while an ordinary container screen is open.",
    "limit": "A received inventory update triggers refill immediately; each client tick also checks before combat. Server latency and rejected slot changes can still delay confirmation."
  },
  "killaura": {
    "how": "Automatically attacks nearby visible enemies in any direction, including while you stand still and look elsewhere. Uses normal weapon reach and the chosen fast or cooldown timing.",
    "start": "Put a weapon in the hotbar, select targets in PvP settings, enable KillAura and click Return to Minecraft.",
    "limit": "Normal weapon reach and line of sight apply. Friends and excluded target types are skipped."
  },
  "auto_crystal": {
    "how": "In right-click assist mode, waits for your placed crystal to spawn, then attacks that crystal through the normal game action. Turning Follow my right click off restores target-based placement and attacks.",
    "start": "Enable Auto Crystal and place your crystal on obsidian or bedrock normally. PvP Settings \u2192 Click Assist selects the mode; self-damage limits and normal reach still apply.",
    "limit": "Needs valid obsidian or bedrock placement and normal reach. Damage estimates do not confirm actual damage; server actions remain observable."
  },
  "auto_anchor": {
    "how": "In right-click assist mode, follows your actual anchor placement, finds glowstone across your inventory, charges, optionally places protective glowstone, and detonates after relevant server updates. Restores inventory changes it still owns.",
    "start": "Enable Auto Anchor, carry an anchor and glowstone, then place the anchor normally. PvP Settings \u2192 Click Assist controls manual mode and protective cover. Cover needs extra glowstone and a valid reachable placement.",
    "limit": "Refuses spawn-setting dimensions, invalid reach and self damage beyond your configured limit. Cover must lie between you and the blast; it can reduce damage but does not guarantee zero. Open menus and detached cameras pause helpers."
  },
  "auto_gapple": {
    "how": "Uses hotbar golden or enchanted golden apples when health plus absorption reaches the shared PvP health threshold, restoring the slot and use-key state it owns.",
    "start": "Put apples in the hotbar, enable Auto Gapple, set the Auto Gapple health threshold, then return to focused gameplay.",
    "limit": "The threshold is shared with Auto Totem. Manual item use, open menus or detached camera controls pause automation."
  },
  "trigger_bot": {
    "how": "Attacks the eligible entity under your crosshair at normal game reach and full attack cooldown. Shared targets, friends and action delay determine eligibility.",
    "start": "Enable TriggerBot, choose PvP target types and friends, then aim directly at an eligible target in Minecraft.",
    "limit": "It does not rotate your view or expand reach. Combat actions remain server-visible."
  },
  "auto_weapon": {
    "how": "Selects the best stock sword or axe in the hotbar when your crosshair points at an eligible target. It does not attack by itself.",
    "start": "Put candidate swords or axes in the hotbar, enable Auto Weapon and aim at eligible targets.",
    "limit": "Ranking ignores enchantments. The previous slot restores when the target disappears, unless you manually select another slot."
  },
  "flight": {
    "how": "Move your actual body with WASD / Space / Shift. Singleplayer grants temporary flight; multiplayer requires server-granted permission.",
    "start": "Enable Flight and click Return to Minecraft. Use WASD to move, Space to ascend and Shift to descend.",
    "limit": "Movement pauses for game menus, detached cameras and lost mouse focus. Temporary owned state is restored when disabled or leaving a world."
  },
  "noclip": {
    "how": "Move your body through blocks in spectator mode. Singleplayer temporarily changes mode and restores it afterward; multiplayer requires spectator permission.",
    "start": "Enable NoClip in singleplayer and click Return to Minecraft. Use spectator movement through blocks. Disable it to restore your original mode.",
    "limit": "Movement pauses for game menus, detached cameras and lost mouse focus. Temporary owned state is restored when disabled or leaving a world."
  },
  "boat_fly": {
    "how": "Move a controlled boat with WASD / Space / Shift in singleplayer; real vehicle movement is applied on the integrated server.",
    "start": "Sit in the controlling seat of a boat in singleplayer, enable Boat Fly and click Return to Minecraft. Use WASD / Space / Shift.",
    "limit": "Movement pauses for game menus, detached cameras and lost mouse focus. Temporary owned state is restored when disabled or leaving a world."
  },
  "fast_swim": {
    "how": "Boost actual directional swimming while in water. Multiplayer movement remains server-authoritative.",
    "start": "Enter water on foot, enable Fast Swim and click Return to Minecraft. Use WASD / Space / Shift to swim.",
    "limit": "Movement pauses for game menus, detached cameras and lost mouse focus. Temporary owned state is restored when disabled or leaving a world."
  },
  "super_trident": {
    "how": "Charge and release your Riptide trident yourself. Super Trident boosts the launch velocity ×3 once per release.",
    "start": "Hold a Riptide trident, enter water or rain, then charge and release it normally.",
    "limit": "Movement pauses for game menus, detached cameras and lost mouse focus. Temporary owned state is restored when disabled or leaving a world."
  },
  "aim_cursor": {
    "how": "Rotates your actual view toward the nearest eligible visible player within a separate viewing range. It does not extend attack reach.",
    "start": "Enable Aim Cursor and set Aim viewing range in Player Controls (3–256 blocks). Friends stay excluded. Auto Punch attacks only at normal game reach.",
    "limit": "Needs a received player, normal line of sight and focused gameplay. Menus and detached cameras pause it."
  },
  "auto_punch": {
    "how": "Uses the normal attack action on an eligible player under your crosshair when reach and attack cooldown permit.",
    "start": "Enable Auto Punch, then aim at a player or enable Aim Cursor. Shared friends and combat range apply.",
    "limit": "Does not extend reach or attack through walls. Server-accepted attacks and their outcome remain authoritative."
  },
  "auto_kill_steal": {
    "how": "Chooses an eligible visible player with low received health and attempts a normal attack when ready.",
    "start": "Enable Auto Kill Steal with a usable weapon. Set shared range and excluded friends in PvP Settings.",
    "limit": "Health may change between updates. The module cannot promise a finishing hit or an attack beyond normal reach."
  },
  "player_info": {
    "how": "Adds a compact label above received players: health, observed totem uses and a visible held totem.",
    "start": "Enable Player Info in Player Controls. Adjust Player info size from 0.45× to 1.0× and the shared Player visual range.",
    "limit": "Totem uses count only events observed during this connection. Hidden inventory totals and exact remaining totems are unknown."
  }
};
