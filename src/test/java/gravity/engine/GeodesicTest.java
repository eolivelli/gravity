package gravity.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeodesicTest {

    private static final Schwarzschild BH = new Schwarzschild(1.0, 0);

    /**
     * Light launched far away, moving in +x, with impact parameter b. The
     * launch direction is measured by a static observer, so the conserved
     * L/E = y / sqrt(f(start)); the offset is corrected to make it exactly b.
     */
    private static Geodesic lightWithImpactParameter(double b, double start) {
        double y = b * Math.sqrt(BH.f(Math.hypot(start, b)));
        Geodesic g = Geodesic.launch(BH, -start, y, 1, 0, 1);
        g.run(start * 4, 5_000_000);
        return g;
    }

    /**
     * Exact deflection of light in Schwarzschild for impact parameter b:
     * 2 * integral_0^u0 du / sqrt(1/b^2 - u^2 + 2M u^3) - pi, with u0 the
     * turning point. The substitution u = u0 - w^2 removes the endpoint
     * singularity; Simpson's rule then converges quickly.
     */
    static double exactDeflection(double b, double M) {
        double lo = 0, hi = 1 / (3 * M);
        for (int i = 0; i < 200; i++) {
            double mid = 0.5 * (lo + hi);
            double p = 2 * M * mid * mid * mid - mid * mid + 1 / (b * b);
            if (p > 0) lo = mid; else hi = mid;
        }
        double u0 = 0.5 * (lo + hi);
        // P(u) = (u - u0) Q(u), Q(u) = 2M u^2 + (2M u0 - 1) u + (2M u0^2 - u0)
        int n = 20000;
        double wMax = Math.sqrt(u0), h = wMax / n, sum = 0;
        for (int i = 0; i <= n; i++) {
            double w = i * h, u = u0 - w * w;
            double q = 2 * M * u * u + (2 * M * u0 - 1) * u + (2 * M * u0 * u0 - u0);
            double f = 2 / Math.sqrt(-q);
            sum += (i == 0 || i == n) ? f : (i % 2 == 1 ? 4 * f : 2 * f);
        }
        return 2 * sum * h / 3 - Math.PI;
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
    void strongFieldDeflectionMatchesExactIntegral() {
        for (double b : new double[]{5.4, 6, 8, 15}) {
            Geodesic g = lightWithImpactParameter(b, 20000);
            assertEquals(Geodesic.Status.ESCAPED, g.status(), "b=" + b);
            double measured = -g.velocityAngle();
            double exact = exactDeflection(b, 1.0);
            // velocityAngle wraps at +-pi; deflections here exceed pi only for b close to critical
            measured = measured - 2 * Math.PI * Math.round((measured - exact) / (2 * Math.PI));
            assertEquals(exact, measured, 3e-4, "b=" + b);   // finite start/escape radius limits accuracy
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
    void fastParticlesAreNotFalselyCaptured() {
        for (double v : new double[]{0.9, 0.99, 0.999, 0.9999}) {
            Geodesic out = Geodesic.launch(BH, 40, 0, 1, 0, v);
            out.run(1000, 1_000_000);
            assertEquals(Geodesic.Status.ESCAPED, out.status(), "outward v=" + v);
            Geodesic in = Geodesic.launch(BH, 10, 0, -1, 0, v);
            in.run(1000, 1_000_000);
            assertEquals(Geodesic.Status.CAPTURED, in.status(), "inward v=" + v);
            assertEquals(BH.rs(), in.r(), BH.rs() * 2 * Geodesic.HORIZON_MARGIN);
        }
    }

    @Test
    void hamiltonianIsConserved() {
        Geodesic light = Geodesic.launch(BH, -500, 6, 1, 0, 1);
        light.run(2000, 1_000_000);
        assertEquals(0.0, light.hamiltonian(), 1e-9, "light stays null");

        Geodesic orbit = Geodesic.launch(BH, 30, 0, 0, 1, BH.circularOrbitSpeed(30) * 0.9);
        assertEquals(-0.5, orbit.hamiltonian(), 1e-12);
        for (int i = 0; i < 200_000; i++) orbit.step(0.05);
        assertTrue(orbit.isMoving() && orbit.r() > 10, "bound orbit stays outside, r=" + orbit.r());
        assertEquals(-0.5, orbit.hamiltonian(), 1e-7, "massive particle after a long run");
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
    void eccentricOrbitPrecessesBySixPiMOverSemiLatusRectum() {
        double apo = 120;
        Geodesic g = Geodesic.launch(BH, apo, 0, 0, 1, BH.circularOrbitSpeed(apo) * 0.85);
        double prevR = g.r(), peri = apo;
        boolean goingIn = true;
        double lastPeri = Double.NaN, sum = 0;
        int count = 0;
        for (int i = 0; i < 20_000_000 && count < 3; i++) {
            g.step(0.2);
            peri = Math.min(peri, g.r());
            if (goingIn && g.r() > prevR) {
                goingIn = false;
                if (!Double.isNaN(lastPeri)) { sum += g.phi() - lastPeri - 2 * Math.PI; count++; }
                lastPeri = g.phi();
            } else if (!goingIn && g.r() < prevR) {
                goingIn = true;
            }
            prevR = g.r();
        }
        assertEquals(3, count, "found enough periapsis passages");
        double p = 2 * apo * peri / (apo + peri);   // semi-latus rectum a (1 - e^2)
        double e = (apo - peri) / (apo + peri);
        // leading term plus the known second-order correction (3 pi / 2) (18 + e^2) (M/p)^2
        double expected = 6 * Math.PI / p + 1.5 * Math.PI * (18 + e * e) / (p * p);
        double measured = sum / count;
        assertTrue(measured > 0, "precession is prograde");
        assertEquals(expected, measured, expected * 0.01, "p=" + p + " e=" + e);
    }

    @Test
    void advanceInCoordinateTimeSlowsDownAndFreezesAtHorizon() {
        Geodesic g = Geodesic.launch(BH, 10, 0, -1, 0, 0.5);
        double before = g.t();
        g.advance(5, 100);
        // the last step is sized from dt/dlambda at its start, so a small overshoot is expected
        assertEquals(before + 5, g.t(), 5e-3);
        double prevGap = g.r() - BH.rs();
        boolean sawSlowApproach = false;
        for (int i = 0; i < 400 && g.isMoving(); i++) {
            g.advance(1, 100);
            double gap = g.r() - BH.rs();
            assertTrue(gap > 0, "never crosses the horizon in finite far-away time");
            // once close, the remaining gap shrinks roughly exponentially in t, never jumps to zero
            if (prevGap < 0.1 && gap > prevGap * 0.5) sawSlowApproach = true;
            prevGap = gap;
        }
        assertTrue(sawSlowApproach, "approach to the horizon is gradual");
        assertEquals(Geodesic.Status.CAPTURED, g.status());
        assertEquals(BH.rs(), g.r(), BH.rs() * 2 * Geodesic.HORIZON_MARGIN);
    }

    @Test
    void particleStopsExactlyOnStarSurface() {
        Schwarzschild star = new Schwarzschild(1.0, 4.8);
        Geodesic g = Geodesic.launch(star, 20, 0, -1, 0, 0.3);
        g.run(100, 1_000_000);
        assertEquals(Geodesic.Status.HIT_SURFACE, g.status());
        assertEquals(star.radius(), g.r(), 1e-9);

        // a grazing ray lands where a finely integrated one does
        Geodesic coarse = Geodesic.launch(star, -200, 4.79, 1, 0, 1);
        coarse.run(1000, 10_000_000);
        Geodesic fine = Geodesic.launch(star, -200, 4.79, 1, 0, 1);
        while (fine.isMoving()) {
            double rb = fine.r();
            fine.step(1e-3);
            if (fine.r() <= star.radius() || fine.r() > 1000) break;
            assertTrue(Double.isFinite(rb));
        }
        assertEquals(Geodesic.Status.HIT_SURFACE, coarse.status());
        assertEquals(fine.phi(), coarse.phi(), 2e-3, "hit longitude (radians)");
    }
}
