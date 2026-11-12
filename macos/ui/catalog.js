const MODULES = [
 {id:"auto_sell",name:"Auto Sell",category:"Utility",available:true,note:"Sell selected full farm stacks through /sell and a matching deposit menu with a named green Confirm/Sell glass button. Partial stacks and named or enchanted items stay untouched. Green Confirm/Sell glass is required; an optional exact menu title can narrow matching.",status:"Full stacks only"},
 {id:"auto_logout",name:"Nearby Player Logout",category:"Utility",available:true,note:"Disconnect when any received player, including a friend, approaches your body. Adjustable distance. Turns itself and Auto Sell off; no auto-rejoin.",status:"Received players only"},
 {id:'client_maps',name:'Client Maps',category:'Visual',available:true,note:'Save received map art separately for every server and restore it later in the same Minecraft profile.',status:'Local map storage'},
 {id:'map_in_slot',name:'Map in Slot',category:'Visual',available:true,note:'Render the real received map image directly inside filled-map inventory slots.',status:'Local inventory preview'},
 {id:'invis_mode',name:'Invis Mode',category:'Visual',available:true,note:'Reveal received invisible players and show a red Invisible header above their Player Info. Real invisibility effects are preserved.',status:'Invisible player avatars'},
 {id:'item_finder',name:'Item Finder',category:'Finder',available:true,note:'Find 50 useful dropped item types: totems, armor, crystals, anchors and more. Names, counts, distances and live chunk highlights.',status:'Received dropped items'},
 {id:'night_vision',name:'Night Vision',category:'Visual',available:true,note:'See caves and nights at full brightness. Removes darkness dimming while keeping all blocks visible. Switch off to restore normal lighting.',status:'Local lighting'},
 {id:'elytra_finder',name:'Elytra Finder',category:'Finder',available:true,note:'Nearest received framed or dropped Elytra, direction for walking or bridging, and persistent nearby-player alerts.',status:'Received entities only'},
 {id:'auto_vault',name:'Trial Chambers',category:'Utility',available:true,note:'One normal or ominous vault opening at the selected preview. Loot is random; timing never guarantees a reward.',status:'One normal key interaction'},
 {id:'mace_bot',name:'Mace PvP',category:'PvP',available:true,note:'Mace, axe, sword and shield. Reads actual enchantments; one deliberate wind launch at a time. No Elytra. Friends excluded.',status:'Choose a keybind in settings'},
 {id:'combat_bot',name:'Combat Bot',category:'PvP',available:true,note:'One-fight Crystal / Anchor coordinator. Press P to arm; Auto Totem is active while armed. Friends are always excluded.',status:'Explicitly armed in game'},
 {id:'diamond_esp',name:'Highlight Diamonds',category:'Visual',available:true,note:'Cyan highlights for received diamond and deepslate diamond ore.',status:'Loaded blocks'},
 {id:'ore_esp',name:'Highlight Ores',category:'Visual',available:true,note:'Choose individual minerals: emerald, lapis, iron, gold, copper, redstone, coal, quartz, ancient debris, amethyst and sulfur.',status:'Loaded blocks'},
 {id:'lava_esp',name:'Highlight Lava',category:'Visual',available:true,note:'Amber lava surface highlights within the selected range.',status:'Loaded blocks'},
  {
    "id": "chunk_finder",
    "name": "Chunk Finder",
    "category": "Finder",
    "available": true,
    "note": "Twelve selected signals with solid-color object models and view-center tracers. Press J in Minecraft for full-height category outlines and persistent chunk observations.",
    "status": "Client visuals \u00b7 detection unknown"
  },
  {
    "id": "storage_esp",
    "name": "Storage ESP",
    "category": "Visual",
    "available": true,
    "note": "Solid-color highlights for received container models; chests are orange. Optional tracers start at the view center.",
    "status": "Client visuals \u00b7 detection unknown"
  },
  {
    "id": "amethyst_esp",
    "name": "Amethyst ESP",
    "category": "Visual",
    "available": true,
    "note": "Purple solid-color highlights for received budding amethyst and crystals. Budding grows via random ticks; existing crystals do not prove player presence.",
    "status": "Client visuals \u00b7 detection unknown"
  },
  {
    "id": "xray",
    "name": "Shadow Byte X-ray",
    "category": "Visual",
    "available": true,
    "note": "Hides common received terrain. Exposed ores only filters buried decoys by real neighboring blocks; it cannot reconstruct true ore data hidden by a server.",
    "status": "Client visuals \u00b7 detection unknown"
  },
  {
    "id": "player_esp",
    "name": "Player Direction",
    "category": "Visual",
    "available": true,
    "note": "Orange view-center direction arrows and tracers leave player models and armor intact. Set their color and received-player range in Player Controls.",
    "status": "Client visuals \u00b7 detection unknown"
  },
  {
    "id": "freelook",
    "name": "Freelook",
    "category": "Camera",
    "available": true,
    "note": "Look around independently of your movement direction. The local camera does not change your player's aim.",
    "status": "Local camera \u00b7 detection unknown"
  },
  {
    "id": "freecam",
    "name": "Freecam",
    "category": "Camera",
    "available": true,
    "note": "Independent camera with 0.5\u20132560 blocks/s speed and 3\u00d7 sprint boost. Your body stays in place; only received terrain is visible.",
    "status": "Local camera \u00b7 detection unknown"
  },
  {
    "id": "base_chunks",
    "name": "Base Chunks",
    "category": "Finder",
    "available": true,
    "note": "Amber full-height chunk boundaries for observed construction evidence. Weak and strong evidence describe received patterns; they do not establish a player base.",
    "status": "Client visuals \u00b7 detection unknown"
  },
  {
    "id": "player_chunks",
    "name": "Player Chunks",
    "category": "Finder",
    "available": true,
    "note": "Pink full-height boundaries for current, received player positions in actually loaded chunks. Old positions disappear when unavailable.",
    "status": "Client visuals \u00b7 detection unknown"
  },
  {
    "id": "spawner_chunks",
    "name": "Spawner Chunks",
    "category": "Finder",
    "available": true,
    "note": "Cyan full-height boundaries for observed normal and trial spawners. Spawners can be naturally generated.",
    "status": "Client visuals \u00b7 detection unknown"
  },
  {
    "id": "player_detection",
    "name": "Player Detection",
    "category": "Finder",
    "available": true,
    "note": "Show tracked players in the loaded world.",
    "status": "Client visuals \u00b7 detection unknown"
  },
  {
    "id": "auto_totem",
    "name": "Auto Totem",
    "category": "PvP",
    "available": true,
    "note": "Immediately equip an inventory totem whenever the offhand has none, at any health. Refills on inventory updates and at the start of each tick.",
    "status": "Server-visible inventory"
  },
  {
    "id": "killaura",
    "name": "Kill Aura",
    "category": "PvP",
    "available": true,
    "note": "Automatically attack nearby visible enemies in any direction while standing still. Uses normal weapon reach, friend filters and your selected attack timing.",
    "status": "Server-visible combat"
  },
  {
    "id": "auto_crystal",
    "name": "Auto Crystal",
    "category": "PvP",
    "available": true,
    "note": "Follows your right-click crystal placement with a real attack after server spawn. Manual self-damage checks are optional; automatic mode uses damage limits.",
    "status": "Server-visible crystal actions"
  },
  {
    "id": "auto_anchor",
    "name": "Auto Anchor",
    "category": "PvP",
    "available": true,
    "note": "Follows your anchor placement, finds inventory glowstone, charges, adds optional cover and detonates after server updates.",
    "status": "Server-visible anchor actions"
  },
  {
    "id": "auto_gapple",
    "name": "Auto Gapple",
    "category": "PvP",
    "available": true,
    "note": "Use a golden apple when the configured health threshold is reached and an apple is available. Item use is visible to the server.",
    "status": "Server-visible item use"
  },
  {
    "id": "trigger_bot",
    "name": "Trigger Bot",
    "category": "PvP",
    "available": true,
    "note": "Attack an eligible target under your crosshair using normal game attacks and cooldown. Shared target and friend settings apply.",
    "status": "Server-visible combat"
  },
  {
    "id": "auto_weapon",
    "name": "Auto Weapon",
    "category": "PvP",
    "available": true,
    "note": "Select an available hotbar weapon for eligible nearby targets. Shared target and friend settings apply; inventory selection is server-visible.",
    "status": "Server-visible inventory"
  },
  {
    "id": "aim_cursor",
    "name": "Automatic Aim",
    "category": "PvP",
    "available": true,
    "note": "Turns your view toward the nearest eligible visible player within the separate Aim viewing range (3–256 blocks). Attack reach is unchanged.",
    "status": "Server-visible combat"
  },
  {
    "id": "auto_punch",
    "name": "Auto Punch",
    "category": "PvP",
    "available": true,
    "note": "Attacks an eligible player under the crosshair using normal reach and your selected fast or cooldown timing. Works with Aim Cursor; shared friend settings apply.",
    "status": "Server-visible combat"
  },
  {
    "id": "auto_kill_steal",
    "name": "Auto Kill Steal",
    "category": "PvP",
    "available": true,
    "note": "Prioritizes eligible visible players at low received health for a normal attack. A finishing hit is never guaranteed; normal reach and cooldown apply.",
    "status": "Server-visible combat"
  },
  {
    "id": "player_info",
    "name": "Player Info",
    "category": "PvP",
    "available": true,
    "note": "Compact hearts, visible held-totem icons and observed totem uses above received players. Change label size in Player Controls. Remaining inventory totals are unknown.",
    "status": "Received player data"
  },
  {
    "id": "flight",
    "name": "Flight",
    "category": "Movement",
    "available": true,
    "note": "Move your actual body with WASD / Space / Shift. Singleplayer grants temporary flight; multiplayer requires server-granted permission.",
    "status": "High anti-cheat exposure \u00b7 timing unverified"
  },
  {
    "id": "noclip",
    "name": "NoClip",
    "category": "Movement",
    "available": true,
    "note": "Move your body through blocks in spectator mode. Singleplayer temporarily changes mode and restores it afterward; multiplayer requires spectator permission.",
    "status": "High anti-cheat exposure \u00b7 timing unverified"
  },
  {
    "id": "boat_fly",
    "name": "Boat Fly",
    "category": "Movement",
    "available": true,
    "note": "Move a controlled boat with WASD / Space / Shift in singleplayer; real vehicle movement is applied on the integrated server.",
    "status": "High anti-cheat exposure \u00b7 timing unverified"
  },
  {
    "id": "fast_swim",
    "name": "Fast Swim",
    "category": "Movement",
    "available": true,
    "note": "Boost actual directional swimming while in water. Multiplayer movement remains server-authoritative.",
    "status": "High anti-cheat exposure \u00b7 timing unverified"
  },
  {
    "id": "super_trident",
    "name": "Super Trident",
    "category": "Movement",
    "available": true,
    "note": "Charge and release your Riptide trident yourself. Super Trident boosts the launch velocity ×3 once per release.",
    "status": "High anti-cheat exposure \u00b7 timing unverified"
  },
  {
    "id": "deepslate_bypass",
    "name": "Deepslate Bypass",
    "category": "Utility",
    "available": false,
    "note": "Unavailable. Server-side hidden block data cannot be reconstructed by this app.",
    "status": "Hidden server data \u00b7 detection unknown"
  },
  {
    "id": "donut_flight_bypass",
    "name": "Donut Flight Bypass",
    "category": "Movement",
    "available": false,
    "note": "Unavailable. No anti-cheat bypass is implemented; detection timing is unverified.",
    "status": "High anti-cheat exposure \u00b7 timing unverified"
  },
  {
    "id": "anti_kick",
    "name": "Anti Kick",
    "category": "Utility",
    "available": false,
    "note": "Unavailable. A client cannot prevent an authoritative server kick.",
    "status": "Server control \u00b7 timing unverified"
  },
  {
    "id": "auction_sniper",
    "name": "Auction Sniper",
    "category": "Utility",
    "available": false,
    "note": "Needs a server-specific auction adapter. No automatic purchases are implemented.",
    "status": "Server adapter \u00b7 timing unverified"
  }
];
