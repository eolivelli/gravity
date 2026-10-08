package gravity.ui;

import gravity.engine.Schwarzschild;

import java.util.ArrayList;
import java.util.List;

/** Shared, mutable state: the body, the map zoom and the animation speed. */
public final class Model {

    /** Schwarzschild radius of one solar mass, in km. */
    public static final double RS_SUN_KM = 2.953;

    private Schwarzschild metric = new Schwarzschild(1.0, 0.0);
    private double massSolar = 10;
    private double radiusInRs = 0;
    private double viewRadius = 30;   // half-width of the map, in units of M
    private double timeScale = 12;    // simulated time (units of M) per real second
    private final List<Runnable> metricListeners = new ArrayList<>();

    public Schwarzschild metric() { return metric; }
    public double massSolar() { return massSolar; }
    public double radiusInRs() { return radiusInRs; }
    public double viewRadius() { return viewRadius; }
    public double timeScale() { return timeScale; }

    /** Kilometres per unit of M for the current mass. */
    public double kmPerM() { return massSolar * RS_SUN_KM / 2; }

    public void setMassSolar(double v) { massSolar = v; }
    public void setViewRadius(double v) { viewRadius = Math.max(4, Math.min(400, v)); }
    public void setTimeScale(double v) { timeScale = v; }

    public void setRadiusInRs(double v) {
        radiusInRs = Math.max(0, v);
        metric = new Schwarzschild(1.0, radiusInRs * 2.0);
        for (Runnable r : metricListeners) r.run();
    }

    public void onMetricChanged(Runnable r) { metricListeners.add(r); }
}
