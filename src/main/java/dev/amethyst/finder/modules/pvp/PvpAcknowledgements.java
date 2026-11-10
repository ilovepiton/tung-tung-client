package dev.amethyst.finder.modules.pvp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;

/** Bounded observations of server packets, separate from vanilla client prediction. */
public final class PvpAcknowledgements {
    private static ClientPacketListener connection;
    private static ClientLevel level;
    private static LocalPlayer player;
    private static int acknowledgedBlockSequence = -1;
    private static final long[] inventoryRevisions = new long[41];
    private static final ItemStack[] serverInventory = new ItemStack[41];
    private static Method predictionGetter;
    private static final LinkedHashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState> blocks=new LinkedHashMap<>();
    private static long removalRevision;
    private static long offhandTotemRevision;
    private static final LinkedHashMap<Integer, Long> removedEntities = new LinkedHashMap<>();

    private PvpAcknowledgements() {}

    private static void session(Minecraft client) {
        if (connection != client.getConnection() || level != client.level || player != client.player) {
            connection = client.getConnection(); level = client.level; player = client.player;
            acknowledgedBlockSequence = -1;blocks.clear();
            java.util.Arrays.fill(inventoryRevisions, 0);
            java.util.Arrays.fill(serverInventory, null);
            removalRevision = offhandTotemRevision = 0; removedEntities.clear();
        }
    }

    public static int predictionSequence(Minecraft client) throws ReflectiveOperationException {
        session(client);
        if (predictionGetter == null) {
            predictionGetter = ClientLevel.class.getDeclaredMethod("getBlockStatePredictionHandler");
            predictionGetter.setAccessible(true);
        }
        return ((BlockStatePredictionHandler) predictionGetter.invoke(client.level)).currentSequence();
    }

    public static boolean blockAcknowledged(Minecraft client, int sequence) {
        session(client);
        return sequence >= 0 && acknowledgedBlockSequence >= sequence;
    }

    public static void blockAck(ClientPacketListener source, int sequence) {
        Minecraft client = Minecraft.getInstance(); session(client);
        if (source == connection) acknowledgedBlockSequence = Math.max(acknowledgedBlockSequence, sequence);
    }

    public static void serverBlockUpdate(ClientPacketListener source,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state) {
        Minecraft c=Minecraft.getInstance();session(c);if(source!=connection)return;
        blocks.remove(pos);blocks.put(pos.immutable(),state);while(blocks.size()>4096)blocks.remove(blocks.keySet().iterator().next());
    }
    public static boolean serverBlockKnown(Minecraft c,net.minecraft.core.BlockPos pos){session(c);return blocks.containsKey(pos);}
    public static net.minecraft.world.level.block.state.BlockState serverBlock(Minecraft c,net.minecraft.core.BlockPos pos){session(c);return blocks.getOrDefault(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());}
    public static void inventorySlot(ClientPacketListener source, int slot, ItemStack stack) {
        Minecraft client = Minecraft.getInstance(); session(client);
        if (source != connection || slot < 0 || slot >= serverInventory.length) return;
        serverInventory[slot] = stack.copy(); inventoryRevisions[slot]++;
        if (slot == 40 && stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) offhandTotemRevision = inventoryRevisions[slot];
    }

    public static int inventorySlotForMenu(int slot) {
        if (slot >= 36 && slot <= 44) return slot - 36;
        if (slot >= 9 && slot <= 35) return slot;
        if (slot >= 5 && slot <= 8) return 44 - slot;
        return slot == 45 ? 40 : -1;
    }

    public static long inventoryRevision(Minecraft client, int slot) {
        session(client);
        return slot >= 0 && slot < inventoryRevisions.length ? inventoryRevisions[slot] : -1;
    }

    public static ItemStack inventorySnapshot(Minecraft client, int slot) {
        session(client);
        return slot >= 0 && slot < serverInventory.length && serverInventory[slot] != null
                ? serverInventory[slot].copy() : ItemStack.EMPTY;
    }

    public static long offhandTotemRevision(Minecraft client) { session(client); return offhandTotemRevision; }

    public static void entityRemoved(ClientPacketListener source, int id) {
        Minecraft client = Minecraft.getInstance(); session(client);
        if (source != connection) return;
        removedEntities.remove(id); removedEntities.put(id, ++removalRevision);
        while (removedEntities.size() > 256) removedEntities.remove(removedEntities.keySet().iterator().next());
    }

    public static long entityRemovalRevision(Minecraft client, int id) {
        session(client);
        return removedEntities.getOrDefault(id, 0L);
    }
}
