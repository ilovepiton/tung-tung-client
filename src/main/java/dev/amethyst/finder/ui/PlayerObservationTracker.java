package dev.amethyst.finder.ui;

import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/** Current-world observations from normal entity state and authoritative entity events. */
public final class PlayerObservationTracker {
    private static final TotemPopLedger POPS = new TotemPopLedger();
    private static Object level, connection;
    private static long tick;
    private static final java.util.concurrent.ConcurrentMap<java.util.UUID,Integer> INVENTORY=new java.util.concurrent.ConcurrentHashMap<>();

    private PlayerObservationTracker() {}

    public static void tick(Minecraft client) {
        if (!enabled() || client.level == null || client.player == null) { reset(); return; }
        if (level != client.level || connection != client.getConnection()) {
            reset(); level = client.level; connection = client.getConnection();
        }
        tick++;
        var server=client.getSingleplayerServer();
        if(server!=null&&tick%10==0)server.execute(()->{
            var counts=new java.util.HashMap<java.util.UUID,Integer>();
            for(var p:server.getPlayerList().getPlayers()){
                int count=p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)?p.getOffhandItem().getCount():0;
                for(int slot=0;slot<36;slot++){var item=p.getInventory().getItem(slot);if(item.is(Items.TOTEM_OF_UNDYING))count+=item.getCount();}
                counts.put(p.getUUID(),count);
            }
            if(Minecraft.getInstance().getSingleplayerServer()==server){INVENTORY.clear();INVENTORY.putAll(counts);}
        });
        POPS.prune(tick);
        for (var player : client.level.players())
            if (player != client.player && player.isAlive() && !player.isRemoved()) POPS.seen(player.getUUID(), tick);
    }

    public static void entityEvent(ClientPacketListener source, ClientboundEntityEventPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (!enabled() || client.level == null || client.player == null || source != client.getConnection()
                || level != client.level || connection != source || packet.getEventId() != EntityEvent.PROTECTED_FROM_DEATH) return;
        if (packet.getEntity(client.level) instanceof Player player && player != client.player && !player.isRemoved())
            POPS.popped(player.getUUID(), tick);
    }

    public static Integer inventoryTotems(Player player){return INVENTORY.get(player.getUUID());}

    public static int pops(Player player) { return POPS.pops(player.getUUID()); }

    public static int visibleHeldTotems(Player player) {
        return (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) ? player.getMainHandItem().getCount() : 0)
                + (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING) ? player.getOffhandItem().getCount() : 0);
    }

    public static void reset() { POPS.clear();INVENTORY.clear(); level = null; connection = null; tick = 0; }
    private static boolean enabled() { return ChunkFinderClient.CONFIG != null && ChunkFinderClient.CONFIG.isModuleEnabled("player_info"); }
}
