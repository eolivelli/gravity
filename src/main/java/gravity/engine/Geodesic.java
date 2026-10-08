package gravity.engine;

/**
 * A geodesic in the equatorial plane of Schwarzschild spacetime, integrated
 * numerically with a 4th order Runge-Kutta scheme.
 *
 * State: (r, phi, p_r, t) as functions of the affine parameter lambda
 * (proper time for massive particles). Energy E and angular momentum L are
 * conserved. Hamiltonian H = 1/2 (-E^2/f + f p_r^2 + L^2/r^2):
 *   dr/dl   = f p_r
 *   dphi/dl = L / r^2
 *   dp_r/dl = -1/2 f' (E^2/f^2 + p_r^2) + L^2 / r^3
 *   dt/dl   = E / f
 */
public final class Geodesic {

    public enum Status { MOVING, CAPTURED, HIT_SURFACE, ESCAPED }

    private final Schwarzschild m;
    private final boolean massless;
    private final double energy;
    private final double angularMomentum;

    private double r, phi, pr, t;
    private Status status = Status.MOVING;

    private final double[] k1 = new double[4], k2 = new double[4], k3 = new double[4], k4 = new double[4];

    private Geodesic(Schwarzschild m, boolean massless, double energy, double angularMomentum,
                     double r, double phi, double pr) {
        this.m = m;
        this.massless = massless;
        this.energy = energy;
        this.angularMomentum = angularMomentum;
        this.r = r;
        this.phi = phi;
        this.pr = pr;
    }

    /**
     * Launch from world position (x, y) in direction (dx, dy) with speed v as
     * measured by a static observer at that point. v = 1 launches light.
     */
    public static Geodesic launch(Schwarzschild m, double x, double y, double dx, double dy, double v) {
        double r = Math.hypot(x, y);
        double phi = Math.atan2(y, x);
        double n = Math.hypot(dx, dy);
        if (n == 0) { dx = 1; dy = 0; n = 1; }
        dx /= n; dy /= n;
        double cr = Math.cos(phi), sr = Math.sin(phi);
        double cosA = dx * cr + dy * sr;      // component along the outward radial direction
        double sinA = -dx * sr + dy * cr;     // component along +phi
        boolean massless = v >= 1;
        double speed = massless ? 1 : Math.max(0, v);
        double gamma = massless ? 1 : 1 / Math.sqrt(1 - speed * speed);
        if (r <= m.surface() * (1 + 1e-6)) {
            Geodesic g = new Geodesic(m, massless, 1, 0, r, phi, 0);
            g.status = m.isBlackHole() ? Status.CAPTURED : Status.HIT_SURFACE;
            return g;
        }
        double sf = Math.sqrt(m.f(r));
        double energy = gamma * sf;
        double angularMomentum = gamma * speed * r * sinA;
        double pr = gamma * speed * cosA / sf;
        return new Geodesic(m, massless, energy, angularMomentum, r, phi, pr);
    }

    public Status status() { return status; }
    public boolean isMoving() { return status == Status.MOVING; }
    public boolean isMassless() { return massless; }
    public double r() { return r; }
    public double phi() { return phi; }
    public double t() { return t; }
    public double x() { return r * Math.cos(phi); }
    public double y() { return r * Math.sin(phi); }

    /** Angle (in the plane, from the +x axis) of the direction of motion. */
    public double velocityAngle() {
        double dr = m.f(r) * pr;
        double rdphi = angularMomentum / r;
        double c = Math.cos(phi), s = Math.sin(phi);
        double vx = dr * c - rdphi * s;
        double vy = dr * s + rdphi * c;
        return Math.atan2(vy, vx);
    }

    private void deriv(double r, double pr, double[] out) {
        double f = m.f(r);
        double fp = m.rs() / (r * r);
        out[0] = f * pr;
        out[1] = angularMomentum / (r * r);
        out[2] = -0.5 * fp * (energy * energy / (f * f) + pr * pr)
                + angularMomentum * angularMomentum / (r * r * r);
        out[3] = energy / f;
    }

    private double stepSize() {
        // shrink the step near the horizon, where p_r grows like 1/f
        double scale = Math.min(r, 3 * (r - m.rs()));
        return Math.min(1.0, Math.max(1e-5, 0.03 * scale));
    }

    /** One RK4 step of size h in the affine parameter. */
    public void step(double h) {
        if (status != Status.MOVING) return;
        deriv(r, pr, k1);
        deriv(r + 0.5 * h * k1[0], pr + 0.5 * h * k1[2], k2);
        deriv(r + 0.5 * h * k2[0], pr + 0.5 * h * k2[2], k3);
        deriv(r + h * k3[0], pr + h * k3[2], k4);
        r += h / 6 * (k1[0] + 2 * k2[0] + 2 * k3[0] + k4[0]);
        phi += h / 6 * (k1[1] + 2 * k2[1] + 2 * k3[1] + k4[1]);
        pr += h / 6 * (k1[2] + 2 * k2[2] + 2 * k3[2] + k4[2]);
        t += h / 6 * (k1[3] + 2 * k2[3] + 2 * k3[3] + k4[3]);
    }

    private void checkBounds(double rBefore, double rEscape) {
        boolean wild = !Double.isFinite(r) || !Double.isFinite(pr) || !Double.isFinite(phi)
                || Math.abs(r - rBefore) > 0.5 * rBefore;
        if (wild || r <= m.surface() * (1 + 1e-3)) {
            status = m.isBlackHole() ? Status.CAPTURED : Status.HIT_SURFACE;
            r = wild ? m.surface() : Math.max(r, m.surface());
        } else if (r > rEscape) {
            status = Status.ESCAPED;
        }
    }

    /** Advance the particle with its natural step size until it stops or leaves rEscape. */
    public int run(double rEscape, int maxSteps) {
        int n = 0;
        while (status == Status.MOVING && n < maxSteps) {
            double rBefore = r;
            step(stepSize());
            checkBounds(rBefore, rEscape);
            n++;
        }
        return n;
    }

    /**
     * Advance by dt of coordinate time (the time of a far-away observer).
     * Near the horizon dt/dlambda diverges, so the particle appears to freeze,
     * exactly as a distant observer would see it.
     */
    public void advance(double dt, double rEscape) {
        double target = t + dt;
        int guard = 0;
        while (status == Status.MOVING && t < target && guard++ < 5000) {
            double h = stepSize();
            double dtdl = energy / m.f(r);
            double needed = (target - t) / dtdl;
            if (needed < h) h = needed;
            double rBefore = r;
            step(h);
            checkBounds(rBefore, rEscape);
        }
    }
}
