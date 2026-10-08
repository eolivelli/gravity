package gravity.ui;

import gravity.engine.Lensing;
import gravity.engine.Schwarzschild;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * What a camera near the body actually sees: the background grid bent into
 * arcs and rings.
 *
 * Threading: render() runs on the FX thread and, when parameters changed,
 * submits a job to a single worker. Each job carries a generation number and
 * a cancel flag; a newer job cancels the older one, and a job's result is
 * applied (on the FX thread) only if it is still the newest generation.
 */
public final class LensingView implements View {

    private static final int MAX_W = 1280, MAX_H = 960;
    private static final int TABLE = 6000;
    private static final int REFERENCE_TABLE = 600;

    private final Slider distance = new Slider(Math.log10(5), Math.log10(3000), Math.log10(400));  // log10 of M
    private final Slider fov = new Slider(2, 120, 30);
    private final Label distanceLabel = new Label();
    private final CheckBox showSky = new CheckBox("Show the undistorted sky");
    private final Label info = new Label();
    private final VBox controls;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lensing");
        t.setDaemon(true);
        return t;
    });

    // FX-thread state
    private boolean dirty = true;
    private int generation, appliedGeneration = -1;
    private AtomicBoolean cancelCurrent;
    private WritableImage image, reference;
    private String referenceKey = "";
    private int lastW, lastH;
    private double shadowDeg, effectiveCamera;

    public LensingView() {
        distance.valueProperty().addListener((o, a, b) -> { dirty = true; distanceLabel.setText(distanceText()); });
        fov.valueProperty().addListener((o, a, b) -> dirty = true);
        distanceLabel.setText(distanceText());
        controls = new VBox(6,
                new Label("Camera distance"), distance, distanceLabel,
                new Label("Field of view (degrees)"), fov,
                showSky, info,
                new Label("Far away the black hole looks\ntiny but the ring around it\nis much bigger: that ring is\nthe sky directly behind it."));
        controls.setPadding(new Insets(8, 0, 0, 0));
    }

    @Override public String name() { return "Through a camera"; }

    @Override public String description() {
        return "This is what you would see standing near the mass with a camera, looking at a checkerboard sky. "
                + "Light from behind bends around the mass, so the sky is stretched into arcs, the spot right "
                + "behind the body becomes a ring, and a black hole shows a dark shadow.";
    }

    @Override public Node controls() { return controls; }

    @Override public void onMetricChanged(Model model) { dirty = true; }

    @Override public boolean ready() { return !dirty && appliedGeneration == generation; }

    @Override public void dispose() {
        if (cancelCurrent != null) cancelCurrent.set(true);
        worker.shutdownNow();
    }

    private double cameraDistance() { return Math.pow(10, distance.getValue()); }

    private String distanceText() { return String.format("%.0f M from the centre", cameraDistance()); }

    @Override public void render(GraphicsContext g, Viewport vp, Model model) {
        int w = Math.min(MAX_W, Math.max(2, (int) vp.w()));
        int h = Math.min(MAX_H, Math.max(2, (int) vp.h()));
        if (w != lastW || h != lastH) { lastW = w; lastH = h; dirty = true; }
        if (dirty) start(model, w, h);
        Backdrop.clear(g, vp);
        if (showSky.isSelected() && reference != null) {
            g.drawImage(reference, 0, 0, vp.w(), vp.h());
        } else if (image != null) {
            g.drawImage(image, 0, 0, vp.w(), vp.h());
        }
        Schwarzschild m = model.metric();
        boolean rendering = appliedGeneration != generation;
        String what = !m.isBlackHole() ? String.format("star of radius %.1f M", m.radius())
                : Double.isNaN(shadowDeg) ? "black hole shadow fills the whole field of view"
                : String.format("black hole shadow: %.1f degrees across", 2 * shadowDeg);
        Backdrop.hud(g, vp,
                String.format("camera at %.0f M from the centre, sky squares are %s degrees wide, field of view %.0f degrees",
                        effectiveCamera, Backdrop.fmt(Lensing.cellDegrees(fov.getValue())), fov.getValue()),
                what,
                rendering ? "rendering..." : showSky.isSelected() ? "flat space (no mass) for comparison" : "");
    }

    private void start(Model model, int w, int h) {
        dirty = false;
        if (cancelCurrent != null) cancelCurrent.set(true);
        AtomicBoolean cancel = new AtomicBoolean();
        cancelCurrent = cancel;
        int gen = ++generation;

        Schwarzschild m = model.metric();
        double rCam = Math.max(cameraDistance(), m.surface() * 1.05);
        effectiveCamera = rCam;
        double fovRad = Math.toRadians(fov.getValue());
        double thetaMax = Math.atan(Math.tan(fovRad / 2) * Math.hypot(1, (double) h / w)) + 0.01;
        String key = w + "x" + h + "@" + fovRad + "@" + rCam;
        boolean needReference = !key.equals(referenceKey);

        worker.execute(() -> {
            try {
                if (cancel.get()) return;
                Lensing lens = new Lensing(m, rCam, thetaMax, TABLE, cancel);
                if (cancel.get()) return;
                int[] buf = new int[w * h];
                lens.render(buf, w, h, fovRad, cancel);
                if (cancel.get()) return;
                double shadow = Math.toDegrees(lens.shadowAngle());
                int[] ref = null;
                if (needReference) {
                    // flat-space reference: same camera, negligible mass
                    Lensing flat = new Lensing(new Schwarzschild(1e-6, 0), rCam, thetaMax, REFERENCE_TABLE, cancel);
                    ref = new int[w * h];
                    flat.render(ref, w, h, fovRad, cancel);
                }
                if (cancel.get()) return;
                int[] refFinal = ref;
                Platform.runLater(() -> {
                    if (gen != generation) return;
                    image = toImage(buf, w, h);
                    if (refFinal != null) { reference = toImage(refFinal, w, h); referenceKey = key; }
                    shadowDeg = shadow;
                    info.setText(!m.isBlackHole() ? "No shadow: the surface is visible"
                            : Double.isNaN(shadow) ? "Shadow fills the view"
                            : String.format("Shadow radius on screen: %.1f°", shadow));
                    appliedGeneration = gen;
                });
            } catch (Throwable t) {
                System.err.println("lensing render failed: " + t);
                Platform.runLater(() -> { if (gen == generation) appliedGeneration = gen; });
            }
        });
    }

    private static WritableImage toImage(int[] argb, int w, int h) {
        WritableImage img = new WritableImage(w, h);
        img.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), argb, 0, w);
        return img;
    }
}
