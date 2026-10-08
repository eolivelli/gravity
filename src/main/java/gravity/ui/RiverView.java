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
 */
public final class RiverView implements View {

    private static final int DUST = 700;
    private static final double PULSE_EVERY = 4.0;   // in simulated units of M

    private static final class Pulse {
        double r, phi, dir;   // dir = +1 outward, -1 inward
        double age;
        final List<double[]> tail = new ArrayList<>();
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

    private double[] emitterRadii(Schwarzschild m, double viewR) {
        if (m.isBlackHole()) return new double[]{m.rs() * 0.55, m.rs() * 1.6, m.rs() * 3.5, m.rs() * 7, viewR * 0.75};
        return new double[]{m.radius() * 1.15, m.radius() * 2.2, m.rs() * 7, viewR * 0.75};
    }

    @Override public void update(double dt, Model model) {
        if (!seeded) seed(model);
        Schwarzschild m = model.metric();
        double R = model.viewRadius();
        double h = dt * model.timeScale();
        simTime += h;
        for (int i = 0; i < DUST; i++) {
            dustR[i] -= m.riverSpeed(dustR[i]) * h;
            double inner = m.isBlackHole() ? 0.2 * m.rs() : 0.06 * m.radius();
            if (dustR[i] < inner || (!m.isBlackHole() && dustR[i] < m.radius() * 0.08)) {
                dustR[i] = R * (1.2 + 0.8 * rnd.nextDouble());
                dustPhi[i] = rnd.nextDouble() * 2 * Math.PI;
            }
        }
        if (showPulses.isSelected() && simTime >= nextPulse) {
            nextPulse = simTime + PULSE_EVERY;
            double[] radii = emitterRadii(m, R);
            for (int i = 0; i < radii.length; i++) {
                double phi = Math.toRadians(25 + 60 * i);
                for (double dir : new double[]{1, -1}) {
                    Pulse p = new Pulse();
                    p.r = radii[i]; p.phi = phi; p.dir = dir;
                    pulses.add(p);
                }
            }
        }
        for (Pulse p : pulses) {
            double v = m.riverSpeed(p.r);
            p.r += (p.dir - v) * h;
            p.age += h;
            p.tail.add(new double[]{p.r});
            if (p.tail.size() > 14) p.tail.remove(0);
        }
        pulses.removeIf(p -> p.r < (m.isBlackHole() ? 0.1 * m.rs() : 0.02 * m.radius()) || p.r > R * 2.5 || p.age > 80);
    }

    @Override public void render(GraphicsContext g, Viewport vp, Model model) {
        Backdrop.clear(g, vp);
        Schwarzschild m = model.metric();
        double s = vp.scale();
        if (showArrows.isSelected()) drawArrows(g, vp, m);
        Backdrop.drawBody(g, vp, m, true);
        if (showDust.isSelected()) {
            g.setFill(Color.rgb(200, 215, 255, 0.75));
            for (int i = 0; i < DUST; i++) {
                double x = vp.sx(dustR[i] * Math.cos(dustPhi[i])), y = vp.sy(dustR[i] * Math.sin(dustPhi[i]));
                g.fillOval(x - 1.2, y - 1.2, 2.4, 2.4);
            }
        }
        if (showPulses.isSelected()) {
            for (double r : emitterRadii(m, model.viewRadius())) {
                int idx = 0;
                for (double rr : emitterRadii(m, model.viewRadius())) { if (rr == r) break; idx++; }
                double phi = Math.toRadians(25 + 60 * idx);
                double x = vp.sx(r * Math.cos(phi)), y = vp.sy(r * Math.sin(phi));
                g.setStroke(Color.rgb(255, 255, 255, 0.8));
                g.setLineWidth(1);
                g.strokeOval(x - 4, y - 4, 8, 8);
            }
            for (Pulse p : pulses) {
                Color c = p.dir > 0 ? Color.rgb(255, 220, 90) : Color.rgb(90, 220, 255);
                double cs = Math.cos(p.phi), sn = Math.sin(p.phi);
                g.setStroke(c.deriveColor(0, 1, 1, 0.5));
                g.setLineWidth(2);
                if (p.tail.size() > 1) {
                    g.strokeLine(vp.sx(p.tail.get(0)[0] * cs), vp.sy(p.tail.get(0)[0] * sn), vp.sx(p.r * cs), vp.sy(p.r * sn));
                }
                g.setFill(c);
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
                Color c = v >= 1 ? Color.rgb(255, 110, 90, 0.9) : Color.rgb(110, 150, 255, 0.35 + 0.55 * Math.min(1, v));
                g.setStroke(c);
                g.strokeLine(x0, y0, x1, y1);
                double ah = Math.min(6, len * 0.4);
                double ang = Math.atan2(-uy, ux);
                g.strokeLine(x1, y1, x1 - ah * Math.cos(ang - 0.5), y1 - ah * Math.sin(ang - 0.5));
                g.strokeLine(x1, y1, x1 - ah * Math.cos(ang + 0.5), y1 - ah * Math.sin(ang + 0.5));
            }
        }
    }
}
