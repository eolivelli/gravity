package gravity.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchwarzschildTest {

    private static final double M = 1.0;

    @Test
    void blackHoleBelowBuchdahlBound() {
        assertTrue(new Schwarzschild(M, 0).isBlackHole());
        assertTrue(new Schwarzschild(M, 2.2).isBlackHole());      // 1.1 rs < 9/8 rs
        assertFalse(new Schwarzschild(M, 2.4 * 2).isBlackHole()); // neutron star
    }

    @Test
    void clockRateFarAwayIsOneAndZeroAtHorizon() {
        Schwarzschild bh = new Schwarzschild(M, 0);
        assertEquals(1.0, bh.lapse(1e9), 1e-8);
        assertEquals(0.0, bh.lapse(bh.rs()), 1e-12);
        assertEquals(Math.sqrt(0.5), bh.lapse(4), 1e-12);
    }

    @Test
    void clockRateIsContinuousAtStarSurface() {
        Schwarzschild star = new Schwarzschild(M, 4.8);
        double r = star.radius();
        assertEquals(star.lapse(r + 1e-9), star.lapse(r - 1e-9), 1e-6);
        assertTrue(star.lapse(0) < star.lapse(r), "centre runs slower than surface");
        assertTrue(star.lapse(0) > 0);
    }

    @Test
    void clockStopsAtCentreOfBuchdahlStar() {
        Schwarzschild star = new Schwarzschild(M, Schwarzschild.BUCHDAHL_RATIO * 2 * M + 1e-9);
        assertEquals(0.0, star.lapse(0), 1e-4);
    }

    @Test
    void riverReachesSpeedOfLightAtHorizonAndIsContinuousAtStarSurface() {
        Schwarzschild bh = new Schwarzschild(M, 0);
        assertEquals(1.0, bh.riverSpeed(bh.rs()), 1e-12);
        Schwarzschild star = new Schwarzschild(M, 4.8);
        double r = star.radius();
        assertEquals(star.riverSpeed(r + 1e-9), star.riverSpeed(r - 1e-9), 1e-6);
        assertEquals(0.0, star.riverSpeed(0), 1e-12);
    }

    @Test
    void tidalTensorIsTraceFreeOutsideAndIsotropicInside() {
        Schwarzschild star = new Schwarzschild(M, 4.8);
        double r = 10;
        assertEquals(0.0, star.tidalRadial(r) + 2 * star.tidalTangential(r), 1e-12);
        assertEquals(star.tidalRadial(1), star.tidalTangential(1), 1e-12);
        assertTrue(star.tidalRadial(1) < 0);
    }
}
