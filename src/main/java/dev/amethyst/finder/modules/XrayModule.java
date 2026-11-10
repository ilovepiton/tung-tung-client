package dev.amethyst.finder.modules;

import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.Set;

/** Changes client meshes only. World blocks, collisions and received chunk distance stay authoritative. */
public final class XrayModule {
    private static volatile boolean active;
    private static volatile boolean exposedOnly = true;
    private static volatile Object world;
    private static final ThreadLocal<BlockPos.MutableBlockPos> NEIGHBOR = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
    private static final Direction[] FACES = Direction.values();
    private static final Set<Block> ORES = Set.of(
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.ANCIENT_DEBRIS
    );
    private static final Set<Block> TERRAIN = Set.of(
            Blocks.STONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.SMOOTH_STONE,
            Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE,
            Blocks.GRANITE, Blocks.POLISHED_GRANITE, Blocks.DIORITE, Blocks.POLISHED_DIORITE,
            Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.TUFF, Blocks.POLISHED_TUFF,
            Blocks.CALCITE, Blocks.BASALT, Blocks.SMOOTH_BASALT, Blocks.POLISHED_BASALT,
            Blocks.BEDROCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.GRASS_BLOCK,
            Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MUD, Blocks.CLAY, Blocks.GRAVEL,
            Blocks.SAND, Blocks.RED_SAND, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE,
            Blocks.RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE,
            Blocks.NETHERRACK, Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.SOUL_SAND,
            Blocks.SOUL_SOIL, Blocks.END_STONE, Blocks.WATER, Blocks.LAVA,
            Blocks.SNOW, Blocks.SNOW_BLOCK, Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.POWDER_SNOW
    );
    private XrayModule() {}
    public static boolean active() { return active; }
    public static boolean hidden(BlockState state) { return TERRAIN.contains(state.getBlock()); }
    /** Reads the original received region, never the AIR used only by the mesh compiler. */
    public static BlockState meshState(RenderSectionRegion region, BlockPos pos, BlockState received) {
        if (!active) return received;
        if (hidden(received)) return Blocks.AIR.defaultBlockState();
        if (exposedOnly && ORES.contains(received.getBlock()) && !hasOpening(region, pos))
            return Blocks.AIR.defaultBlockState();
        return received;
    }
    private static boolean hasOpening(RenderSectionRegion region, BlockPos pos) {
        BlockPos.MutableBlockPos adjacent = NEIGHBOR.get();
        for (Direction face : FACES) {
            adjacent.set(pos.getX() + face.getStepX(), pos.getY() + face.getStepY(), pos.getZ() + face.getStepZ());
            // Region padding and the world boundary must not be mistaken for received cave air.
            if (adjacent.getY() < region.getMinY() || adjacent.getY() >= region.getMinY() + region.getHeight()) continue;
            if ((pos.getX() >> 4) != (adjacent.getX() >> 4) || (pos.getZ() >> 4) != (adjacent.getZ() >> 4)) {
                if (!(world instanceof ClientLevel receivedWorld)
                        || receivedWorld.getChunkSource().getChunk(adjacent.getX() >> 4, adjacent.getZ() >> 4, ChunkStatus.FULL, false) == null) continue;
            }
            BlockState neighbor = region.getBlockState(adjacent);
            if (neighbor.isAir() || !neighbor.getFluidState().isEmpty() || !neighbor.canOcclude()) return true;
        }
        return false;
    }
    public static String status() {
        if (!active) return "Disabled";
        return exposedOnly
                ? "Exposed ores only · buried decoys filtered · server-hidden blocks unknown"
                : "All received ores · buried blocks may be server decoys";
    }
    public static void tick(Minecraft client) {
        boolean next = ChunkFinderClient.CONFIG != null && client.level != null
                && ChunkFinderClient.CONFIG.isModuleEnabled("xray");
        boolean nextExposedOnly = ChunkFinderClient.CONFIG == null || ChunkFinderClient.CONFIG.xrayExposedOnly;
        if (next != active || world != client.level || nextExposedOnly != exposedOnly) {
            active = next;
            exposedOnly = nextExposedOnly;
            world = client.level;
            // Cancels/rebuilds terrain work on the client thread, including on disable and world change.
            if (client.level != null) client.levelExtractor.allChanged();
        }
    }
}
