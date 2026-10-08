package gravity.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeodesicTest {

    private static final Schwarzschild BH = new Schwarzschild(1.0, 0);

    private static Geodesic lightWithImpactParameter(double b, double start) {
        Geodesic g = Geodesic.launch(BH, -start, b, 1, 0, 1);
        g.run(start * 4, 5_000_000);
        return g;
    }

    @Test
    void weakFieldDeflectionIsFourMOverB() {
        for (double b : new double[]{100, 400, 1000}) {
            Geodesic g = lightWithImpactParameter(b, 5000);
            assertEquals(Geodesic.Status.ESCAPED, g.status());
            double deflection = -g.velocityAngle();
            // next-order term is (15 pi / 4) (M/b)^2; allow a bit more than that
            assertEquals(4.0 / b, deflection, 1.5 * 15 * Math.PI / 4 / (b * b) + 1e-4, "b=" + b);
        }
    }

    @Test
    void lightIsCapturedBelowCriticalImpactParameter() {
        double critical = 3 * Math.sqrt(3);
        assertEquals(Geodesic.Status.CAPTURED, lightWithImpactParameter(critical - 0.05, 2000).status());
        assertEquals(Geodesic.Status.ESCAPED, lightWithImpactParameter(critical + 0.05, 2000).status());
    }

    @Test
    void radialLightIsCapturedAndNeverEscapesNumerically() {
        for (double b = 0; b < 5; b += 0.25) {
            assertEquals(Geodesic.Status.CAPTURED, lightWithImpactParameter(b, 500).status(), "b=" + b);
        }
    }

    @Test
    void circularOrbitStaysCircular() {
        double r0 = 40;
        Geodesic g = Geodesic.launch(BH, r0, 0, 0, 1, BH.circularOrbitSpeed(r0));
        double rmin = Double.MAX_VALUE, rmax = 0;
        for (int i = 0; i < 100_000; i++) {
            g.step(0.05);
            rmin = Math.min(rmin, g.r());
            rmax = Math.max(rmax, g.r());
        }
        assertEquals(r0, rmin, 1e-3);
        assertEquals(r0, rmax, 1e-3);
        assertTrue(g.phi() > 4 * Math.PI, "completed several orbits");
    }

    @Test
    void eccentricOrbitPrecessesForward() {
        Geodesic g = Geodesic.launch(BH, 60, 0, 0, 1, BH.circularOrbitSpeed(60) * 0.9);
        double prevR = g.r();
        boolean goingIn = true;
        double lastPeri = Double.NaN;
        double sum = 0;
        int count = 0;
        for (int i = 0; i < 6_000_000 && count < 4; i++) {
            g.step(0.02);
            if (goingIn && g.r() > prevR) {
                goingIn = false;
                if (!Double.isNaN(lastPeri)) { sum += g.phi() - lastPeri - 2 * Math.PI; count++; }
                lastPeri = g.phi();
            } else if (!goingIn && g.r() < prevR) {
                goingIn = true;
            }
            prevR = g.r();
        }
        assertEquals(4, count, "found enough periapsis passages");
        double perOrbit = sum / count;
        assertTrue(perOrbit > 0, "precession is prograde");
        assertTrue(perOrbit < 1.0, "precession per orbit is modest for a wide orbit: " + perOrbit);
    }

    @Test
    void advanceInCoordinateTimeFreezesAtHorizon() {
        Geodesic g = Geodesic.launch(BH, 10, 0, -1, 0, 0.5);
        double before = g.t();
        g.advance(5, 100);
        // the last step is sized from dt/dlambda at its start, so a small overshoot is expected
        assertEquals(before + 5, g.t(), 5e-3);
        for (int i = 0; i < 200; i++) g.advance(5, 100);
        assertTrue(g.r() <= BH.rs() * 1.01, "ended at the horizon, r=" + g.r());
        assertEquals(Geodesic.Status.CAPTURED, g.status());
    }

    @Test
    void particleStopsAtStarSurface() {
        Schwarzschild star = new Schwarzschild(1.0, 4.8);
        Geodesic g = Geodesic.launch(star, 20, 0, -1, 0, 0.3);
        g.run(100, 1_000_000);
        assertEquals(Geodesic.Status.HIT_SURFACE, g.status());
        assertEquals(star.radius(), g.r(), 0.05);
    }
}
