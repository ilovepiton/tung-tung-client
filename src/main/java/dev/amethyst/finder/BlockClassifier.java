package dev.amethyst.finder;

import dev.amethyst.finder.core.Signal;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;

public final class BlockClassifier {
    private static final Map<Block, Signal> BLOCKS = new IdentityHashMap<>();
    static {
        add(Signal.CHEST, Blocks.CHEST, Blocks.TRAPPED_CHEST);
        add(Signal.BARREL, Blocks.BARREL);
        add(Signal.ENDER_CHEST, Blocks.ENDER_CHEST);
        add(Signal.HOPPER, Blocks.HOPPER);
        add(Signal.FURNACE, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER);
        add(Signal.DISPENSER, Blocks.DISPENSER);
        add(Signal.DROPPER, Blocks.DROPPER);
        add(Signal.CRAFTING, Blocks.CRAFTING_TABLE);
        add(Signal.ANVIL, Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL);
        add(Signal.ENCHANTING, Blocks.ENCHANTING_TABLE);
        add(Signal.BEACON, Blocks.BEACON);
        add(Signal.REDSTONE, Blocks.REDSTONE_WIRE, Blocks.REPEATER, Blocks.COMPARATOR,
                Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.OBSERVER);
        add(Signal.TORCH, Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH);
        add(Signal.PORTAL, Blocks.NETHER_PORTAL);
        add(Signal.SPAWNER, Blocks.SPAWNER, Blocks.TRIAL_SPAWNER);
        add(Signal.AMETHYST, Blocks.AMETHYST_BLOCK);
        add(Signal.BUDDING, Blocks.BUDDING_AMETHYST);
        add(Signal.BUD, Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.LARGE_AMETHYST_BUD);
        add(Signal.CLUSTER, Blocks.AMETHYST_CLUSTER);
        // These also occur in generated structures. Their arrangements are observations, never proof of a player base.
        add(Signal.CONSTRUCTION, Blocks.BRICKS, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS,
                Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS, Blocks.NETHER_BRICKS,
                Blocks.RED_NETHER_BRICKS, Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_BRICKS, Blocks.SMOOTH_QUARTZ,
                Blocks.SMOOTH_STONE, Blocks.GLASS, Blocks.GLASS_PANE, Blocks.OAK_PLANKS,
                Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS,
                Blocks.DARK_OAK_PLANKS, Blocks.MANGROVE_PLANKS, Blocks.CHERRY_PLANKS,
                Blocks.BAMBOO_PLANKS, Blocks.CRIMSON_PLANKS, Blocks.WARPED_PLANKS);
    }
    private static void add(Signal s, Block... blocks) { for (Block b : blocks) BLOCKS.put(b, s); }
    public static Signal classify(BlockState state) {
        Block block = state.getBlock();
        Signal cached = BLOCKS.get(block);
        if (cached != null) return cached;
        if (block instanceof ShulkerBoxBlock) return Signal.SHULKER;
        if (block instanceof BedBlock) return Signal.BED;
        return null;
    }
    public static boolean relevant(BlockState state) { return classify(state) != null; }
    public static int growthStage(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.SMALL_AMETHYST_BUD) return 1;
        if (block == Blocks.MEDIUM_AMETHYST_BUD) return 2;
        if (block == Blocks.LARGE_AMETHYST_BUD) return 3;
        return block == Blocks.AMETHYST_CLUSTER ? 4 : 0;
    }
}
