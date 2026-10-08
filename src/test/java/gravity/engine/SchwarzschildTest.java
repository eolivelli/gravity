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
    void interiorClockRateMatchesConstantDensitySolution() {
        double R = 4.8;
        Schwarzschild star = new Schwarzschild(M, R);
        double rs = 2 * M;
        for (double r : new double[]{0, R / 3, R / 2, 0.9 * R}) {
            double expected = 1.5 * Math.sqrt(1 - rs / R) - 0.5 * Math.sqrt(1 - rs * r * r / (R * R * R));
            assertEquals(expected, star.lapse(r), 1e-12, "r=" + r);
        }
        assertEquals(star.lapse(R + 1e-9), star.lapse(R - 1e-9), 1e-6, "continuous at the surface");
        assertTrue(star.lapse(0) < star.lapse(R), "centre runs slower than surface");
    }

    @Test
    void clockStopsAtCentreOfBuchdahlStar() {
        Schwarzschild star = new Schwarzschild(M, Schwarzschild.BUCHDAHL_RATIO * 2 * M + 1e-9);
        assertEquals(0.0, star.lapse(0), 1e-4);
    }

    @Test
    void riverReachesSpeedOfLightAtHorizon() {
        Schwarzschild bh = new Schwarzschild(M, 0);
        assertEquals(1.0, bh.riverSpeed(bh.rs()), 1e-12);
        assertEquals(Math.sqrt(2.0 / 8), bh.riverSpeed(8), 1e-12);
    }

    @Test
    void riverInsideStarIsContinuousAndKeepsGrowingInward() {
        Schwarzschild star = new Schwarzschild(M, 4.8);
        double R = star.radius();
        assertEquals(star.riverSpeed(R + 1e-9), star.riverSpeed(R - 1e-9), 1e-6);
        // the river is the speed of an observer fallen from rest at infinity: sqrt(1 - lapse^2)
        double a0 = star.lapse(0);
        assertEquals(Math.sqrt(1 - a0 * a0), star.riverSpeed(0), 1e-12);
        assertTrue(star.riverSpeed(0) > star.riverSpeed(R / 2));
        assertTrue(star.riverSpeed(R / 2) > star.riverSpeed(R));
        assertTrue(star.riverSpeed(0) < 1);
    }

    @Test
    void tidalTensorIsTraceFreeOutsideAndIsotropicInside() {
        Schwarzschild star = new Schwarzschild(M, 4.8);
        double r = 10;
        assertEquals(0.0, star.tidalRadial(r) + 2 * star.tidalTangential(r), 1e-12);
        assertEquals(2 * M / 1000, star.tidalRadial(r), 1e-12);
        assertEquals(star.tidalRadial(1), star.tidalTangential(1), 1e-12);
        assertTrue(star.tidalRadial(1) < 0);
    }

    @Test
    void circularOrbitSpeedIsUndefinedInsidePhotonSphere() {
        Schwarzschild bh = new Schwarzschild(M, 0);
        assertTrue(Double.isNaN(bh.circularOrbitSpeed(2.5)));
        assertEquals(0.5, bh.circularOrbitSpeed(6), 1e-12);   // ISCO: v = 1/2
    }
}
