package gravity.ui;

import gravity.engine.Geodesic;
import gravity.engine.Schwarzschild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitPresetTest {

    @Test
    void presetOrbitStaysBoundForAllZoomLevels() {
        for (Schwarzschild m : new Schwarzschild[]{new Schwarzschild(1, 0), new Schwarzschild(1, 4.8), new Schwarzschild(1, 10)}) {
            for (double viewRadius : new double[]{Model.MIN_VIEW_RADIUS, 10, Model.DEFAULT_VIEW_RADIUS, 100, Model.MAX_VIEW_RADIUS}) {
                double r = Math.max(viewRadius * 0.55, Math.max(m.surface() + 4 * m.mass(), m.isco() + 4 * m.mass()));
                double v = GeodesicView.boundOrbitSpeed(m, r, 0.80);
                Geodesic g = Geodesic.launch(m, r, 0, 0, 1, v);
                for (int i = 0; i < 400 && g.isMoving(); i++) g.advance(50, 1e6);
                assertTrue(g.isMoving(), "orbit at r=" + r + " (view " + viewRadius + ", body " + m.radius() + ") ended " + g.status());
                assertTrue(v < m.circularOrbitSpeed(r), "orbit is eccentric, not circular");
            }
        }
    }
}
