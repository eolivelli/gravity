package gravity.engine;

/** Small 3D value-noise generator (x, y, time) used for the fog texture. */
public final class Noise {

    private Noise() {}

    private static double hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    /** Value noise in [0, 1]. */
    public static double value(double x, double y, double z) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double fx = smooth(x - xi), fy = smooth(y - yi), fz = smooth(z - zi);
        double c000 = hash(xi, yi, zi), c100 = hash(xi + 1, yi, zi);
        double c010 = hash(xi, yi + 1, zi), c110 = hash(xi + 1, yi + 1, zi);
        double c001 = hash(xi, yi, zi + 1), c101 = hash(xi + 1, yi, zi + 1);
        double c011 = hash(xi, yi + 1, zi + 1), c111 = hash(xi + 1, yi + 1, zi + 1);
        double x00 = c000 + (c100 - c000) * fx, x10 = c010 + (c110 - c010) * fx;
        double x01 = c001 + (c101 - c001) * fx, x11 = c011 + (c111 - c011) * fx;
        double y0 = x00 + (x10 - x00) * fy, y1 = x01 + (x11 - x01) * fy;
        return y0 + (y1 - y0) * fz;
    }

    /** Fractal sum of three octaves, roughly in [0, 1]. */
    public static double fbm(double x, double y, double z) {
        double v = value(x, y, z) * 0.55
                + value(x * 2.1 + 17, y * 2.1 + 31, z * 1.7) * 0.28
                + value(x * 4.3 + 5, y * 4.3 + 7, z * 2.9) * 0.17;
        return v;
    }
}
