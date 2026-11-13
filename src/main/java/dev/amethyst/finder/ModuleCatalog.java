package dev.amethyst.finder;

import dev.amethyst.finder.modules.ClientModules;
import dev.amethyst.finder.modules.PvpModules;
import dev.amethyst.finder.modules.MovementModules;
import java.util.List;
import java.util.Set;

public final class ModuleCatalog {
    public record Entry(String id, String name, String category, String note) {}
    private static final Set<String> VISUALS = Set.of("invis_mode", "client_maps", "map_in_slot", "chunk_finder", "storage_esp", "player_esp", "player_info", "amethyst_esp", "player_chunks", "base_chunks", "spawner_chunks", "xray", "night_vision", "diamond_esp", "ore_esp", "lava_esp");
    private static final Set<String> MOVEMENT = Set.of("flight", "noclip", "boat_fly", "fast_swim", "super_trident", "donut_flight_bypass");
    public static final List<Entry> ENTRIES = List.of(
            new Entry("cave_detector", "Auto Cave Detector", "Finder", "Green full-height chunks with no detected underground air, water or lava cavities. Checks all 256 columns, from the world floor + 5 to five blocks below the received opaque surface. Only completed current scans count; hidden server terrain remains unknown."),
            new Entry("search_chunk", "Sus Chunk", "Finder", "Red full-height columns for currently scanned chunks with enough mature amethyst clusters. A search hint, not proof of a base or another player. Natural growth and your own simulation can produce clusters."),
            new Entry("auto_relog", "Auto Relog", "Utility", "Arms one reconnect to the same server after one second at or below the chosen body Y level. Shows a cancellable countdown. No camera, item use, movement or retry loop."),
            new Entry("auto_sell", "Auto Sell", "Utility", "Sends /sell when selected plain farm resources form full stacks. Deposits one acknowledged stack at a time into a recognized Sell menu, then clicks its named green Confirm/Sell glass button. Partial, named and enchanted stacks stay untouched. Re-arm after reconnecting."),
            new Entry("auto_logout", "Nearby Player Logout", "Utility", "Disconnects if any received non-spectator player, including a friend, comes within the chosen body distance. Turns itself and Auto Sell off; never reconnects automatically."),
            new Entry("client_maps", "Client Maps", "Visuals", "Stores map pixels received from each server and restores them when that map is no longer being held. Data stays in this Minecraft profile."),
            new Entry("map_in_slot", "Map in Slot", "Visuals", "Renders the real received map image inside filled-map inventory slots. Toggle it without removing the mod."),
            new Entry("item_finder", "Item Finder", "Finder", "Highlights 50 selected valuable dropped item types with names, counts, distance and current chunk outlines. Received entities only; pickup and despawn clear the markers."),
            new Entry("elytra_finder", "Elytra Finder", "Finder", "Nearest received Elytra in an item frame or as a dropped item, with a walking/bridging compass and persistent nearby-player warning. Only currently received entities; no distant ship prediction."),
            new Entry("mace_bot", "Mace PvP", "PvP", "Enchantment-aware mace / axe / sword coordinator with deliberate wind charges, shield defense, pearls and emergency totems. Uses real movement, normal reach and observed server results. No Elytra; assign a key in settings."),
            new Entry("auto_vault", "Trial Chambers", "Utility", "One aimed vault opening when the selected preview appears. Automatically equips its normal or ominous key. Preview timing does not change random server rewards. Arms once and stops after one request."),
            new Entry("chunk_finder", "Chunk Finder", "Finder", "Choose 12 base features, including regular/trial spawners and armor stands. Solid type-color models and view-center tracers appear inside Minecraft. J opens full-height chunk category controls; raid history and loot contents remain unknown."),
            new Entry("storage_esp", "Storage ESP", "Visuals", "Solid type-colored storage models: orange chests and distinct container colors, with optional view-center tracers."),
            new Entry("player_esp", "Player Direction", "Visuals", "Orange cursor-to-player direction arrows without model fills. Color and received-player range are adjustable in Player Controls; armor stays visible."),
            new Entry("player_info", "Player Info", "PvP", "Compact colored hearts and visible totem icons above received players, with observed uses. Label size is adjustable; hidden remaining inventory stays unknown."),
            new Entry("amethyst_esp", "Amethyst ESP", "Visuals", "Solid purple model highlights for amethyst, buds and clusters, retaining transparent cutouts."),
            new Entry("diamond_esp", "Highlight Diamonds", "Visuals", "Cyan model highlights for diamond and deepslate diamond ore in received chunks. O opens in-game highlight controls."),
            new Entry("ore_esp", "Highlight Ores", "Visuals", "Choose individual useful minerals in Resource highlights, including emerald, lapis, iron, quartz, ancient debris, amethyst and sulfur."),
            new Entry("lava_esp", "Highlight Lava", "Visuals", "Amber fluid-surface highlights for received lava. Scans and markers have bounded budgets."),
            new Entry("xray", "Shadow Byte X-ray", "Visuals", "Hides common terrain and fluids. Exposed-ore filtering reduces buried decoys using received neighboring blocks; server-hidden ores remain unknown."),
            new Entry("night_vision", "Night Vision", "Visuals", "Full-bright caves and nights with darkness dimming removed. Keeps all terrain visible; changes local lighting only. Disable to restore normal lighting."),
            new Entry("invis_mode", "Invis Mode", "Visuals", "Reveals the avatars of received invisible players and adds a red Invisible header above Player Info. Their real invisibility effect and equipment are preserved."),
            new Entry("freecam", "Freecam", "Camera", "Independent camera with adjustable speed and sprint boost. Your body remains in the world and can take damage."),
            new Entry("freelook", "Freelook", "Camera", "Independent third-person view without turning your body."),
            new Entry("combat_bot", "Combat Bot", "PvP", "One-fight Crystal / Anchor coordinator with sword fallback, Auto Totem, normal movement and optional ender pearls. Press P to arm; friends are always excluded. Stops on confirmed death, not totem pops."),
            new Entry("auto_totem", "Auto Totem", "PvP", "Immediately equips a totem from inventory whenever the offhand has none, at any health. Refills on received inventory updates and at the start of each tick; preserves displaced items."),
            new Entry("aim_cursor", "Automatic Aim", "PvP", "Aims toward the nearest eligible visible player within a separate 3–256 block viewing range. Friends are excluded; attack reach is unchanged."),
            new Entry("auto_punch", "Auto Punch", "PvP", "Attacks an eligible player under the actual crosshair at normal reach with fast per-tick or full-cooldown attacks. Can be combined with Aim Cursor."),
            new Entry("auto_kill_steal", "Auto Kill Steal", "PvP", "Prioritizes an eligible low-health player within normal visible attack reach. No kill or reward is guaranteed."),
            new Entry("auto_crystal", "Auto Crystal", "PvP", "Follows your placed crystal with a normal attack after server spawn; optional target-based automation and blast limits."),
            new Entry("auto_anchor", "Auto Anchor", "PvP", "Follows your anchor placement, finds inventory glowstone, charges, adds optional cover and detonates after server updates."),
            new Entry("auto_gapple", "Auto Gapple", "PvP", "Uses a hotbar golden apple below the health threshold, restoring owned slot and use-key state."),
            new Entry("trigger_bot", "TriggerBot", "PvP", "Attacks the eligible entity under the crosshair at normal reach with selectable fast or full-cooldown attacks."),
            new Entry("auto_weapon", "Auto Weapon", "PvP", "Selects a hotbar sword or axe for an eligible crosshair target; does not attack by itself."),
            new Entry("killaura", "Kill Aura", "PvP", "Automatically attacks nearby eligible visible players or hostile mobs in any direction, while you may stand still. Uses normal weapon reach, friend filters and selected attack timing."),
            new Entry("player_chunks", "Player Chunks", "Discovery", "Chunk boundaries around currently loaded players."),
            new Entry("base_chunks", "Base Chunks", "Discovery", "Chunk outlines for detected build signals. A heuristic, not proof of a base."),
            new Entry("player_detection", "Player Detection", "Discovery", "Alerts for players received from the server into the client world."),
            new Entry("spawner_chunks", "Spawner Chunks", "Discovery", "Solid cyan models and matching chunk boundaries for received regular/trial spawners."),
            new Entry("flight", "Flight", "Movement", "Moves the player's actual body: WASD / Space / Shift. Uses server-granted flight; singleplayer temporarily grants and restores flight abilities."),
            new Entry("noclip", "NoClip", "Movement", "Moves the actual player through blocks in spectator mode. Singleplayer temporarily uses spectator and restores the original mode; multiplayer requires server-granted spectator."),
            new Entry("boat_fly", "Boat Fly", "Movement", "Singleplayer boat movement: WASD, Space up, sprint key (Ctrl default) down. Shift dismounts normally. Multiplayer needs server-side support."),
            new Entry("fast_swim", "Fast Swim", "Movement", "On-foot water movement: WASD / Space / Shift with a 0.32-block/tick speed floor. Preserves stronger momentum, item use and Riptide; remote servers can correct movement."),
            new Entry("super_trident", "Super Trident", "Movement", "Normal automatic Riptide charge/release with a usable enchanted trident while wet. Manual item use and active inventory transactions take priority."),
            new Entry("anti_kick", "Anti Kick", "Server", "Connection handling is controlled by the server. No supported bypass is implemented."),
            new Entry("deepslate_bypass", "Deepslate Bypass", "Server", "Blocks hidden by server Anti-Xray are unknown to the client. No bypass is implemented."),
            new Entry("donut_flight_bypass", "Donut Flight Bypass", "Server", "No verified Donut SMP flight bypass is implemented."),
            new Entry("auction_sniper", "Auction Sniper", "Server", "Requires an adapter for the auction plugin and its price format. Not implemented.")
    );
    private ModuleCatalog() {}
    public static boolean available(String id) { return "cave_detector".equals(id)||"search_chunk".equals(id)||"auto_relog".equals(id)||"item_finder".equals(id) || "elytra_finder".equals(id) || "auto_vault".equals(id) || MapArtModules.available(id) || VISUALS.contains(id) && !Set.of("client_maps","map_in_slot").contains(id) || ClientModules.capability(id) || PvpModules.capability(id) || MovementModules.capability(id); }
    public static Entry find(String id) { return ENTRIES.stream().filter(e -> e.id.equals(id)).findFirst().orElse(null); }

    /** Technical observability labels; these do not promise any particular anti-cheat outcome. */
    public static String detectionStatus(String id) {
        if (VISUALS.contains(id)) return "Client-side visuals · detection unknown";
        if (MOVEMENT.contains(id)) return "Server-authoritative movement";
        return switch (id) {
            case "freecam", "freelook" -> "Local camera · detection unknown";
            case "player_detection" -> "Client-side alerts · detection unknown";
            case "auto_totem", "auto_weapon", "auto_gapple" -> "Server-visible inventory and item use";
            case "aim_cursor" -> "Normal player aim · received targets";
            case "killaura", "trigger_bot", "auto_crystal", "auto_anchor", "auto_punch", "auto_kill_steal" -> "Server-visible combat and interactions";
            case "anti_kick" -> "Server-controlled connection · unverified";
            case "deepslate_bypass" -> "Server-hidden blocks · no verified bypass";
            case "auction_sniper" -> "Server-visible transactions · unverified";
            default -> "Detection unknown";
        };
    }
}
