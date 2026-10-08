package gravity.engine;

/**
 * Schwarzschild spacetime around a spherical body of mass M and radius R.
 *
 * Geometric units (G = c = 1): lengths, times and masses are all measured in
 * the same unit. Throughout the app that unit is M itself, so rs = 2.
 *
 * Outside the body the metric is the vacuum Schwarzschild solution.
 * Inside a star we use the interior Schwarzschild solution for a sphere of
 * constant density, which is exact for the clock rate and a good illustration
 * for the rest. A static star cannot be smaller than 9/8 rs (Buchdahl's bound),
 * so a radius below that is treated as a black hole.
 */
public final class Schwarzschild {

    public static final double BUCHDAHL_RATIO = 9.0 / 8.0;

    private final double mass;
    private final double radius;

    public Schwarzschild(double mass, double radius) {
        if (mass <= 0) throw new IllegalArgumentException("mass must be positive");
        this.mass = mass;
        this.radius = Math.max(0, radius);
    }

    public double mass() { return mass; }

    /** Schwarzschild radius, the event horizon of a black hole. */
    public double rs() { return 2 * mass; }

    public double radius() { return radius; }

    public boolean isBlackHole() { return radius < BUCHDAHL_RATIO * rs(); }

    /** Radius at which trajectories stop: the horizon or the surface of the star. */
    public double surface() { return isBlackHole() ? rs() : radius; }

    /** Radius of the circular light orbit (only meaningful if outside the body). */
    public double photonSphere() { return 3 * mass; }

    /** Innermost stable circular orbit for massive particles. */
    public double isco() { return 6 * mass; }

    /** g_tt factor of the exterior metric: 1 - rs/r. */
    public double f(double r) { return 1 - rs() / r; }

    /**
     * Rate of a clock held static at radius r, as seen by a far-away observer
     * (d tau / dt). 1 far away, 0 at the horizon.
     */
    public double lapse(double r) {
        if (isBlackHole() || r >= radius) {
            return r <= rs() ? 0 : Math.sqrt(f(r));
        }
        double a = Math.sqrt(1 - rs() / radius);
        double b = Math.sqrt(1 - rs() * r * r / (radius * radius * radius));
        return Math.max(0, 1.5 * a - 0.5 * b);
    }

    /**
     * Speed at which space "flows" inward in the river model
     * (Gullstrand-Painleve coordinates). Equals the Newtonian escape velocity;
     * reaches 1 (the speed of light) at the horizon.
     */
    public double riverSpeed(double r) {
        if (r <= 0) return 0;
        if (!isBlackHole() && r < radius) {
            return r * Math.sqrt(rs() / (radius * radius * radius));
        }
        return Math.sqrt(rs() / r);
    }

    /**
     * Tidal acceleration per unit separation along the radial direction, in the
     * frame of a freely falling observer: positive means stretching.
     */
    public double tidalRadial(double r) {
        if (!isBlackHole() && r < radius) return -mass / (radius * radius * radius);
        return 2 * mass / (r * r * r);
    }

    /** Tidal acceleration per unit separation across the radial direction: negative means squeezing. */
    public double tidalTangential(double r) {
        if (!isBlackHole() && r < radius) return -mass / (radius * radius * radius);
        return -mass / (r * r * r);
    }

    /** Local-frame speed of a circular orbit at r (massive particle), measured by a static observer. */
    public double circularOrbitSpeed(double r) {
        return Math.sqrt(mass / (r - rs()));
    }
}
