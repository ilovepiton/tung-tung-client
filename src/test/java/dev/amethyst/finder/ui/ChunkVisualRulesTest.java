package dev.amethyst.finder.ui;

import dev.amethyst.finder.core.ChunkAnalysis;
import dev.amethyst.finder.core.ChunkEvidence;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkVisualRulesTest {
    private static ChunkAnalysis observed(ChunkAnalysis.Status status,int storage,int amethyst,int spawners,ChunkAnalysis.StructureEvidence evidence) {
        return new ChunkAnalysis(0,0,1000,status,Map.of(),storage,amethyst,spawners,evidence,"Observed test data",0,0,0,0,0,0,List.of(),false);
    }
    private static Set<ChunkSelection.Category> all(){return EnumSet.allOf(ChunkSelection.Category.class);}
    private static ChunkVisualRules.ObservedPlayer player(int id,double x,double z,boolean available){return new ChunkVisualRules.ObservedPlayer(id,x,64,z,available);}

    @Test void actualStorageCountProducesRedCategoryAndZeroCountRemovesIt() {
        var full=observed(ChunkAnalysis.Status.LIVE,14,0,0,ChunkAnalysis.StructureEvidence.NONE);
        var empty=observed(ChunkAnalysis.Status.LIVE,0,0,0,ChunkAnalysis.StructureEvidence.NONE);
        assertEquals(Set.of(ChunkSelection.Category.CHEST),ChunkVisualRules.categories(full,all(),false,false));
        assertTrue(ChunkVisualRules.categories(empty,all(),true,false).isEmpty());
        assertEquals(0xFFFF2D2D,ChunkSelection.Category.CHEST.color);
    }
    @Test void historicalCountsNeverImplyCurrentPlayerPositions() {
        var old=observed(ChunkAnalysis.Status.HISTORICAL,3,0,0,ChunkAnalysis.StructureEvidence.NONE);
        assertTrue(ChunkVisualRules.categories(old,all(),false,false).isEmpty());
        assertEquals(Set.of(ChunkSelection.Category.CHEST),ChunkVisualRules.categories(old,all(),true,false));
        assertFalse(ChunkVisualRules.categories(old,all(),true,false).contains(ChunkSelection.Category.PLAYER));
    }
    @Test void reverificationRetainsPendingObservationWithoutCallingItLive() {
        var pending=observed(ChunkAnalysis.Status.VERIFYING,1,2,1,ChunkAnalysis.StructureEvidence.WEAK);
        var categories=ChunkVisualRules.categories(pending,all(),false,false);
        assertEquals(Set.of(ChunkSelection.Category.CHEST,ChunkSelection.Category.AMETHYST,ChunkSelection.Category.BASE,ChunkSelection.Category.SPAWNER),categories);
        assertEquals(ChunkAnalysis.Status.VERIFYING,pending.status());
    }
    @Test void naturalSpawnerOrAmethystAloneDoesNotBecomeStructureEvidence() {
        var natural=observed(ChunkAnalysis.Status.LIVE,0,8,1,ChunkAnalysis.StructureEvidence.NONE);
        assertEquals(Set.of(ChunkSelection.Category.AMETHYST,ChunkSelection.Category.SPAWNER),ChunkVisualRules.categories(natural,all(),false,false));
    }
    @Test void disabledCategoriesStayDisabledAndIndependentGrowthStillShowsObservedAmethyst() {
        var observed=observed(ChunkAnalysis.Status.LIVE,3,8,1,ChunkAnalysis.StructureEvidence.STRONG);
        assertTrue(ChunkVisualRules.categories(observed,Set.of(),true,false).isEmpty());
        assertEquals(Set.of(ChunkSelection.Category.AMETHYST),ChunkVisualRules.categories(observed,Set.of(),false,true));
        assertEquals(0xFFB66CFF,ChunkSelection.Category.AMETHYST.color);
        assertEquals(0xFFFFB020,ChunkSelection.Category.BASE.color);
        assertEquals(0xFFFF55CC,ChunkSelection.Category.PLAYER.color);
        assertEquals(0xFF35C9FF,ChunkSelection.Category.SPAWNER.color);
    }
    @Test void movementRemovesOldChunkInNextCurrentSnapshot() {
        var old=ChunkVisualRules.currentPlayerChunks(List.of(player(1,8,8,true)),0,64,0,128);
        var now=ChunkVisualRules.currentPlayerChunks(List.of(player(1,20,8,true)),0,64,0,128);
        assertEquals(Map.of(ChunkEvidence.key(0,0),1),old);
        assertEquals(Map.of(ChunkEvidence.key(1,0),1),now);
        assertFalse(now.containsKey(ChunkEvidence.key(0,0)));
    }
    @Test void unavailableOrUnloadedPlayerDoesNotLeaveLiveLocationOrHistoryTrail() {
        assertEquals(1,ChunkVisualRules.currentPlayerChunks(List.of(player(1,8,8,true)),0,64,0,128).size());
        assertTrue(ChunkVisualRules.currentPlayerChunks(List.of(player(1,8,8,false)),0,64,0,128).isEmpty());
        assertTrue(ChunkVisualRules.currentPlayerChunks(List.of(),0,64,0,128).isEmpty());
    }
    @Test void playersShareOneCurrentChunkCountAndDuplicatesDoNotInflateIt() {
        var result=ChunkVisualRules.currentPlayerChunks(List.of(player(1,-.1,-.1,true),player(2,-15,-1,true),player(1,-.1,-.1,true)),0,64,0,128);
        assertEquals(Map.of(ChunkEvidence.key(-1,-1),2),result);
        assertThrows(UnsupportedOperationException.class,()->result.put(0L,9));
    }
    @Test void outOfRangeAndInvalidPlayerPositionsAreOmitted() {
        var current=List.of(player(1,129,0,true),player(2,Double.NaN,0,true),player(3,1,1,true));
        assertEquals(Map.of(ChunkEvidence.key(0,0),1),ChunkVisualRules.currentPlayerChunks(current,0,64,0,128));
        assertTrue(ChunkVisualRules.currentPlayerChunks(current,Double.NaN,64,0,128).isEmpty());
    }
}
