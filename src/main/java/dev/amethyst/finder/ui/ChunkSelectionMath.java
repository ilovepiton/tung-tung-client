package dev.amethyst.finder.ui;

import java.util.Optional;

/** Pure geometry used by chunk selection and clipping; never rounds world positions to screen pixels. */
public final class ChunkSelectionMath {
    public record Bounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        public static Bounds chunk(int chunkX,int chunkZ,int minY,int maxY) {
            if (maxY<=minY) throw new IllegalArgumentException("Empty world height");
            double x=chunkX*16.0,z=chunkZ*16.0;
            return new Bounds(x,minY,z,x+16,maxY,z+16);
        }
    }
    public record ClipPoint(double x,double y,double w) {}
    public record Segment(ClipPoint a,ClipPoint b) {}
    private ChunkSelectionMath() {}

    /** Minecraft getMaxY() is inclusive; the upper chunk boundary is one block above it. */
    public static int exclusiveWorldMaxY(int inclusiveMaxY) { return Math.addExact(inclusiveMaxY,1); }

    /** Distance along a normalized view ray; infinity means the complete world-height chunk is not intersected. */
    public static double rayDistance(Bounds bounds,double ox,double oy,double oz,double dx,double dy,double dz) {
        if (!finite(ox,oy,oz,dx,dy,dz)) return Double.POSITIVE_INFINITY;
        double length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if (!Double.isFinite(length)||length<1e-12) return Double.POSITIVE_INFINITY;
        double[] origin={ox,oy,oz},direction={dx/length,dy/length,dz/length};
        double[] minimum={bounds.minX,bounds.minY,bounds.minZ},maximum={bounds.maxX,bounds.maxY,bounds.maxZ};
        double near=0,far=Double.POSITIVE_INFINITY;
        for (int axis=0;axis<3;axis++) {
            if (Math.abs(direction[axis])<1e-12) {
                if (origin[axis]<minimum[axis]||origin[axis]>=maximum[axis]) return Double.POSITIVE_INFINITY;
            } else {
                double a=(minimum[axis]-origin[axis])/direction[axis],b=(maximum[axis]-origin[axis])/direction[axis];
                near=Math.max(near,Math.min(a,b));far=Math.min(far,Math.max(a,b));
                if (far<near) return Double.POSITIVE_INFINITY;
            }
        }
        return far>=0 ? near : Double.POSITIVE_INFINITY;
    }

    /** Clips before perspective division, retaining edges crossing the near camera plane and screen boundary. */
    public static Optional<Segment> clipSegment(ClipPoint a,ClipPoint b) {
        if (a==null||b==null||!finite(a.x,a.y,a.w,b.x,b.y,b.w)) return Optional.empty();
        double t0=0,t1=1;
        for (int plane=0;plane<5;plane++) {
            double da=distance(a,plane),db=distance(b,plane);
            if (da<0&&db<0) return Optional.empty();
            if ((da<0)!=(db<0)) {
                double scale=Math.max(Math.abs(da),Math.abs(db));
                double t=(Math.abs(da)/scale)/(Math.abs(da)/scale+Math.abs(db)/scale);
                if (da<0) t0=Math.max(t0,t);else t1=Math.min(t1,t);
                if (t0>t1) return Optional.empty();
            }
        }
        return Optional.of(new Segment(clamp(interpolate(a,b,t0)),clamp(interpolate(a,b,t1))));
    }
    public static double fade(double ageMillis,double durationMillis) {
        if (!Double.isFinite(ageMillis)||!Double.isFinite(durationMillis)||durationMillis<=0) return 0;
        double t=Math.clamp(ageMillis/durationMillis,0,1);
        return t*t*(3-2*t);
    }
    /** Vanilla GUI pipelines cull back faces: their accepted screen order is TL, BL, BR, TR. */
    public static boolean reverseForGui(double ax,double ay,double bx,double by,double cx,double cy,double dx,double dy) {
        double area=ax*by-bx*ay+bx*cy-cx*by+cx*dy-dx*cy+dx*ay-ax*dy;
        return area>0;
    }
    private static double distance(ClipPoint p,int plane) {
        return switch(plane) { case 0 -> p.w-.05;case 1 -> p.w*.5+p.x*.5;case 2 -> p.w*.5-p.x*.5;
            case 3 -> p.w*.5+p.y*.5;case 4 -> p.w*.5-p.y*.5;default -> throw new IllegalArgumentException(); };
    }
    private static ClipPoint interpolate(ClipPoint a,ClipPoint b,double t) {
        return new ClipPoint(a.x*(1-t)+b.x*t,a.y*(1-t)+b.y*t,a.w*(1-t)+b.w*t);
    }
    private static ClipPoint clamp(ClipPoint p) { double w=Math.max(.05,p.w);return new ClipPoint(Math.clamp(p.x,-w,w),Math.clamp(p.y,-w,w),w); }
    private static boolean finite(double... values) { for(double value:values)if(!Double.isFinite(value))return false;return true; }
}
