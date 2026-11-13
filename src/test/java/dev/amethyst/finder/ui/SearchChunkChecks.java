package dev.amethyst.finder.ui;

import dev.amethyst.finder.core.*;
import dev.amethyst.finder.modules.RelogPolicy;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.*;

/** Pure geometry and lifecycle regression checks; no game or account required. */
public final class SearchChunkChecks {
    public static void main(String[] args) {
        var policy=new RelogPolicy();policy.arm();
        for(int i=0;i<19;i++)assert !policy.observe(-1,0,true);
        assert policy.observe(-1,0,true);
        for(int i=0;i<100;i++)assert !policy.observe(-20,0,true):"One arming must never loop";
        policy.arm();for(int i=0;i<19;i++)policy.observe(-1,0,true);
        assert !policy.observe(-1,0,false):"Menus and disconnected sessions reset the descent timer";
        assert !policy.observe(-1,0,true);
        policy.cancel();assert !policy.observe(-1,0,true);

        var builder=new ChunkEvidence.Builder(0,0);
        for(int y=-64;y<0;y++)for(int x=0;x<16;x++)builder.observe(Signal.CHEST,x,y,0);
        for(int y=0;y<64;y++)for(int x=0;x<16;x++)builder.observe(Signal.CHEST,x,y,0);
        var evidence=builder.finish(1);
        assert evidence.marks().size()==2048:"Do not stop at the old first 512 markers";
        assert evidence.marks().stream().anyMatch(m->m.y()<0)&&evidence.marks().stream().anyMatch(m->m.y()>0);
        var reservoir=new MarkerReservoir();
        for(int y=-64;y<320;y++)for(int x=0;x<16;x++)for(int z=0;z<4;z++)reservoir.add(new BlockMark(Signal.CHEST,x,y,z));
        var sampled=reservoir.snapshot();assert sampled.size()==MarkerReservoir.LIMIT;
        assert sampled.stream().filter(m->m.y()<0).count()>500&&sampled.stream().filter(m->m.y()>255).count()>500:"Crowded chunks retain both extremes of world height";

        for(var status:ChunkAnalysis.Status.values()) {
            var a=new ChunkAnalysis(0,0,1,status,Map.of(Signal.CLUSTER,4),0,4,0,ChunkAnalysis.StructureEvidence.NONE,"",0,0,0,0,0,0,List.of(),false);
            boolean candidate=ChunkVisualRules.categories(a,Set.of(ChunkSelection.Category.SEARCH),true,false,4).contains(ChunkSelection.Category.SEARCH);
            assert candidate==(status==ChunkAnalysis.Status.LIVE):"Search candidates require a current completed scan";
            assert !ChunkVisualRules.categories(a,Set.of(ChunkSelection.Category.SEARCH),true,false,5).contains(ChunkSelection.Category.SEARCH);
        }
        var texture=Identifier.fromNamespaceAndPath("minecraft","textures/block/stone.png");
        float[] uv={0,0,1,0,1,1,0,1};
        // A face at Y=0 must project from either side of the ground, not only from above.
        var face=new ModelGeometryCapture.Face(new double[]{0,0,0,1,0,0,1,0,1,0,0,1},uv,texture);
        var capture=new ModelGeometryCapture.Capture(List.of(face),0,false,"");
        for(int side:new int[]{-1,1}) {
            var matrix=new Matrix4f().perspective((float)Math.toRadians(70),1.5f,.05f,1000)
                    .lookAt(0,0,0,0,-side,0,0,0,-side);
            var eye=new Vec3(.5,side*20,.5);
            var mesh=new ModelHighlightMesh(matrix,eye,900,600);
            assert mesh.add(capture,0xFFFF9000):"Face remains visible from both vertical directions";
            var behind=new ModelGeometryCapture.Face(new double[]{0,side*40,0,1,side*40,0,1,side*40,1,0,side*40,1},uv,texture);
            var hidden=new ModelHighlightMesh(matrix,eye,900,600);
            assert !hidden.add(new ModelGeometryCapture.Capture(List.of(behind),0,false,""),0xFFFF9000);
            assert hidden.remaining()==ModelHighlightMesh.FACE_BUDGET:"Behind-camera geometry must not starve visible objects";
        }
        System.out.println("PASS: single relog, cancellation, mature-cluster thresholds, full-height markers, above/below projection and off-screen budgets");
    }
}
