const MODULES = [
  {
    "id": "chunk_finder",
    "name": "Chunk Finder",
    "category": "Finder",
    "available": true,
    "note": "Twelve received signal types. Solid-color models: orange chests. Matching chunks use one color band per type. View-center tracers; amethyst stays separate.",
    "status": "Client visuals · detection unknown"
  },
  {
    "id": "storage_esp",
    "name": "Storage ESP",
    "category": "Visual",
    "available": true,
    "note": "Solid-color highlights for received container models; chests are orange. Optional tracers start at the view center.",
    "status": "Client visuals · detection unknown"
  },
  {
    "id": "amethyst_esp",
    "name": "Amethyst ESP",
    "category": "Visual",
    "available": true,
    "note": "Purple solid-color highlights for received budding amethyst and crystals. Budding grows via random ticks; existing crystals do not prove player presence.",
    "status": "Client visuals · detection unknown"
  },
  {
    "id": "xray",
    "name": "Shadow Byte X-ray",
    "category": "Visual",
    "available": true,
    "note": "Hides common terrain, water and lava from received chunks. Vanilla Fabric rendering only; server-hidden blocks remain unknown.",
    "status": "Client visuals · detection unknown"
  },
  {
    "id": "player_esp",
    "name": "Player ESP",
    "category": "Visual",
    "available": true,
    "note": "Solid-color highlights for players already received by your client, with optional tracers from the view center. Normal server tracking range still applies.",
    "status": "Client visuals · detection unknown"
  },
  {
    "id": "freelook",
    "name": "Freelook",
    "category": "Camera",
    "available": true,
    "note": "Look around independently of your movement direction. The local camera does not change your player's aim.",
    "status": "Local camera · detection unknown"
  },
  {
    "id": "freecam",
    "name": "Freecam",
    "category": "Camera",
    "available": true,
    "note": "Move the local camera independently of the player. The body remains vulnerable and only server-loaded chunks are available.",
    "status": "Local camera · detection unknown"
  },
  {
    "id": "base_chunks",
    "name": "Base Chunks",
    "category": "Finder",
    "available": true,
    "note": "Optional chunk boundaries for stronger activity candidates. A chunk marker is evidence, rather than proof of a player base.",
    "status": "Client visuals · detection unknown"
  },
  {
    "id": "player_chunks",
    "name": "Player Chunks",
    "category": "Finder",
    "available": true,
    "note": "Optional chunk boundaries around players received from the server. Use Player ESP for model highlights.",
    "status": "Client visuals · detection unknown"
  },
  {
    "id": "spawner_chunks",
    "name": "Spawner Chunks",
    "category": "Finder",
    "available": true,
    "note": "Optional chunk boundaries for received normal and trial spawners. Spawners can be naturally generated.",
    "status": "Client visuals · detection unknown"
  },
  {
    "id": "player_detection",
    "name": "Player Detection",
    "category": "Finder",
    "available": true,
    "note": "Show tracked players in the loaded world.",
    "status": "Client visuals · detection unknown"
  },
  {
    "id": "auto_totem",
    "name": "Auto Totem",
    "category": "PvP",
    "available": true,
    "note": "Move a totem into the offhand when empty or health reaches your configured threshold. Inventory actions are visible to the server.",
    "status": "Server-visible inventory"
  },
  {
    "id": "killaura",
    "name": "Kill Aura",
    "category": "PvP",
    "available": true,
    "note": "Attack visible hostile mobs within 3 blocks with full game cooldown. The legacy hostile-mob targeting remains separate from shared PvP target settings.",
    "status": "Server-visible combat"
  },
  {
    "id": "auto_crystal",
    "name": "Auto Crystal",
    "category": "PvP",
    "available": true,
    "note": "Use end crystals around eligible nearby targets. Shared range, delay, target and estimated explosion-damage limits apply.",
    "status": "Server-visible crystal actions"
  },
  {
    "id": "auto_anchor",
    "name": "Auto Anchor",
    "category": "PvP",
    "available": true,
    "note": "Use charged respawn anchors around eligible nearby targets. Shared delay and estimated explosion-damage limits apply.",
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
    "id": "flight",
    "name": "Flight",
    "category": "Movement",
    "available": false,
    "note": "Needs a server-granted flight ability. A desktop app cannot grant flight on a remote server; detection timing is unverified.",
    "status": "High anti-cheat exposure · timing unverified"
  },
  {
    "id": "noclip",
    "name": "NoClip",
    "category": "Movement",
    "available": false,
    "note": "Needs server support. Client-side movement cannot change authoritative collisions; detection timing is unverified.",
    "status": "High anti-cheat exposure · timing unverified"
  },
  {
    "id": "boat_fly",
    "name": "Boat Fly",
    "category": "Movement",
    "available": false,
    "note": "Unavailable. No vehicle or anti-cheat bypass is included; detection timing is unverified.",
    "status": "High anti-cheat exposure · timing unverified"
  },
  {
    "id": "fast_swim",
    "name": "Fast Swim",
    "category": "Movement",
    "available": false,
    "note": "Unavailable. Swimming speed is governed by the game and server; detection timing is unverified.",
    "status": "High anti-cheat exposure · timing unverified"
  },
  {
    "id": "super_trident",
    "name": "Super Trident",
    "category": "Movement",
    "available": false,
    "note": "Unavailable. No modified trident movement is sent; detection timing is unverified.",
    "status": "High anti-cheat exposure · timing unverified"
  },
  {
    "id": "deepslate_bypass",
    "name": "Deepslate Bypass",
    "category": "Utility",
    "available": false,
    "note": "Unavailable. Server-side hidden block data cannot be reconstructed by this app.",
    "status": "Hidden server data · detection unknown"
  },
  {
    "id": "donut_flight_bypass",
    "name": "Donut Flight Bypass",
    "category": "Movement",
    "available": false,
    "note": "Unavailable. No anti-cheat bypass is implemented; detection timing is unverified.",
    "status": "High anti-cheat exposure · timing unverified"
  },
  {
    "id": "anti_kick",
    "name": "Anti Kick",
    "category": "Utility",
    "available": false,
    "note": "Unavailable. A client cannot prevent an authoritative server kick.",
    "status": "Server control · timing unverified"
  },
  {
    "id": "auction_sniper",
    "name": "Auction Sniper",
    "category": "Utility",
    "available": false,
    "note": "Needs a server-specific auction adapter. No automatic purchases are implemented.",
    "status": "Server adapter · timing unverified"
  }
];
