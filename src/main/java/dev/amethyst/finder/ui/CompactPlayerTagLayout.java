package dev.amethyst.finder.ui;

/** Fits the complete scaled tag, including its padding, inside the GUI viewport. */
public final class CompactPlayerTagLayout {
    public record Placement(float x, float y, float scale, int width, int height) {}

    private CompactPlayerTagLayout() {}

    public static Placement fit(double anchorX, double anchorY, int contentWidth, int contentHeight,
                                double requestedScale, int viewportWidth, int viewportHeight) {
        if (!Double.isFinite(anchorX) || !Double.isFinite(anchorY) || contentWidth < 1 || contentHeight < 1
                || viewportWidth < 1 || viewportHeight < 1) return null;
        double scale = Double.isFinite(requestedScale) ? Math.clamp(requestedScale, .16, 1) : .50;
        double margin = Math.min(4, Math.min(viewportWidth, viewportHeight) * .1);
        scale = Math.min(scale, Math.min((viewportWidth - margin * 2) / contentWidth,
                (viewportHeight - margin * 2) / contentHeight));
        if (scale <= 0 || !Double.isFinite(scale)) return null;
        double width = contentWidth * scale, height = contentHeight * scale;
        double x = Math.clamp(anchorX - width * .5, margin, Math.max(margin, viewportWidth - margin - width));
        double y = Math.clamp(anchorY - height - 3, margin, Math.max(margin, viewportHeight - margin - height));
        return new Placement((float) x, (float) y, (float) scale, contentWidth, contentHeight);
    }
}
