package dev.amethyst.finder.ui;

import dev.amethyst.finder.core.ChunkAnalysis;
import dev.amethyst.finder.core.ChunkEvidence;
import java.util.*;

/** Shared pure observation policy. Player results contain only the supplied current client snapshot. */
public final class ChunkVisualRules {
    public record ObservedPlayer(int id,double x,double y,double z,boolean observable) {}
    private ChunkVisualRules() {}
    public static Set<ChunkSelection.Category> categories(ChunkAnalysis analysis,Set<ChunkSelection.Category> enabled,
                                                         boolean showHistory,boolean growthEnabled) {
        return categories(analysis,enabled,showHistory,growthEnabled,1);
    }
    public static Set<ChunkSelection.Category> categories(ChunkAnalysis analysis,Set<ChunkSelection.Category> enabled,
                                                         boolean showHistory,boolean growthEnabled,int matureMinimum) {
        if(analysis.status()==ChunkAnalysis.Status.HISTORICAL&&!showHistory)return Set.of();
        var result=EnumSet.noneOf(ChunkSelection.Category.class);
        if(enabled.contains(ChunkSelection.Category.CHEST)&&analysis.storageCount()>0)result.add(ChunkSelection.Category.CHEST);
        if((enabled.contains(ChunkSelection.Category.AMETHYST)||growthEnabled)&&analysis.amethystCount()>0)result.add(ChunkSelection.Category.AMETHYST);
        if(enabled.contains(ChunkSelection.Category.BASE)&&analysis.evidence()!=ChunkAnalysis.StructureEvidence.NONE)result.add(ChunkSelection.Category.BASE);
        if(enabled.contains(ChunkSelection.Category.SPAWNER)&&analysis.spawnerCount()>0)result.add(ChunkSelection.Category.SPAWNER);
        if(enabled.contains(ChunkSelection.Category.SEARCH)&&analysis.status()==ChunkAnalysis.Status.LIVE
                &&analysis.count(dev.amethyst.finder.core.Signal.CLUSTER)>=Math.max(1,matureMinimum))result.add(ChunkSelection.Category.SEARCH);
        // Player information deliberately never comes from persistent block observations.
        return Set.copyOf(result);
    }
    public static Map<Long,Integer> currentPlayerChunks(Collection<ObservedPlayer> current,double viewerX,double viewerY,double viewerZ,double range) {
        if(!finite(viewerX,viewerY,viewerZ,range)||range<=0)return Map.of();
        Map<Integer,ObservedPlayer> unique=new HashMap<>();
        for(ObservedPlayer player:current)if(player!=null)unique.put(player.id(),player);
        Map<Long,Integer> chunks=new TreeMap<>();
        for(ObservedPlayer player:unique.values()) {
            if(!player.observable()||!finite(player.x(),player.y(),player.z()))continue;
            double dx=player.x()-viewerX,dy=player.y()-viewerY,dz=player.z()-viewerZ;
            if(dx*dx+dy*dy+dz*dz>range*range)continue;
            if(player.x()<Integer.MIN_VALUE||player.x()>=Integer.MAX_VALUE||player.z()<Integer.MIN_VALUE||player.z()>=Integer.MAX_VALUE)continue;
            int x=(int)Math.floor(player.x())>>4,z=(int)Math.floor(player.z())>>4;
            chunks.merge(ChunkEvidence.key(x,z),1,Integer::sum);
        }
        return Map.copyOf(chunks);
    }
    private static boolean finite(double... values){for(double value:values)if(!Double.isFinite(value))return false;return true;}
}
