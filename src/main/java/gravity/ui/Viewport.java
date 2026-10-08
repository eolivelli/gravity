package gravity.ui;

/** Maps world coordinates (units of M, origin at the body) to screen pixels. */
public record Viewport(double w, double h, double radius) {

    public double scale() { return Math.min(w, h) / (2 * radius); }

    public double sx(double x) { return w / 2 + x * scale(); }
    public double sy(double y) { return h / 2 - y * scale(); }
    public double wx(double sx) { return (sx - w / 2) / scale(); }
    public double wy(double sy) { return (h / 2 - sy) / scale(); }

    /** World half-width and half-height actually visible. */
    public double visibleX() { return w / 2 / scale(); }
    public double visibleY() { return h / 2 / scale(); }
}
