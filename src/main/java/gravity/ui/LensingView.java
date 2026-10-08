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
import javafx.scene.paint.Color;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** What a camera near the body actually sees: the background grid bent into arcs and rings. */
public final class LensingView implements View {

    private static final int MAX_W = 1280, MAX_H = 960;
    private static final int TABLE = 6000;

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
    private AtomicBoolean cancel = new AtomicBoolean();
    private WritableImage image;
    private WritableImage reference;
    private boolean dirty = true, computing;
    private int lastW, lastH;
    private String lastKey = "";
    private double shadowDeg;

    public LensingView(Model model) {
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

    private double cameraDistance() { return Math.pow(10, distance.getValue()); }

    private String distanceText() { return String.format("%.0f M from the centre", cameraDistance()); }

    @Override public void onMetricChanged(Model model) { dirty = true; }

    @Override public void render(GraphicsContext g, Viewport vp, Model model) {
        int w = Math.min(MAX_W, Math.max(2, (int) vp.w()));
        int h = Math.min(MAX_H, Math.max(2, (int) vp.h()));
        if (w != lastW || h != lastH) { lastW = w; lastH = h; dirty = true; }
        if (dirty && !computing) start(model, w, h);
        Backdrop.clear(g, vp);
        if (showSky.isSelected() && reference != null) {
            g.drawImage(reference, 0, 0, vp.w(), vp.h());
        } else if (image != null) {
            g.drawImage(image, 0, 0, vp.w(), vp.h());
        }
        Schwarzschild m = model.metric();
        String what = m.isBlackHole()
                ? String.format("black hole shadow: %.1f degrees across", 2 * shadowDeg)
                : String.format("star of radius %.1f M", m.radius());
        Backdrop.hud(g, vp,
                String.format("camera at %.0f M from the centre, sky squares are %s degrees wide, field of view %.0f degrees",
                        cameraDistance(), Backdrop.fmt(Lensing.cellDegrees(fov.getValue())), fov.getValue()),
                what,
                computing ? "rendering..." : showSky.isSelected() ? "flat space (no mass) for comparison" : "");
    }

    private void start(Model model, int w, int h) {
        dirty = false;
        computing = true;
        cancel.set(false);
        AtomicBoolean myCancel = cancel;
        Schwarzschild m = model.metric();
        double rCam = Math.max(cameraDistance(), m.surface() * 1.05);
        double fovRad = Math.toRadians(fov.getValue());
        double thetaMax = Math.atan(Math.tan(fovRad / 2) * Math.hypot(1, (double) h / w)) + 0.01;
        worker.submit(() -> {
            Lensing lens = new Lensing(m, rCam, thetaMax, TABLE, myCancel);
            if (myCancel.get()) return;
            int[] buf = new int[w * h];
            lens.render(buf, w, h, fovRad);
            double shadow = Math.toDegrees(lens.shadowAngle());
            String key = w + "x" + h + "@" + fovRad;
            int[] ref = null;
            if (!key.equals(lastKey)) {
                // flat-space reference: same camera, no deflection (tiny mass far away behaves as flat)
                Lensing flat = new Lensing(new Schwarzschild(1e-6, 0), rCam, thetaMax, 600, null);
                ref = new int[w * h];
                flat.render(ref, w, h, fovRad);
            }
            int[] refFinal = ref;
            Platform.runLater(() -> {
                if (myCancel.get()) return;
                WritableImage img = new WritableImage(w, h);
                img.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), buf, 0, w);
                image = img;
                if (refFinal != null) {
                    WritableImage r = new WritableImage(w, h);
                    r.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), refFinal, 0, w);
                    reference = r;
                    lastKey = key;
                }
                shadowDeg = shadow;
                info.setText(m.isBlackHole()
                        ? String.format("Shadow radius on screen: %.1f°", shadow)
                        : "No shadow: the surface is visible");
                computing = false;
            });
        });
    }
}
