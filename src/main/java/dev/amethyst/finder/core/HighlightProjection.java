package dev.amethyst.finder.core;

import java.util.ArrayList;
import java.util.List;

/** Clips ordered, convex model faces before their perspective divide. */
public final class HighlightProjection {
    public static final double MIN_W = 0.05;

    public record Vertex(double x, double y, double z, double w, float u, float v) {}

    private HighlightProjection() {}

    /**
     * Retains the positive camera half-space and the four screen edges. Z is
     * carried through without a depth clip because these faces form an overlay.
     * Invalid input rejects the entire face rather than emitting unsafe vertices.
     */
    public static List<Vertex> clip(List<Vertex> polygon) {
        if (!valid(polygon)) return List.of();
        List<Vertex> result = new ArrayList<>(polygon);
        for (int plane = 0; plane < 5; plane++) {
            if (result.size() < 3) return List.of();
            List<Vertex> next = new ArrayList<>(result.size() + 2);
            Vertex previous = result.getLast();
            double previousDistance = distance(previous, plane);
            for (Vertex current : result) {
                double currentDistance = distance(current, plane);
                boolean previousInside = previousDistance >= 0;
                boolean currentInside = currentDistance >= 0;
                if (previousInside != currentInside) {
                    double a = Math.abs(previousDistance);
                    double b = Math.abs(currentDistance);
                    double scale = Math.max(a, b);
                    // Normalizing first avoids overflow for large finite inputs.
                    double t = (a / scale) / (a / scale + b / scale);
                    append(next, onPlane(interpolate(previous, current, t), plane));
                }
                if (currentInside) append(next, current);
                previous = current;
                previousDistance = currentDistance;
            }
            if (next.size() > 1 && next.getFirst().equals(next.getLast())) next.removeLast();
            result = next;
        }
        if (result.size() < 3) return List.of();
        // Remove sub-ulp drift left by interpolation against an earlier plane.
        List<Vertex> safe = new ArrayList<>(result.size());
        for (Vertex vertex : result) {
            double w = Math.max(MIN_W, vertex.w());
            append(safe, new Vertex(Math.clamp(vertex.x(), -w, w),
                    Math.clamp(vertex.y(), -w, w), vertex.z(), w, vertex.u(), vertex.v()));
        }
        if (safe.size() > 1 && safe.getFirst().equals(safe.getLast())) safe.removeLast();
        return safe.size() < 3 ? List.of() : List.copyOf(safe);
    }

    /** Emits each fan triangle as four vertices, with its final vertex repeated. */
    public static List<Vertex> trianglesAsQuads(List<Vertex> polygon) {
        if (!valid(polygon)) return List.of();
        List<Vertex> result = new ArrayList<>((polygon.size() - 2) * 4);
        Vertex first = polygon.getFirst();
        for (int i = 1; i < polygon.size() - 1; i++) {
            Vertex last = polygon.get(i + 1);
            result.add(first);
            result.add(polygon.get(i));
            result.add(last);
            result.add(last);
        }
        return List.copyOf(result);
    }

    private static boolean valid(List<Vertex> polygon) {
        if (polygon == null || polygon.size() < 3) return false;
        for (Vertex v : polygon) {
            if (v == null || !Double.isFinite(v.x()) || !Double.isFinite(v.y())
                    || !Double.isFinite(v.z()) || !Double.isFinite(v.w())
                    || !Float.isFinite(v.u()) || !Float.isFinite(v.v())) return false;
        }
        return true;
    }

    private static double distance(Vertex vertex, int plane) {
        return switch (plane) {
            case 0 -> vertex.w() - MIN_W;
            case 1 -> vertex.x() * 0.5 + vertex.w() * 0.5;
            case 2 -> vertex.w() * 0.5 - vertex.x() * 0.5;
            case 3 -> vertex.y() * 0.5 + vertex.w() * 0.5;
            case 4 -> vertex.w() * 0.5 - vertex.y() * 0.5;
            default -> throw new IllegalArgumentException("Unknown clip plane");
        };
    }

    private static Vertex interpolate(Vertex a, Vertex b, double t) {
        double s = 1 - t;
        return new Vertex(s * a.x() + t * b.x(), s * a.y() + t * b.y(),
                s * a.z() + t * b.z(), s * a.w() + t * b.w(),
                (float) (s * a.u() + t * b.u()), (float) (s * a.v() + t * b.v()));
    }

    private static Vertex onPlane(Vertex v, int plane) {
        return switch (plane) {
            case 0 -> new Vertex(v.x(), v.y(), v.z(), MIN_W, v.u(), v.v());
            case 1 -> new Vertex(-v.w(), v.y(), v.z(), v.w(), v.u(), v.v());
            case 2 -> new Vertex(v.w(), v.y(), v.z(), v.w(), v.u(), v.v());
            case 3 -> new Vertex(v.x(), -v.w(), v.z(), v.w(), v.u(), v.v());
            case 4 -> new Vertex(v.x(), v.w(), v.z(), v.w(), v.u(), v.v());
            default -> throw new IllegalArgumentException("Unknown clip plane");
        };
    }

    private static void append(List<Vertex> vertices, Vertex vertex) {
        if (vertices.isEmpty() || !vertices.getLast().equals(vertex)) vertices.add(vertex);
    }
}
