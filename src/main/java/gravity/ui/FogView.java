package gravity.ui;

import gravity.engine.Noise;
import gravity.engine.Schwarzschild;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** Time dilation as fog: the thicker the fog, the slower clocks tick compared to a far-away clock. */
public final class FogView implements View {

    private static final double CLOCK_PERIOD_S = 6.0;   // one turn of the far-away clock, in real seconds

    private int[] buf;
    private float[] density;          // cached 1 - lapse(r) per pixel; depends only on size, zoom and body
    private Schwarzschild densityMetric;
    private double densityRadius;
    private WritableImage img;
    private int iw, ih;
    private double time;
    private final CheckBox showClocks = new CheckBox("Show clocks");
    private final CheckBox showFog = new CheckBox("Show fog");
    private final VBox controls;

    public FogView() {
        showClocks.setSelected(true);
        showFog.setSelected(true);
        controls = new VBox(6, showFog, showClocks,
                new Label("Fog density = how much slower\na clock there runs than a far-away\none. At the horizon time stops\nfor the distant observer."));
        controls.setPadding(new Insets(8, 0, 0, 0));
    }

    @Override public String name() { return "Fog of time"; }

    @Override public String description() {
        return "Think of time as something you move through, and near a mass it gets thick. "
                + "Where the fog is dense, clocks tick slowly compared to a clock far away. "
                + "The far clock makes one turn while the others lag behind.";
    }

    @Override public Node controls() { return controls; }

    @Override public void update(double dt, Model model) { time += dt; }

    @Override public void render(GraphicsContext g, Viewport vp, Model model) {
        Backdrop.clear(g, vp);
        Schwarzschild m = model.metric();
        if (!m.isBlackHole()) Backdrop.drawBody(g, vp, m, false);
        if (showFog.isSelected()) drawFog(g, vp, m);
        double s = vp.scale(), edge = m.surface() * s;
        if (m.isBlackHole()) {
            g.setFill(Color.BLACK);
            g.fillOval(vp.sx(0) - edge, vp.sy(0) - edge, 2 * edge, 2 * edge);
        }
        g.setStroke(m.isBlackHole() ? Color.rgb(255, 255, 255, 0.7) : Color.rgb(255, 200, 120, 0.8));
        g.setLineWidth(1.2);
        g.strokeOval(vp.sx(0) - edge, vp.sy(0) - edge, 2 * edge, 2 * edge);
        Backdrop.label(g, vp.sx(0), vp.sy(0) - edge - 6, m.isBlackHole() ? "event horizon: time stands still" : "star surface",
                Color.rgb(240, 240, 250, 0.85), TextAlignment.CENTER);
        if (showClocks.isSelected()) drawClocks(g, vp, m);
        Backdrop.scaleBar(g, vp, model);
    }

    private void drawFog(GraphicsContext g, Viewport vp, Schwarzschild m) {
        int w = Math.max(1, (int) (vp.w() / 2)), h = Math.max(1, (int) (vp.h() / 2));
        double scale = vp.scale() / 2;
        double cx = w / 2.0, cy = h / 2.0;
        if (buf == null || w != iw || h != ih) {
            iw = w; ih = h;
            buf = new int[w * h];
            density = new float[w * h];
            img = new WritableImage(w, h);
            densityMetric = null;
        }
        if (densityMetric != m || densityRadius != vp.radius()) {
            densityMetric = m;
            densityRadius = vp.radius();
            IntStream.range(0, h).parallel().forEach(py -> {
                double y = (cy - (py + 0.5)) / scale;
                for (int px = 0; px < w; px++) {
                    double x = ((px + 0.5) - cx) / scale;
                    double d = 1 - m.lapse(Math.hypot(x, y));
                    density[py * w + px] = d >= 0.999 ? 2f : (float) Math.pow(d, 0.7);   // 2 marks "opaque"
                }
            });
        }
        double noiseScale = 10.0 / vp.radius();
        double t = time * 0.25;
        IntStream.range(0, h).parallel().forEach(py -> {
            double y = (cy - (py + 0.5)) / scale;
            for (int px = 0; px < w; px++) {
                double x = ((px + 0.5) - cx) / scale;
                float d = density[py * w + px];
                double a;
                if (d >= 2f) {
                    a = 1;
                } else {
                    double n = Noise.fbm(x * noiseScale, y * noiseScale, t);
                    a = Math.max(0, Math.min(1, d * (0.25 + 1.3 * n)));
                }
                buf[py * w + px] = ((int) (a * 255) << 24) | 0xDCE4F8;
            }
        });
        img.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), buf, 0, w);
        g.drawImage(img, 0, 0, vp.w(), vp.h());
    }

    private void drawClocks(GraphicsContext g, Viewport vp, Schwarzschild m) {
        List<Double> radii = new ArrayList<>();
        double rs = m.rs();
        if (m.isBlackHole()) {
            for (double k : new double[]{1.02, 1.1, 1.3, 1.7, 2.5, 4, 7, 12, 20}) radii.add(k * rs);
        } else {
            radii.add(0.0);
            radii.add(m.radius() * 0.6);
            for (double k : new double[]{1.15, 1.6, 2.5, 4, 7, 12, 20}) if (k * rs > m.radius() * 1.08) radii.add(k * rs);
        }
        double clockR = 17;
        double limit = Math.min(vp.visibleX(), vp.visibleY()) - clockR / vp.scale() * 1.5;
        double prevX = Double.NEGATIVE_INFINITY;
        for (double r : radii) {
            if (r > limit) continue;
            double sx = vp.sx(r), sy = vp.sy(0);
            if (sx - prevX < clockR * 2.6) continue;
            prevX = sx;
            drawClock(g, sx, sy, clockR, m.lapse(r), String.format("r = %.2f M", r));
        }
        drawClock(g, vp.w() - 60, 60, clockR + 5, 1.0, "far away");
    }

    private void drawClock(GraphicsContext g, double x, double y, double rad, double rate, String caption) {
        g.setFill(Color.rgb(20, 24, 36, 0.92));
        g.fillOval(x - rad, y - rad, 2 * rad, 2 * rad);
        g.setStroke(Color.rgb(240, 240, 250));
        g.setLineWidth(1.5);
        g.strokeOval(x - rad, y - rad, 2 * rad, 2 * rad);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            g.setLineWidth(i % 3 == 0 ? 1.5 : 0.7);
            g.strokeLine(x + Math.sin(a) * rad * 0.8, y - Math.cos(a) * rad * 0.8,
                    x + Math.sin(a) * rad * 0.92, y - Math.cos(a) * rad * 0.92);
        }
        double turns = time / CLOCK_PERIOD_S * rate;
        double a = turns * 2 * Math.PI;
        g.setStroke(rate <= 0 ? Color.rgb(255, 90, 90) : Color.rgb(255, 215, 90));
        g.setLineWidth(2);
        g.strokeLine(x, y, x + Math.sin(a) * rad * 0.75, y - Math.cos(a) * rad * 0.75);
        double a2 = turns / 12 * 2 * Math.PI;
        g.setLineWidth(2.5);
        g.strokeLine(x, y, x + Math.sin(a2) * rad * 0.45, y - Math.cos(a2) * rad * 0.45);
        g.setFont(Backdrop.SMALL);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.TOP);
        g.setFill(Color.rgb(0, 0, 0, 0.55));
        g.fillRect(x - 36, y + rad + 2, 72, 28);
        g.setFill(Color.rgb(240, 240, 250));
        g.fillText(caption, x, y + rad + 3);
        g.fillText(rate <= 0 ? "stopped" : String.format("%.0f%% speed", rate * 100), x, y + rad + 16);
        g.setTextAlign(TextAlignment.LEFT);
    }
}
