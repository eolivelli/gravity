package gravity.ui;

import gravity.engine.Schwarzschild;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Curvature as what it does to a small ball of free-falling dust: stretched
 * along the radial direction, squeezed sideways. This is the quantity the
 * Riemann tensor measures.
 */
public final class TidalView implements View {

    private static final int HUES = 32;
    private static final Color[] PALETTE = new Color[HUES + 1];
    private static final Color ORIGINAL = Color.rgb(255, 255, 255, 0.12);
    static {
        for (int i = 0; i <= HUES; i++) PALETTE[i] = Color.hsb(210 - 210.0 * i / HUES, 0.7, 0.95, 0.9);
    }

    private final Slider gain = new Slider(1, 300, 60);
    private final VBox controls;
    private double time;

    public TidalView() {
        controls = new VBox(6, new Label("Exaggeration"), gain,
                new Label("Each circle is a ball of dust\nfalling freely. Watch how one\nunit of time deforms it."));
        controls.setPadding(new Insets(8, 0, 0, 0));
    }

    @Override public String name() { return "Stretch & squeeze"; }

    @Override public String description() {
        return "Curvature is what happens to a small ball of dust in free fall: it gets stretched toward the mass "
                + "and squeezed from the sides, more strongly the closer it is. Inside a star everything is squeezed. "
                + "Far away nothing happens, which is what 'flat' means.";
    }

    @Override public Node controls() { return controls; }

    @Override public void update(double dt, Model model) { time += dt; }

    @Override public void render(GraphicsContext g, Viewport vp, Model model) {
        Backdrop.clear(g, vp);
        Schwarzschild m = model.metric();
        Backdrop.drawBody(g, vp, m, false);
        double phase = 0.5 - 0.5 * Math.cos(2 * Math.PI * time / 4.0);
        double spacing = vp.radius() / 7;
        double a = spacing * 0.28 * vp.scale();
        int nx = (int) Math.ceil(vp.visibleX() / spacing), ny = (int) Math.ceil(vp.visibleY() / spacing);
        double k = gain.getValue() * phase;
        for (int i = -nx; i <= nx; i++) {
            for (int j = -ny; j <= ny; j++) {
                double x = i * spacing, y = j * spacing;
                double r = Math.hypot(x, y);
                if (r < 1e-9) r = 1e-9;
                if (m.isBlackHole() && r < m.rs() * 1.05) continue;
                double tr = m.tidalRadial(r), tt = m.tidalTangential(r);
                double sr = Math.exp(clamp(k * tr, -1.5, 1.5));
                double st = Math.exp(clamp(k * tt, -1.5, 1.5));
                double strength = Math.min(1, Math.abs(tr) * gain.getValue() * 3);
                Color c = PALETTE[(int) Math.round(strength * HUES)];
                g.save();
                g.translate(vp.sx(x), vp.sy(y));
                g.rotate(-Math.toDegrees(Math.atan2(y, x)));
                g.setStroke(c);
                g.setLineWidth(1.5);
                g.strokeOval(-a * sr, -a * st, 2 * a * sr, 2 * a * st);
                g.setStroke(ORIGINAL);
                g.setLineWidth(1);
                g.strokeOval(-a, -a, 2 * a, 2 * a);
                g.restore();
            }
        }
        Backdrop.scaleBar(g, vp, model);
        Backdrop.hud(g, vp, "red = strong curvature, blue = weak", "faint circle = original shape");
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
