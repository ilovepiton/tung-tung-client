package dev.amethyst.finder.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.amethyst.finder.core.HighlightProjection;
import dev.amethyst.finder.modules.mixin.GuiGraphicsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Solid type-colored model surfaces; texture alpha preserves cutouts through terrain. */
final class ModelHighlightMesh {
    static final int FACE_BUDGET = 8192;
    private final List<Surface> surfaces = new ArrayList<>();
    private final Matrix4f projection;
    private final Vec3 eye;
    private final int width, height;
    private int faces, unsupported;
    private boolean truncated;
    private String issue = "";

    ModelHighlightMesh(Matrix4f projection, Vec3 eye, int width, int height) {
        this.projection = projection; this.eye = eye; this.width = width; this.height = height;
    }
    int remaining() { return Math.max(0, FACE_BUDGET - faces); }
    void budgetReached() { truncated = true; }
    String status() {
        if (!issue.isEmpty()) return "Model highlights: " + issue;
        if (unsupported > 0) return "Model highlights: " + unsupported + " unsupported render submissions omitted";
        if (truncated) return "Model highlights: detail budget reached";
        return "Model highlights ready";
    }
    boolean hasIssue() { return !issue.isEmpty() || unsupported > 0 || truncated; }

    boolean add(ModelGeometryCapture.Capture capture, int color) {
        int before=surfaces.size();
        unsupported += capture.unsupported();
        truncated |= capture.truncated();
        if (!capture.issue().isEmpty() && issue.isEmpty()) issue = capture.issue();
        for (ModelGeometryCapture.Face face : capture.faces()) {
            double[] xyz = face.xyz(); float[] uv = face.uv();
            if (xyz.length != 12 || uv.length != 8 || face.texture() == null) {
                unsupported++; continue;
            }
            List<HighlightProjection.Vertex> vertices = new ArrayList<>(4);
            double depth = 0;
            for (int i = 0; i < 4; i++) {
                Vector4f q = projection.transform(new Vector4f((float)(xyz[i*3] - eye.x),
                        (float)(xyz[i*3+1] - eye.y), (float)(xyz[i*3+2] - eye.z), 1));
                vertices.add(new HighlightProjection.Vertex(q.x, q.y, q.z, q.w, uv[i*2], uv[i*2+1]));
                depth += q.w;
            }
            List<HighlightProjection.Vertex> polygon = HighlightProjection.clip(vertices);
            if (polygon.size() < 3) continue;
            List<ScreenVertex> screen = new ArrayList<>(polygon.size());
            for (var v : polygon) screen.add(new ScreenVertex((float)((v.x()/v.w()*.5+.5)*width),
                    (float)((.5-v.y()/v.w()*.5)*height), v.u(), v.v()));
            double area = 0;
            for (int i=0;i<screen.size();i++) {
                ScreenVertex a=screen.get(i), b=screen.get((i+1)%screen.size());
                area += a.x*b.y-b.x*a.y;
            }
            if (Math.abs(area)<.15) continue;
            if (faces >= FACE_BUDGET) { truncated = true; break; }
            faces++;
            // Keep both sides: cutout planes and resource-pack geometry can be double sided.
            surfaces.add(new Surface(screen, face.texture(), shade(color, xyz), depth/4));
        }
        return surfaces.size()>before;
    }

    void draw(GuiGraphicsExtractor graphics, Minecraft client) {
        surfaces.sort(Comparator.comparingDouble(Surface::depth).reversed());
        Map<Identifier,TextureSetup> textures = new HashMap<>();
        var gui = ((GuiGraphicsAccessor)graphics).tuntung$guiRenderState();
        Matrix3x2fc pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissor = new ScreenRectangle(0,0,width,height);
        for (int at=0;at<surfaces.size();) {
            Surface surface=surfaces.get(at);
            int end=at+1;
            while (end<surfaces.size() && end-at<64 && surfaces.get(end).texture.equals(surface.texture)) end++;
            List<Surface> batch=List.copyOf(surfaces.subList(at,end));
            at=end;
            TextureSetup texture = textures.get(surface.texture);
            if (texture == null) {
                try {
                    var source = client.getTextureManager().getTexture(surface.texture);
                    if (source.getTextureView() == null || source.getSampler() == null) {
                        unsupported++; continue;
                    }
                    texture = TextureSetup.singleTexture(source.getTextureView(), source.getSampler());
                    textures.put(surface.texture,texture);
                } catch (RuntimeException error) {
                    unsupported++; if (issue.isEmpty()) issue="Texture unavailable: " + surface.texture;
                    continue;
                }
            }
            gui.addGuiElement(new FaceState(batch,pose,texture,scissor));
        }
    }

    private static int shade(int color, double[] p) {
        double ax=p[3]-p[0], ay=p[4]-p[1], az=p[5]-p[2];
        double bx=p[6]-p[0], by=p[7]-p[1], bz=p[8]-p[2];
        double nx=ay*bz-az*by, ny=az*bx-ax*bz, nz=ax*by-ay*bx;
        double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
        double light=length<1e-10 ? 1 : Math.min(1,.76+.17*Math.abs(ny/length)+.07*Math.abs(nx/length)
                +(length<.035 ? .13 : 0));
        int r=(int)(((color>>16)&255)*light), g=(int)(((color>>8)&255)*light), b=(int)((color&255)*light);
        return 0xFF000000 | (r<<16) | (g<<8) | b;
    }
    private record ScreenVertex(float x,float y,float u,float v) {}
    private record Surface(List<ScreenVertex> vertices,Identifier texture,int color,double depth) {}

    private static final class FaceState implements GuiElementRenderState {
        private final List<Surface> surfaces;
        private final Matrix3x2fc pose;
        private final TextureSetup texture;
        private final ScreenRectangle scissor,bounds;
        FaceState(List<Surface> surfaces,Matrix3x2fc pose,TextureSetup texture,ScreenRectangle scissor) {
            this.surfaces=surfaces;this.pose=pose;this.texture=texture;this.scissor=scissor;
            float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
            for (Surface surface:surfaces) for (ScreenVertex v:surface.vertices) { minX=Math.min(minX,v.x);minY=Math.min(minY,v.y);maxX=Math.max(maxX,v.x);maxY=Math.max(maxY,v.y); }
            bounds=new ScreenRectangle((int)Math.floor(minX),(int)Math.floor(minY),
                    Math.max(1,(int)Math.ceil(maxX)-(int)Math.floor(minX)),Math.max(1,(int)Math.ceil(maxY)-(int)Math.floor(minY)))
                    .transformMaxBounds(pose).intersection(scissor);
        }
        public RenderPipeline pipeline() { return SolidHighlightPipeline.PIPELINE; }
        public TextureSetup textureSetup() { return texture; }
        public ScreenRectangle scissorArea() { return scissor; }
        public ScreenRectangle bounds() { return bounds; }
        public void buildVertices(VertexConsumer out) {
            // GUI uses quads. A duplicated final vertex represents each clipped fan triangle.
            for (Surface surface:surfaces) for (int i=1;i<surface.vertices.size()-1;i++) {
                ScreenVertex a=surface.vertices.get(0),b=surface.vertices.get(i),c=surface.vertices.get(i+1);
                // Projection can reverse captured world-face winding. Both GUI pipelines cull by default.
                boolean reverse=ChunkSelectionMath.reverseForGui(a.x,a.y,b.x,b.y,c.x,c.y,c.x,c.y);
                emit(out,a,surface.color);emit(out,reverse?c:b,surface.color);
                emit(out,reverse?b:c,surface.color);emit(out,reverse?b:c,surface.color);
            }
        }
        private void emit(VertexConsumer out,ScreenVertex v,int color) {
            out.addVertexWith2DPose(pose,v.x,v.y).setUv(v.u,v.v).setColor(color);
        }
    }
}
