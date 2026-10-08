package gravity.ui;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Spacetime curvature around a spherical mass, shown five ways.
 *
 * Keys: 1-5 switch view, S saves a PNG of the canvas into ./snapshots.
 * Set GRAVITY_SNAPSHOT_DIR to render every view once to that directory and
 * exit; GRAVITY_RADIUS_RS presets the body radius (in rs) for that run.
 */
public final class App extends Application {

    private static final double SNAPSHOT_MIN_WAIT_S = 2.5, SNAPSHOT_MAX_WAIT_S = 90;

    private final Model model = new Model();
    private List<View> views;
    private View current;
    private Canvas canvas;
    private VBox viewControls;
    private Label description;
    private ToggleGroup viewGroup;
    private AnimationTimer frameTimer;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        views = List.of(new GeodesicView(model), new LensingView(), new FogView(), new RiverView(), new TidalView());

        canvas = new Canvas(900, 700);
        Pane canvasPane = new Pane(canvas);
        canvasPane.setMinSize(200, 200);
        canvas.widthProperty().bind(canvasPane.widthProperty());
        canvas.heightProperty().bind(canvasPane.heightProperty());

        canvas.setOnMousePressed(e -> { Viewport vp = viewport(); current.mousePressed(vp.wx(e.getX()), vp.wy(e.getY()), model); });
        canvas.setOnMouseDragged(e -> { Viewport vp = viewport(); current.mouseDragged(vp.wx(e.getX()), vp.wy(e.getY()), model); });
        canvas.setOnMouseReleased(e -> { Viewport vp = viewport(); current.mouseReleased(vp.wx(e.getX()), vp.wy(e.getY()), model); });
        canvas.setOnScroll(e -> {
            if (e.getDeltaY() != 0) model.setViewRadius(model.viewRadius() * (e.getDeltaY() > 0 ? 0.85 : 1.18));
        });

        description = new Label();
        description.setWrapText(true);
        description.setFont(Font.font("Sans", 13));
        description.setPadding(new Insets(8, 12, 10, 12));
        description.setStyle("-fx-background-color: #161a28; -fx-text-fill: #e8e8f0;");

        BorderPane root = new BorderPane();
        root.setTop(buildParameterBar());
        root.setLeft(buildSidebar());
        root.setCenter(canvasPane);
        root.setBottom(description);
        root.setStyle("-fx-background-color: #1e2232;");

        model.onMetricChanged(() -> views.forEach(v -> v.onMetricChanged(model)));

        Scene scene = new Scene(root, 1280, 860);
        scene.getRoot().setStyle("-fx-base: #2a2f42; -fx-background: #1e2232; -fx-text-fill: #e8e8f0;");
        KeyCode[] digits = {KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4, KeyCode.DIGIT5};
        for (int i = 0; i < views.size() && i < digits.length; i++) {
            int idx = i;
            scene.getAccelerators().put(new KeyCodeCombination(digits[i]), () -> select(idx));
        }
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S), () -> snapshot(new File("snapshots"), current.name()));

        stage.setTitle("Curved spacetime around a mass");
        stage.setScene(scene);
        stage.show();
        canvasPane.requestFocus();

        select(0);
        frameTimer = new AnimationTimer() {
            long last;
            @Override public void handle(long now) {
                double dt = last == 0 ? 0 : Math.min(0.1, (now - last) / 1e9);
                last = now;
                current.update(dt, model);
                GraphicsContext g = canvas.getGraphicsContext2D();
                current.render(g, viewport(), model);
            }
        };
        frameTimer.start();

        String snapshotDir = System.getenv("GRAVITY_SNAPSHOT_DIR");
        if (snapshotDir != null && !snapshotDir.isBlank()) snapshotAll(new File(snapshotDir), 0);
    }

    @Override
    public void stop() {
        if (frameTimer != null) frameTimer.stop();
        if (views != null) views.forEach(View::dispose);
    }

    private Viewport viewport() { return new Viewport(canvas.getWidth(), canvas.getHeight(), model.viewRadius()); }

    private HBox buildParameterBar() {
        Label massLabel = new Label();
        Slider mass = new Slider(0, 9, 1);   // log10 of solar masses
        mass.setPrefWidth(200);
        mass.valueProperty().addListener((o, a, b) -> {
            model.setMassSolar(Math.pow(10, b.doubleValue()));
            massLabel.setText(formatMass(model.massSolar()));
        });
        model.setMassSolar(10);
        massLabel.setText(formatMass(model.massSolar()));

        Label radiusLabel = new Label();
        Slider radius = new Slider(0, 8, 0);
        radius.setPrefWidth(200);
        radius.valueProperty().addListener((o, a, b) -> {
            model.setRadiusInRs(b.doubleValue());
            radiusLabel.setText(formatRadius());
        });
        radiusLabel.setText(formatRadius());
        String presetRadius = System.getenv("GRAVITY_RADIUS_RS");
        if (presetRadius != null && !presetRadius.isBlank()) radius.setValue(Double.parseDouble(presetRadius));

        Button bh = new Button("Black hole");
        bh.setOnAction(e -> radius.setValue(0));
        Button ns = new Button("Neutron star");
        ns.setOnAction(e -> radius.setValue(2.4));
        Button dense = new Button("Dense star");
        dense.setOnAction(e -> radius.setValue(5));

        Label zoomLabel = new Label();
        Slider zoom = new Slider(Math.log(Model.MIN_VIEW_RADIUS), Math.log(Model.MAX_VIEW_RADIUS), Math.log(model.viewRadius()));
        zoom.setPrefWidth(140);
        zoom.valueProperty().addListener((o, a, b) -> model.setViewRadius(Math.exp(b.doubleValue())));
        model.viewRadiusProperty().addListener((o, a, b) -> {
            zoomLabel.setText(String.format("map: %.0f M", b.doubleValue()));
            double v = Math.log(b.doubleValue());
            if (Math.abs(v - zoom.getValue()) > 1e-9) zoom.setValue(v);
        });
        zoomLabel.setText(String.format("map: %.0f M", model.viewRadius()));

        Label speedLabel = new Label();
        Slider speed = new Slider(0, 2, Math.log10(model.timeScale()));
        speed.setPrefWidth(120);
        speed.valueProperty().addListener((o, a, b) -> {
            model.setTimeScale(Math.pow(10, b.doubleValue()));
            speedLabel.setText(String.format("speed: %.0f M/s", model.timeScale()));
        });
        speedLabel.setText(String.format("speed: %.0f M/s", model.timeScale()));

        HBox bar = new HBox(10,
                new Label("Mass"), mass, massLabel, new Separator(Orientation.VERTICAL),
                new Label("Body radius"), radius, radiusLabel, bh, ns, dense, new Separator(Orientation.VERTICAL),
                zoom, zoomLabel, new Separator(Orientation.VERTICAL),
                speed, speedLabel);
        bar.setPadding(new Insets(8, 12, 8, 12));
        bar.setStyle("-fx-background-color: #161a28; -fx-alignment: center-left;");
        return bar;
    }

    private VBox buildSidebar() {
        viewGroup = new ToggleGroup();
        VBox box = new VBox(4);
        box.setPadding(new Insets(10));
        box.setPrefWidth(230);
        box.setStyle("-fx-background-color: #161a28;");
        Label title = new Label("Views");
        title.setFont(Font.font("Sans", 13));
        box.getChildren().add(title);
        for (int i = 0; i < views.size(); i++) {
            ToggleButton b = new ToggleButton((i + 1) + ". " + views.get(i).name());
            b.setMaxWidth(Double.MAX_VALUE);
            b.setToggleGroup(viewGroup);
            box.getChildren().add(b);
        }
        viewGroup.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) { a.setSelected(true); return; }   // keep one view always selected
            int idx = viewGroup.getToggles().indexOf(b);
            if (views.get(idx) != current) select(idx);
        });
        viewControls = new VBox();
        box.getChildren().addAll(new Separator(), viewControls);
        return box;
    }

    private void select(int index) {
        current = views.get(index);
        viewGroup.getToggles().get(index).setSelected(true);
        viewControls.getChildren().clear();
        if (current.controls() != null) viewControls.getChildren().add(current.controls());
        description.setText(current.description());
        current.onShown(model);
    }

    private String formatMass(double solar) {
        if (solar < 10) return String.format("%.1f suns", solar);
        if (solar < 1e6) return String.format("%,.0f suns", solar);
        return String.format("%.1f million suns", solar / 1e6);
    }

    private String formatRadius() {
        if (model.metric().isBlackHole()) return "black hole";
        return String.format("%.1f rs = %s", model.radiusInRs(), Backdrop.fmtKm(model.metric().radius() * model.kmPerM()));
    }

    private File snapshot(File dir, String name) {
        try {
            dir.mkdirs();
            WritableImage img = canvas.snapshot(null, null);
            File out = new File(dir, name.replaceAll("[^A-Za-z0-9]+", "-").toLowerCase() + ".png");
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
            System.out.println("saved " + out);
            return out;
        } catch (IOException e) {
            System.err.println("snapshot failed: " + e);
            return null;
        }
    }

    /** Snapshot mode: show each view, wait until it is ready (with a cap), save, move on, then exit. */
    private void snapshotAll(File dir, int index) {
        if (index >= views.size()) { Platform.exit(); return; }
        select(index);
        long started = System.nanoTime();
        PauseTransition poll = new PauseTransition(Duration.millis(250));
        poll.setOnFinished(e -> {
            double waited = (System.nanoTime() - started) / 1e9;
            if ((waited >= SNAPSHOT_MIN_WAIT_S && current.ready()) || waited >= SNAPSHOT_MAX_WAIT_S) {
                if (!current.ready()) System.err.println("warning: " + current.name() + " was not ready after " + waited + " s");
                snapshot(dir, (index + 1) + "-" + views.get(index).name());
                snapshotAll(dir, index + 1);
            } else {
                poll.playFromStart();
            }
        });
        poll.play();
    }
}
