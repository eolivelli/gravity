package gravity.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LensingTest {

    @Test
    void shadowAngleMatchesTheory() {
        Schwarzschild bh = new Schwarzschild(1.0, 0);
        double rCam = 25;
        Lensing lens = new Lensing(bh, rCam, 0.6, 3000, null);
        double expected = Math.asin(3 * Math.sqrt(3) * Math.sqrt(bh.f(rCam)) / rCam);
        assertEquals(expected, lens.shadowAngle(), 1e-3);
    }

    @Test
    void starHasNoShadowAndRendersSurfaceInTheMiddle() {
        Schwarzschild star = new Schwarzschild(1.0, 4.8);
        Lensing lens = new Lensing(star, 100, 0.3, 1000, null);
        assertEquals(0.0, lens.shadowAngle(), 1e-12);
        int w = 64, h = 48;
        int[] px = new int[w * h];
        lens.render(px, w, h, Math.toRadians(20));
        int centre = px[(h / 2) * w + w / 2];
        int r = (centre >> 16) & 255, g = (centre >> 8) & 255, b = centre & 255;
        assertTrue(r > 150 && r > b + 50, "centre pixel is orange-ish: " + Integer.toHexString(centre));
        assertTrue(g > 40);
    }

    @Test
    void blackHoleRendersBlackCentreAndOpaquePixels() {
        Lensing lens = new Lensing(new Schwarzschild(1.0, 0), 400, 0.3, 1000, null);
        int w = 64, h = 48;
        int[] px = new int[w * h];
        lens.render(px, w, h, Math.toRadians(30));
        assertEquals(0xFF000000, px[(h / 2) * w + w / 2]);
        for (int p : px) assertEquals(0xFF, (p >>> 24), "opaque");
    }

    @Test
    void cellSizeIsANiceFractionOfTheFieldOfView() {
        assertEquals(2.5, Lensing.cellDegrees(30), 1e-12);
        assertEquals(10, Lensing.cellDegrees(120), 1e-12);
        assertEquals(0.5, Lensing.cellDegrees(5), 1e-12);
    }
}
