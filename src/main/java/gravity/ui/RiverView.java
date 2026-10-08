package gravity.ui;

import gravity.engine.Schwarzschild;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The river model: space itself flows into the mass at the escape velocity.
 * Light swims at speed 1 relative to the river; at the horizon the river flows
 * at the speed of light, so even outgoing light is carried inward.
 *
 * Inside a star the flow speed is shown for illustration only; the flashes of
 * light stop at the surface because the simple "swim relative to the river"
 * rule is only exact outside the body.
 */
public final class RiverView implements View {

    private static final int DUST = 700;
    private static final double PULSE_EVERY = 4.0;   // in simulated units of M
    private static final int ARROW_LEVELS = 8;
    private static final Color DUST_COLOR = Color.rgb(200, 215, 255, 0.75);
    private static final Color EMITTER_COLOR = Color.rgb(255, 255, 255, 0.8);
    private static final Color OUTWARD = GeodesicView.LIGHT, INWARD = GeodesicView.MATTER;
    private static final Color OUTWARD_TAIL = OUTWARD.deriveColor(0, 1, 1, 0.5), INWARD_TAIL = INWARD.deriveColor(0, 1, 1, 0.5);
    private static final Color ARROW_FAST = Color.rgb(255, 110, 90, 0.9);
    private static final Color[] ARROW_SLOW = new Color[ARROW_LEVELS + 1];
    static {
        for (int i = 0; i <= ARROW_LEVELS; i++) ARROW_SLOW[i] = Color.rgb(110, 150, 255, 0.35 + 0.55 * i / ARROW_LEVELS);
    }

    private static final class Pulse {
        double r, phi, dir;   // dir = +1 outward, -1 inward
        double age, tailR;
    }

    private final Random rnd = new Random(7);
    private final double[] dustR = new double[DUST], dustPhi = new double[DUST];
    private final List<Pulse> pulses = new ArrayList<>();
    private double simTime, nextPulse;
    private boolean seeded;
    private final CheckBox showArrows = new CheckBox("Flow arrows");
    private final CheckBox showDust = new CheckBox("Dust carried by the flow");
    private final CheckBox showPulses = new CheckBox("Flashes of light (out / in)");
    private final VBox controls;

    public RiverView() {
        showArrows.setSelected(true);
        showDust.setSelected(true);
        showPulses.setSelected(true);
        controls = new VBox(6, showArrows, showDust, showPulses,
                new Label("Yellow flashes try to swim\noutward, cyan ones inward,\nboth at the speed of light\nrelative to the river."));
        controls.setPadding(new Insets(8, 0, 0, 0));
    }

    @Override public String name() { return "River of space"; }

    @Override public String description() {
        return "Picture space as a river flowing into the mass, faster the closer you get. "
                + "Light swims at a fixed speed relative to the water. At the horizon the river flows at the "
                + "speed of light, so even light swimming outward is carried in.";
    }

    @Override public Node controls() { return controls; }

    @Override public void onMetricChanged(Model model) { pulses.clear(); }

    private void seed(Model model) {
        double R = model.viewRadius();
        for (int i = 0; i < DUST; i++) {
            dustR[i] = R * (0.1 + 1.5 * rnd.nextDouble());
            dustPhi[i] = rnd.nextDouble() * 2 * Math.PI;
        }
        seeded = true;
    }

    private static double[] emitterRadii(Schwarzschild m, double viewR) {
        if (m.isBlackHole()) return new double[]{m.rs() * 0.55, m.rs() * 1.6, m.rs() * 3.5, m.rs() * 7, viewR * 0.75};
        return new double[]{m.radius() * 1.15, m.radius() * 2.2, m.rs() * 7, viewR * 0.75};
    }

    private static double emitterAngle(int index) { return Math.toRadians(25 + 60 * index); }

    @Override public void update(double dt, Model model) {
        if (!seeded) seed(model);
        Schwarzschild m = model.metric();
        double R = model.viewRadius();
        double h = dt * model.timeScale();
        simTime += h;
        double respawnBelow = m.isBlackHole() ? 0.2 * m.rs() : 0.06 * m.radius();
        for (int i = 0; i < DUST; i++) {
            dustR[i] -= m.riverSpeed(dustR[i]) * h;
            if (dustR[i] < respawnBelow) {
                dustR[i] = R * (1.2 + 0.8 * rnd.nextDouble());
                dustPhi[i] = rnd.nextDouble() * 2 * Math.PI;
            }
        }
        if (showPulses.isSelected() && simTime >= nextPulse) {
            nextPulse = simTime + PULSE_EVERY;
            double[] radii = emitterRadii(m, R);
            for (int i = 0; i < radii.length; i++) {
                for (double dir : new double[]{1, -1}) {
                    Pulse p = new Pulse();
                    p.r = radii[i]; p.phi = emitterAngle(i); p.dir = dir; p.tailR = radii[i];
                    pulses.add(p);
                }
            }
        }
        for (Pulse p : pulses) {
            double v = m.riverSpeed(p.r);
            p.tailR = p.r;
            p.r += (p.dir - v) * h;
            p.age += h;
        }
        double vanishBelow = m.isBlackHole() ? 0.1 * m.rs() : m.radius();
        pulses.removeIf(p -> p.r < vanishBelow || p.r > R * 2.5 || p.age > 80);
    }

    @Override public void render(GraphicsContext g, Viewport vp, Model model) {
        Backdrop.clear(g, vp);
        Schwarzschild m = model.metric();
        if (showArrows.isSelected()) drawArrows(g, vp, m);
        Backdrop.drawBody(g, vp, m, true);
        if (showDust.isSelected()) {
            g.setFill(DUST_COLOR);
            for (int i = 0; i < DUST; i++) {
                double x = vp.sx(dustR[i] * Math.cos(dustPhi[i])), y = vp.sy(dustR[i] * Math.sin(dustPhi[i]));
                g.fillOval(x - 1.2, y - 1.2, 2.4, 2.4);
            }
        }
        if (showPulses.isSelected()) {
            double[] radii = emitterRadii(m, model.viewRadius());
            g.setStroke(EMITTER_COLOR);
            g.setLineWidth(1);
            for (int i = 0; i < radii.length; i++) {
                double x = vp.sx(radii[i] * Math.cos(emitterAngle(i))), y = vp.sy(radii[i] * Math.sin(emitterAngle(i)));
                g.strokeOval(x - 4, y - 4, 8, 8);
            }
            g.setLineWidth(2);
            for (Pulse p : pulses) {
                double cs = Math.cos(p.phi), sn = Math.sin(p.phi);
                g.setStroke(p.dir > 0 ? OUTWARD_TAIL : INWARD_TAIL);
                g.strokeLine(vp.sx(p.tailR * cs), vp.sy(p.tailR * sn), vp.sx(p.r * cs), vp.sy(p.r * sn));
                g.setFill(p.dir > 0 ? OUTWARD : INWARD);
                g.fillOval(vp.sx(p.r * cs) - 3, vp.sy(p.r * sn) - 3, 6, 6);
            }
        }
        Backdrop.scaleBar(g, vp, model);
        double vSurface = m.riverSpeed(m.surface());
        Backdrop.hud(g, vp, String.format("flow at the %s: %.0f%% of the speed of light",
                m.isBlackHole() ? "horizon" : "surface", vSurface * 100));
    }

    private void drawArrows(GraphicsContext g, Viewport vp, Schwarzschild m) {
        double spacing = vp.radius() / 9;
        double sp = spacing * vp.scale();
        int nx = (int) Math.ceil(vp.visibleX() / spacing), ny = (int) Math.ceil(vp.visibleY() / spacing);
        g.setLineWidth(1.3);
        for (int i = -nx; i <= nx; i++) {
            for (int j = -ny; j <= ny; j++) {
                double x = i * spacing, y = j * spacing;
                double r = Math.hypot(x, y);
                if (r < spacing * 0.5) continue;
                double v = m.riverSpeed(r);
                double len = Math.min(v, 1.6) * sp * 0.8;
                if (len < 2) continue;
                double ux = -x / r, uy = -y / r;
                double x0 = vp.sx(x), y0 = vp.sy(y);
                double x1 = x0 + ux * len, y1 = y0 - uy * len;
                g.setStroke(v >= 1 ? ARROW_FAST : ARROW_SLOW[(int) Math.round(Math.min(1, v) * ARROW_LEVELS)]);
                g.strokeLine(x0, y0, x1, y1);
                double ah = Math.min(6, len * 0.4);
                double ang = Math.atan2(-uy, ux);
                g.strokeLine(x1, y1, x1 - ah * Math.cos(ang - 0.5), y1 - ah * Math.sin(ang - 0.5));
                g.strokeLine(x1, y1, x1 - ah * Math.cos(ang + 0.5), y1 - ah * Math.sin(ang + 0.5));
            }
        }
    }
}
