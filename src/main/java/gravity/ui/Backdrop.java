package gravity.ui;

import gravity.engine.Schwarzschild;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/** Drawing shared by the map views: background, the body, reference rings, scale bar, HUD text. */
public final class Backdrop {

    public static final Color BACKGROUND = Color.rgb(9, 12, 22);
    public static final Font SMALL = Font.font("Sans", 11);
    public static final Font LABEL = Font.font("Sans", 12);

    private Backdrop() {}

    public static void clear(GraphicsContext g, Viewport vp) {
        g.setFill(BACKGROUND);
        g.fillRect(0, 0, vp.w(), vp.h());
    }

    /** The star or the black hole, with its reference rings. */
    public static void drawBody(GraphicsContext g, Viewport vp, Schwarzschild m, boolean rings) {
        double s = vp.scale();
        double cx = vp.sx(0), cy = vp.sy(0);
        if (m.isBlackHole()) {
            double rh = m.rs() * s;
            g.setFill(Color.BLACK);
            g.fillOval(cx - rh, cy - rh, 2 * rh, 2 * rh);
            g.setStroke(Color.rgb(255, 255, 255, 0.55));
            g.setLineWidth(1.2);
            g.setLineDashes();
            g.strokeOval(cx - rh, cy - rh, 2 * rh, 2 * rh);
            if (rings) {
                ring(g, vp, m.photonSphere(), Color.rgb(255, 210, 80, 0.45), "photon sphere");
                ring(g, vp, m.isco(), Color.rgb(120, 200, 255, 0.3), null);
                label(g, cx - m.isco() * s * 0.7071 - 4, cy - m.isco() * s * 0.7071 - 2, "innermost stable orbit", Color.rgb(140, 210, 255, 0.7));
                label(g, cx, cy + Math.max(rh, m.isco() * s) + 16, "event horizon", Color.rgb(255, 255, 255, 0.7), TextAlignment.CENTER);
            }
        } else {
            double rb = m.radius() * s;
            RadialGradient grad = new RadialGradient(0, 0, cx, cy, rb, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(255, 230, 150)),
                    new Stop(0.75, Color.rgb(245, 150, 50)),
                    new Stop(1, Color.rgb(180, 70, 20)));
            g.setFill(grad);
            g.fillOval(cx - rb, cy - rb, 2 * rb, 2 * rb);
            if (rings) {
                double rh = m.rs() * s;
                g.setStroke(Color.rgb(255, 255, 255, 0.25));
                g.setLineWidth(1);
                g.setLineDashes(3, 5);
                g.strokeOval(cx - rh, cy - rh, 2 * rh, 2 * rh);
                g.setLineDashes();
                label(g, cx, cy + Math.max(rb, m.photonSphere() * s) + 16, "star surface", Color.rgb(255, 220, 160, 0.8), TextAlignment.CENTER);
                if (m.photonSphere() > m.radius()) {
                    ring(g, vp, m.photonSphere(), Color.rgb(255, 210, 80, 0.45), "photon sphere");
                }
            }
        }
    }

    public static void ring(GraphicsContext g, Viewport vp, double r, Color c, String text) {
        double s = vp.scale();
        double cx = vp.sx(0), cy = vp.sy(0);
        g.setStroke(c);
        g.setLineWidth(1);
        g.setLineDashes(4, 6);
        g.strokeOval(cx - r * s, cy - r * s, 2 * r * s, 2 * r * s);
        g.setLineDashes();
        if (text != null) label(g, cx + r * s * 0.7071 + 4, cy - r * s * 0.7071 - 2, text, c.deriveColor(0, 1, 1.4, 1.6));
    }

    /** Small label with its left edge at x and baseline at y. */
    public static void label(GraphicsContext g, double x, double y, String text, Color c) {
        label(g, x, y, text, c, TextAlignment.LEFT);
    }

    public static void label(GraphicsContext g, double x, double y, String text, Color c, TextAlignment align) {
        g.setFont(SMALL);
        g.setFill(c);
        g.setTextAlign(align);
        g.setTextBaseline(VPos.BOTTOM);
        g.fillText(text, x, y);
        g.setTextAlign(TextAlignment.LEFT);
    }

    public static void scaleBar(GraphicsContext g, Viewport vp, Model model) {
        double km = model.kmPerM();
        double target = vp.w() * 0.18 / vp.scale();        // in M
        double unit = Math.pow(10, Math.floor(Math.log10(target)));
        double lenM = unit;
        if (target / unit >= 5) lenM = 5 * unit; else if (target / unit >= 2) lenM = 2 * unit;
        double px = lenM * vp.scale();
        double x0 = 16, y0 = vp.h() - 18;
        g.setStroke(Color.rgb(230, 230, 230, 0.8));
        g.setLineWidth(2);
        g.strokeLine(x0, y0, x0 + px, y0);
        g.strokeLine(x0, y0 - 4, x0, y0 + 4);
        g.strokeLine(x0 + px, y0 - 4, x0 + px, y0 + 4);
        String text = String.format("%s M  =  %s   (rs = %s)",
                fmt(lenM), fmtKm(lenM * km), fmtKm(model.metric().rs() * km));
        label(g, x0, y0 - 6, text, Color.rgb(230, 230, 230, 0.85));
    }

    public static void hud(GraphicsContext g, Viewport vp, String... lines) {
        g.setFont(LABEL);
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.TOP);
        double y = 10;
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            g.setFill(Color.rgb(0, 0, 0, 0.5));
            g.fillRect(8, y - 2, textWidth(line) + 10, 17);
            g.setFill(Color.rgb(235, 235, 240));
            g.fillText(line, 13, y);
            y += 18;
        }
    }

    private static double textWidth(String s) { return s.length() * 6.6; }

    public static String fmt(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.format("%.2f", v);
    }

    public static String fmtKm(double km) {
        if (km >= 1e6) return String.format("%.2f million km", km / 1e6);
        if (km >= 100) return String.format("%.0f km", km);
        if (km >= 1) return String.format("%.1f km", km);
        return String.format("%.1f metres", km * 1000);
    }
}
