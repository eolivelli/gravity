package gravity.ui;

import javafx.scene.Node;
import javafx.scene.canvas.GraphicsContext;

/** One way of looking at the curvature. All views share the Model and the canvas. */
public interface View {

    String name();

    /** One or two plain-language sentences shown under the canvas. */
    String description();

    /** Controls specific to this view, or null. */
    Node controls();

    /** Advance the animation by dt seconds of real time. */
    default void update(double dt, Model model) {}

    void render(GraphicsContext g, Viewport vp, Model model);

    default void onMetricChanged(Model model) {}
    default void onShown(Model model) {}

    /** False while the view still has background work pending for the current parameters. */
    default boolean ready() { return true; }

    /** Release threads and other resources when the application closes. */
    default void dispose() {}

    default void mousePressed(double wx, double wy, Model model) {}
    default void mouseDragged(double wx, double wy, Model model) {}
    default void mouseReleased(double wx, double wy, Model model) {}
}
