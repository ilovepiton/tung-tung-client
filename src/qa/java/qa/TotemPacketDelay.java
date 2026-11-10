package qa;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayDeque;

/** QA-only ordered queue: deliver pop events immediately but hold inventory echoes. */
public final class TotemPacketDelay {
    private static ServerCommonPacketListenerImpl target;
    private static final ArrayDeque<Packet<?>> queue=new ArrayDeque<>();
    private static long deadline;
    private static boolean flushing;
    public static int heldPackets;
    public static void arm(ServerPlayer player,int milliseconds){
        if(milliseconds<=0)return;
        if(!queue.isEmpty())throw new IllegalStateException("Previous QA delay has not drained");
        target=player.connection;deadline=System.nanoTime()+milliseconds*1_000_000L;
    }
    public static boolean hold(ServerCommonPacketListenerImpl listener,Packet<?> packet){
        if(listener!=target||flushing)return false;
        if(!(packet instanceof ClientboundContainerSetSlotPacket||packet instanceof ClientboundContainerSetContentPacket
                ||packet instanceof ClientboundSetPlayerInventoryPacket||packet instanceof ClientboundSetEquipmentPacket))return false;
        if(System.nanoTime()>=deadline&&queue.isEmpty())return false;
        if(queue.size()>=256)throw new IllegalStateException("QA inventory queue overflow");
        queue.addLast(packet);heldPackets++;return true;
    }
    public static void flush(){
        if(target==null||System.nanoTime()<deadline)return;
        flushing=true;
        try{while(!queue.isEmpty())target.send(queue.removeFirst());}
        finally{flushing=false;target=null;}
    }
}
