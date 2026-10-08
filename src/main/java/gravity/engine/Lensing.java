package gravity.engine;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/**
 * Ray-traces what a camera sees when it looks at the body.
 *
 * Because the spacetime is spherically symmetric, every ray that leaves the
 * camera at angle theta from the line of sight bends in its own plane by an
 * amount that depends on theta only. We integrate one geodesic per theta into
 * a lookup table, then for each pixel rotate the result around the line of
 * sight. Rays that escape land on a "sky sphere" painted with a checkerboard;
 * rays that hit the body show its surface; rays that fall into a black hole
 * are black.
 */
public final class Lensing {

    public enum Hit { SKY, SURFACE, HORIZON }

    private static final double TWO_PI = 2 * Math.PI;

    private final Schwarzschild m;
    private final double cameraRadius;
    private final double thetaMax;
    private final int n;
    private final Hit[] hit;
    private final double[] skyAngle;     // direction (in the ray's plane) of an escaping ray
    private final double[] surfaceAngle; // position (in the ray's plane) where the ray hits the star

    public Lensing(Schwarzschild m, double cameraRadius, double thetaMax, int n, AtomicBoolean cancel) {
        if (n < 2) throw new IllegalArgumentException("table needs at least two entries");
        this.m = m;
        this.cameraRadius = cameraRadius;
        this.thetaMax = thetaMax;
        this.n = n;
        this.hit = new Hit[n];
        this.skyAngle = new double[n];
        this.surfaceAngle = new double[n];
        double rFar = Math.max(4000 * m.mass(), 40 * cameraRadius);
        IntStream.range(0, n).parallel().forEach(i -> {
            if (cancel != null && cancel.get()) return;
            double theta = thetaMax * i / (n - 1);
            Geodesic g = Geodesic.launch(m, cameraRadius, 0, -Math.cos(theta), Math.sin(theta), 1);
            g.run(rFar, 200_000);
            switch (g.status()) {
                case ESCAPED -> { hit[i] = Hit.SKY; skyAngle[i] = g.velocityAngle(); }
                case HIT_SURFACE -> { hit[i] = Hit.SURFACE; surfaceAngle[i] = g.phi(); }
                default -> hit[i] = Hit.HORIZON;
            }
        });
        // keep escaping directions continuous between neighbours (atan2 wraps at +-pi)
        for (int i = 1; i < n; i++) {
            if (hit[i] == Hit.SKY && hit[i - 1] == Hit.SKY) {
                double d = skyAngle[i] - skyAngle[i - 1];
                if (d > Math.PI) skyAngle[i] -= TWO_PI * Math.round(d / TWO_PI);
                else if (d < -Math.PI) skyAngle[i] += TWO_PI * Math.round(-d / TWO_PI);
            }
        }
    }

    public double cameraRadius() { return cameraRadius; }

    /**
     * Angular radius of the black hole shadow as seen from the camera, in
     * radians: 0 if there is none, NaN if the shadow extends beyond the table
     * (i.e. fills the whole field of view).
     */
    public double shadowAngle() {
        if (hit[n - 1] == Hit.HORIZON) return Double.NaN;
        for (int i = n - 1; i >= 0; i--) if (hit[i] == Hit.HORIZON) return thetaMax * i / (n - 1);
        return 0;
    }

    /**
     * Renders the camera image into an ARGB buffer. The camera sits at
     * (cameraRadius, 0, 0) and looks along -x. Each pixel's ray is traced in
     * its own plane through the x axis; the in-plane +y direction of that
     * plane is mapped to the pixel's direction from the image centre.
     * Rendering stops early if cancel is set.
     */
    public void render(int[] argb, int w, int h, double fov) { render(argb, w, h, fov, null); }

    /** Size of one sky checker cell for a given field of view: a round number of degrees. */
    public static double cellDegrees(double fovDeg) {
        double raw = fovDeg / 12;
        double[] nice = {0.5, 1, 2, 2.5, 5, 10, 15};
        double best = nice[0];
        for (double n : nice) if (Math.abs(Math.log(n / raw)) < Math.abs(Math.log(best / raw))) best = n;
        return best;
    }

    /** See {@link #render(int[], int, int, double)}; cancel may be null. */
    public void render(int[] argb, int w, int h, double fov, AtomicBoolean cancel) {
        double tanH = Math.tan(fov / 2);
        double fovDeg = Math.toDegrees(fov);
        double cell = Math.toRadians(cellDegrees(fovDeg));
        double s1 = fov * 0.28, s2 = fov * 0.16;
        double[] spotDir = {
                1, 0, 0,                                  // right behind the body: becomes an Einstein ring
                Math.cos(s1), Math.sin(s1), 0,
                Math.cos(s2), -Math.sin(s2) * 0.6, Math.sin(s2) * 0.8,
        };
        double[] spotRad = { cell * 0.35, cell * 0.45, cell * 0.3 };
        int[] spotColor = { 0xFFFFF4C0, 0xFFFFB070, 0xFFB0E0FF };

        IntStream.range(0, h).parallel().forEach(py -> {
            if (cancel != null && cancel.get()) return;
            double v = (h / 2.0 - (py + 0.5)) / (w / 2.0) * tanH;
            for (int px = 0; px < w; px++) {
                double u = ((px + 0.5) - w / 2.0) / (w / 2.0) * tanH;
                double rho = Math.hypot(u, v);
                double theta = Math.atan(rho);
                double psi = Math.atan2(v, u);
                double pos = Math.min(n - 1, theta / thetaMax * (n - 1));
                int i0 = (int) pos;
                int i1 = Math.min(n - 1, i0 + 1);
                double frac = pos - i0;
                Hit hh = frac < 0.5 ? hit[i0] : hit[i1];
                int color;
                if (hh == Hit.HORIZON) {
                    color = 0xFF000000;
                } else if (hh == Hit.SKY) {
                    double ang = (hit[i0] == Hit.SKY && hit[i1] == Hit.SKY)
                            ? skyAngle[i0] + frac * (skyAngle[i1] - skyAngle[i0])
                            : (frac < 0.5 ? skyAngle[i0] : skyAngle[i1]);
                    // direction in 3D: cos(ang) x + sin(ang) n_psi, with n_psi = cos(psi) right + sin(psi) up
                    double cs = Math.cos(ang), sn = Math.sin(ang);
                    double dx = cs;                        // along +x (away from the body, toward the camera side)
                    double dRight = sn * Math.cos(psi);
                    double dUp = sn * Math.sin(psi);
                    color = skyColor(dx, dRight, dUp, fov, cell, spotDir, spotRad, spotColor);
                } else {
                    double ang = frac < 0.5 ? surfaceAngle[i0] : surfaceAngle[i1];
                    double cs = Math.cos(ang), sn = Math.sin(ang);
                    color = surfaceColor(cs, sn * Math.cos(psi), sn * Math.sin(psi), cell * 2);
                }
                argb[py * w + px] = color;
            }
        });
    }

    /**
     * Sky painted in camera-aligned longitude/latitude: forward (-x) is the
     * centre, longitude grows to the right, latitude upward. Hue shifts with
     * longitude and saturation with latitude so mirror images are visible.
     */
    private static int skyColor(double dx, double dRight, double dUp, double fov, double cell,
                                double[] spotDir, double[] spotRad, int[] spotColor) {
        for (int s = 0; s < spotRad.length; s++) {
            double sx = spotDir[3 * s], sy = spotDir[3 * s + 1], sz = spotDir[3 * s + 2];
            // spot directions are given in (x, right, up) with x pointing away from camera: flip to "behind"
            double dot = -dx * sx + dRight * sy + dUp * sz;
            double edge = Math.cos(spotRad[s]);
            if (dot > edge) {
                double soft = Math.min(1, (dot - edge) / (1 - Math.cos(spotRad[s] * 0.25)));
                return blend(spotColor[s], 0xFF101018, 0.35 + 0.65 * soft);
            }
        }
        double lon = Math.atan2(dRight, -dx);
        double lat = Math.asin(Math.max(-1, Math.min(1, dUp)));
        double lu = lon / cell, lv = lat / cell;
        double fu = lu - Math.floor(lu), fv = lv - Math.floor(lv);
        if (fu < 0.05 || fv < 0.05) return 0xFFE8ECF4;
        boolean check = (((long) Math.floor(lu) + (long) Math.floor(lv)) & 1) == 0;
        // colour drifts across the field of view so mirrored images are recognisable
        double span = Math.max(fov, Math.toRadians(20));
        float hue = (float) (210 + 110 * Math.max(-1, Math.min(1, lon / span)));
        float sat = (float) (0.35 + 0.5 * (Math.max(-1, Math.min(1, lat / span)) + 1) / 2);
        float bri = check ? 0.78f : 0.38f;
        // java.awt.Color is used only as a pure HSB-to-RGB function; it needs no display
        // and keeps the engine free of JavaFX, which is the point of the "pure engine" rule.
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue / 360f, sat, bri) & 0xFFFFFF);
    }

    /** Surface of the star: an orange/yellow checker in its own latitude/longitude. */
    private static int surfaceColor(double px, double pRight, double pUp, double cell) {
        double lon = Math.atan2(pRight, px);
        double lat = Math.asin(Math.max(-1, Math.min(1, pUp)));
        cell = Math.max(cell, Math.toRadians(5));
        double lu = lon / cell, lv = lat / cell;
        double fu = lu - Math.floor(lu), fv = lv - Math.floor(lv);
        if (fu < 0.06 || fv < 0.06) return 0xFF7A3A10;
        boolean check = (((long) Math.floor(lu) + (long) Math.floor(lv)) & 1) == 0;
        return check ? 0xFFFFC040 : 0xFFF07A20;
    }

    private static int blend(int a, int b, double t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int r = (int) (ar * t + br * (1 - t)), g = (int) (ag * t + bg * (1 - t)), bl = (int) (ab * t + bb * (1 - t));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
