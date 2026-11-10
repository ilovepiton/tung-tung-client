package dev.amethyst.finder.ui;

/** Projects a received player position to the screen or its nearest padded edge. */
public final class PlayerIndicatorGeometry {
    public record Indicator(double x, double y, double directionX, double directionY, boolean offscreen) {}

    private PlayerIndicatorGeometry() {}

    public static Indicator project(double clipX, double clipY, double clipW, int width, int height, double padding) {
        if (!Double.isFinite(clipX) || !Double.isFinite(clipY) || !Double.isFinite(clipW)
                || width <= 0 || height <= 0 || !Double.isFinite(padding) || padding < 0) return null;
        double halfW = width * .5, halfH = height * .5;
        double edgeX = Math.max(.25, halfW - Math.min(padding, Math.max(0, halfW - .25)));
        double edgeY = Math.max(.25, halfH - Math.min(padding, Math.max(0, halfH - .25)));
        // Dividing by |w| preserves the turn direction for a target behind the camera.
        // Normalize before screen scaling so even extreme finite clip values stay safe.
        double scale = Math.max(Math.abs(clipX), Math.max(Math.abs(clipY), Math.abs(clipW)));
        if (scale == 0) return null;
        double x = clipX / scale, y = -clipY / scale, w = clipW / scale;
        double denominator = Math.max(Math.abs(w), 1e-12);
        double dx = x / denominator * halfW, dy = y / denominator * halfH;
        double length = Math.hypot(dx, dy);
        if (length < 1e-9) {
            if (clipW > .05) return new Indicator(halfW, halfH, 0, -1, false);
            // Straight behind has no left/right direction; consistently point downward.
            dx = 0; dy = 1; length = 1;
        }
        boolean offscreen = clipW <= .05 || Math.abs(dx) > edgeX || Math.abs(dy) > edgeY;
        double factor = offscreen ? Math.min(edgeX / Math.max(Math.abs(dx), 1e-12),
                edgeY / Math.max(Math.abs(dy), 1e-12)) : 1;
        return new Indicator(halfW + dx * factor, halfH + dy * factor, dx / length, dy / length, offscreen);
    }
}
