package gravity.ui;

import gravity.engine.Geodesic;
import gravity.engine.Schwarzschild;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/** Light rays and free-falling particles: nothing pulls on them, they follow the straightest possible path. */
public final class GeodesicView implements View {

    private static final int TRAIL_MAX = 4000;

    private static final class Particle {
        final Geodesic g;
        final Color color;
        final List<double[]> trail = new ArrayList<>();
        Particle(Geodesic g, Color color) { this.g = g; this.color = color; }
    }

    private final List<Particle> particles = new ArrayList<>();
    private final Slider speed = new Slider(0.0, 1.0, 1.0);
    private final Label speedLabel = new Label();
    private double[] dragStart, dragNow;
    private final VBox controls;

    public GeodesicView(Model model) {
        speed.setBlockIncrement(0.05);
        speed.valueProperty().addListener((o, a, b) -> updateSpeedLabel());
        updateSpeedLabel();

        Button beam = new Button("Parallel beam of light");
        beam.setOnAction(e -> presetBeam(model));
        Button fan = new Button("Flashlight");
        fan.setOnAction(e -> presetFan(model));
        Button orbit = new Button("Planet orbit (precession)");
        orbit.setOnAction(e -> presetOrbit(model));
        Button ring = new Button("Drop a ring of dust");
        ring.setOnAction(e -> presetRing(model));
        Button clear = new Button("Clear");
        clear.setOnAction(e -> particles.clear());
        for (Button b : List.of(beam, fan, orbit, ring, clear)) b.setMaxWidth(Double.MAX_VALUE);

        controls = new VBox(6,
                new Label("Launch speed (drag on the map to shoot)"), speed, speedLabel,
                new Label("Presets"), beam, fan, orbit, ring, clear);
        controls.setPadding(new Insets(8, 0, 0, 0));
    }

    private void updateSpeedLabel() {
        double v = speed.getValue();
        speedLabel.setText(v >= 1 ? "light (c)" : String.format("%.0f%% of the speed of light", v * 100));
    }

    @Override public String name() { return "Light & orbits"; }

    @Override public String description() {
        return "Nothing pulls on these rays and particles: each one follows the straightest path that exists, "
                + "and near the mass the straightest path is bent. Yellow is light, cyan is matter. "
                + "Drag on the map to shoot something yourself.";
    }

    @Override public Node controls() { return controls; }

    @Override public void onMetricChanged(Model model) { particles.clear(); }

    @Override public void onShown(Model model) { if (particles.isEmpty()) presetBeam(model); }

    private void add(Model model, double x, double y, double dx, double dy, double v) {
        Geodesic g = Geodesic.launch(model.metric(), x, y, dx, dy, v);
        Color c = v >= 1 ? Color.rgb(255, 220, 90) : Color.rgb(90, 220, 255);
        Particle p = new Particle(g, c);
        p.trail.add(new double[]{g.x(), g.y()});
        particles.add(p);
    }

    private void presetBeam(Model model) {
        particles.clear();
        double R = model.viewRadius();
        int n = 36;
        for (int i = 0; i < n; i++) {
            double y = -R * 0.95 + 1.9 * R * (i + 0.5) / n;
            add(model, -R * 1.25, y, 1, 0, 1);
        }
    }

    private void presetFan(Model model) {
        particles.clear();
        double R = model.viewRadius();
        double x0 = -R * 0.75, y0 = -R * 0.25;
        for (int i = 0; i <= 40; i++) {
            double a = Math.toRadians(-20 + 50.0 * i / 40);
            add(model, x0, y0, Math.cos(a), Math.sin(a), 1);
        }
    }

    private void presetOrbit(Model model) {
        particles.clear();
        Schwarzschild m = model.metric();
        double r = Math.max(model.viewRadius() * 0.55, m.surface() + 4 * m.mass());
        double v = m.circularOrbitSpeed(r) * 0.80;
        add(model, r, 0, 0, 1, v);
        double r2 = Math.max(model.viewRadius() * 0.3, m.surface() + 2 * m.mass());
        add(model, r2, 0, 0, 1, m.circularOrbitSpeed(r2));
    }

    private void presetRing(Model model) {
        particles.clear();
        double R = model.viewRadius();
        double cx = 0, cy = R * 0.65, rad = R * 0.12;
        for (int i = 0; i < 24; i++) {
            double a = 2 * Math.PI * i / 24;
            add(model, cx + rad * Math.cos(a), cy + rad * Math.sin(a), 0, -1, 0.0);
        }
    }

    @Override public void update(double dt, Model model) {
        double simDt = dt * model.timeScale();
        double rEscape = model.viewRadius() * 3;
        for (Particle p : particles) {
            if (!p.g.isMoving()) continue;
            p.g.advance(simDt, rEscape);
            if (p.trail.size() < TRAIL_MAX) p.trail.add(new double[]{p.g.x(), p.g.y()});
        }
    }

    @Override public void render(GraphicsContext g, Viewport vp, Model model) {
        Backdrop.clear(g, vp);
        Backdrop.drawBody(g, vp, model.metric(), true);
        g.setLineWidth(1.4);
        for (Particle p : particles) {
            if (p.trail.size() < 2) continue;
            g.setStroke(p.g.isMoving() ? p.color : p.color.deriveColor(0, 0.6, 0.6, 0.7));
            g.beginPath();
            double[] first = p.trail.get(0);
            g.moveTo(vp.sx(first[0]), vp.sy(first[1]));
            for (int i = 1; i < p.trail.size(); i++) {
                double[] q = p.trail.get(i);
                g.lineTo(vp.sx(q[0]), vp.sy(q[1]));
            }
            g.stroke();
            if (p.g.isMoving()) {
                g.setFill(p.color);
                double r = p.g.isMassless() ? 2.5 : 4;
                g.fillOval(vp.sx(p.g.x()) - r, vp.sy(p.g.y()) - r, 2 * r, 2 * r);
            }
        }
        if (dragStart != null && dragNow != null) {
            g.setStroke(Color.WHITE);
            g.setLineWidth(1.5);
            g.strokeLine(vp.sx(dragStart[0]), vp.sy(dragStart[1]), vp.sx(dragNow[0]), vp.sy(dragNow[1]));
            g.setFill(Color.WHITE);
            g.fillOval(vp.sx(dragStart[0]) - 3, vp.sy(dragStart[1]) - 3, 6, 6);
        }
        Backdrop.scaleBar(g, vp, model);
        long moving = particles.stream().filter(p -> p.g.isMoving()).count();
        long captured = particles.stream().filter(p -> p.g.status() == Geodesic.Status.CAPTURED).count();
        long hit = particles.stream().filter(p -> p.g.status() == Geodesic.Status.HIT_SURFACE).count();
        String fate = model.metric().isBlackHole() ? "captured by the black hole: " + captured : "hit the star: " + hit;
        Backdrop.hud(g, vp, "moving: " + moving + "   " + fate);
    }

    @Override public void mousePressed(double wx, double wy, Model model) {
        dragStart = new double[]{wx, wy};
        dragNow = dragStart;
    }

    @Override public void mouseDragged(double wx, double wy, Model model) {
        dragNow = new double[]{wx, wy};
    }

    @Override public void mouseReleased(double wx, double wy, Model model) {
        if (dragStart != null) {
            double dx = wx - dragStart[0], dy = wy - dragStart[1];
            if (Math.hypot(dx, dy) < 1e-6) { dx = 1; dy = 0; }
            add(model, dragStart[0], dragStart[1], dx, dy, speed.getValue());
        }
        dragStart = null;
        dragNow = null;
    }
}
