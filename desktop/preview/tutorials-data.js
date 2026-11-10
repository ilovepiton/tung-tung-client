const GUIDES = {
  "chunk_finder": {
    "how": "Scores selected blocks and tracked armor stands. Same-position amethyst growth across uninterrupted loaded samples remains separate evidence; your own presence can cause it.",
    "start": "Choose signals and enable Chunk Finder. Solid-color models get matching chunk bands, one per type, plus optional view-center tracers.",
    "limit": "Weak selected hits still appear in Findings. Sensitivity changes candidate scoring. Other ESP modules stay independent; crystals and natural features do not prove a base."
  },
  "storage_esp": {
    "how": "Fills received container models with their type color: chests are orange. Model details and texture cutouts remain intact; contents are not revealed.",
    "start": "Enable Storage ESP in World Tools → Visual. Adjust ESP range and tracers in Control center, then view received objects in Minecraft.",
    "limit": "Storage contents require normal server interaction. Unloaded chunks and server-hidden blocks remain unknown."
  },
  "player_esp": {
    "how": "Fills received player models with their type color, labels and optional view-center tracers. The server determines which players enter the tracking list.",
    "start": "Enable Player ESP in World Tools → Visual, then adjust ESP range and tracers in Control center.",
    "limit": "It cannot reveal players outside tracking range or obtain player data the server has not sent."
  },
  "amethyst_esp": {
    "how": "Fills received budding amethyst and crystal models with purple. Budding grows through random ticks; visible crystals do not confirm nearby players.",
    "start": "Enable Amethyst ESP in World Tools → Visual. Explore loaded chunks and adjust range or tracers in Control center.",
    "limit": "Existing crystals may be old or natural. Chunk Finder compares uninterrupted loaded samples; unloading/reloading breaks observation history, and your own presence can cause growth."
  },
  "xray": {
    "how": "Hides common terrain plus water and lava, removes fog and brightens the view. It exposes faces of interesting blocks already received from the server.",
    "start": "Enable Shadow Byte X-ray in World Tools → Visual or from Findings. Explore loaded terrain; switching it off rebuilds normal terrain meshes.",
    "limit": "Vanilla Fabric renderer only. Uses received chunks at normal client distance; blocks hidden by the server remain unknown."
  },
  "freecam": {
    "how": "Moves a local camera independently while the player's body stays in place. Camera movement is limited to terrain already received by the client.",
    "start": "Enable Freecam, return to Minecraft and use WASD, Space and Shift. Disable Freecam to restore your normal camera.",
    "limit": "Your body remains vulnerable. Freecam and Freelook are mutually exclusive; neither loads distant terrain on its own."
  },
  "freelook": {
    "how": "Provides an independent third-person camera without changing your player's aim. The player's normal movement continues while you look around.",
    "start": "Enable Freelook, return to Minecraft and move your mouse. Disable it to restore your previous camera mode.",
    "limit": "Freelook and Freecam are mutually exclusive. A local camera feature does not provide a detection guarantee."
  },
  "base_chunks": {
    "how": "Adds optional chunk boundaries for stronger activity candidates. Chunk Finder also adds matching colors for selected signals.",
    "start": "Enable Base Chunks for stronger activity candidates. Enable Chunk Finder as well to include weak selected matches in Findings.",
    "limit": "Natural structures and incomplete received data can create misleading signals. Treat the score as a heuristic."
  },
  "player_chunks": {
    "how": "Adds optional chunk boundaries around players currently tracked by the server. Player ESP separately highlights their models.",
    "start": "Enable Player Chunks in World Tools → Finder. Use its boundaries alongside Player ESP to inspect received player positions.",
    "limit": "Players outside the server's tracking range remain unknown. This module does not infer hidden or unloaded players."
  },
  "player_detection": {
    "how": "Notifies you when another player enters the received client-world player list. It uses tracked entities, rather than a hidden-player search.",
    "start": "Enable Player Detection in World Tools → Finder, then watch in-game notifications as loaded players change.",
    "limit": "No player data exists beyond tracking range. Disconnected or unloaded players are not retained as live detections."
  },
  "spawner_chunks": {
    "how": "Adds optional chunk boundaries for received normal and trial spawners. Spawners can be natural, so their presence is not proof of a player build.",
    "start": "Enable Spawner Chunks for its explicit chunk boundaries. Chunk Finder selections control solid-color models, matching chunk colors and candidate scoring.",
    "limit": "Only received spawner blocks qualify. A natural dungeon or trial chamber can create the same signal."
  },
  "auto_totem": {
    "how": "Equips a totem in the offhand when it is empty or health plus absorption reaches the shared health threshold. Uses normal inventory interactions.",
    "start": "Carry a totem in your inventory, enable Auto Totem and set the PvP Totem health threshold. Return to Minecraft.",
    "limit": "Needs an available inventory totem and server-accepted slot actions. A server can delay or reject the inventory change."
  },
  "killaura": {
    "how": "Attacks visible hostile mobs within three blocks using normal attacks and full attack cooldown. This legacy module has its own target list.",
    "start": "Enable KillAura, hold a weapon and return to Minecraft near visible hostile mobs.",
    "limit": "The new shared PvP target switches do not redefine legacy targeting. Normal reach and server-visible combat still apply."
  },
  "auto_crystal": {
    "how": "Places and breaks nearby end crystals around eligible targets, using received block data and estimated blast exposure. Placement and attacks use normal game interactions.",
    "start": "Put crystals in the hotbar, select target types, add friends, then set range, delay and damage limits in PvP settings.",
    "limit": "Needs valid obsidian or bedrock placement and normal reach. Damage estimates do not confirm actual damage; server actions remain observable."
  },
  "auto_anchor": {
    "how": "Places, charges and detonates respawn anchors where anchors explode, waiting for received block changes between stages.",
    "start": "Provide hotbar anchors, glowstone and an empty or other-item slot. Keep glowstone out of the offhand; configure targets and damage limits.",
    "limit": "Received updates are observed client state, rather than confirmed server acknowledgement. Open menus, detached cameras or manual item use pause automation."
  },
  "auto_gapple": {
    "how": "Uses hotbar golden or enchanted golden apples when health plus absorption reaches the shared PvP health threshold, restoring the slot and use-key state it owns.",
    "start": "Put apples in the hotbar, enable Auto Gapple, set the Totem health threshold, then return to focused gameplay.",
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
  }
};
