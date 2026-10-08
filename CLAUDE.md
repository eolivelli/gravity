# CLAUDE.md

Guidance for Claude Code (and humans) working in this repository.

## What this is

A JavaFX desktop app that visualizes spacetime curvature around a single
spherical mass (Schwarzschild metric) for people who have not studied general
relativity. The goal is intuition, not a research tool: every view must stay
physically honest but be explainable in one or two plain sentences, which are
shown under the canvas (`View.description()`).

## Build and run

- JDK 21 and JavaFX 21. The machine's default JDK may be 11, which fails with a
  cryptic Maven error. Always run through `./run.sh`, or set
  `JAVA_HOME` to a JDK 17+ before `mvn javafx:run`.
- Compile only: `JAVA_HOME=<jdk21> mvn -q compile`.
- Headless visual check: `GRAVITY_SNAPSHOT_DIR=/tmp/shots ./run.sh` renders one
  PNG per view and exits. Add `GRAVITY_RADIUS_RS=2.4` to render the star case.
  Look at the PNGs after any change to rendering code.
- `mvn verify` runs the JUnit 5 engine tests (see "Tests and CI"). Physics
  changes must keep them green and should add a check when a new quantity is
  introduced.

## Layout

- `gravity.engine` is pure physics, no JavaFX imports. Keep it that way so it
  can be unit tested and reused. (`java.awt.Color.HSBtoRGB` is used as a plain
  colour-math function in `Lensing`; that is fine, it needs no display.)
  - `Schwarzschild`: metric quantities (clock rate, river speed, tidal tensor).
  - `Geodesic`: equatorial geodesic integrator (RK4, Hamiltonian form).
  - `Lensing`: ray-traced camera image via a one-dimensional angle table.
  - `Noise`: value noise for the fog texture.
- `gravity.ui` is the JavaFX app. `App` owns the window, the parameter bar and
  the animation loop. Each visualization implements `View` and owns its own
  controls. `Model` is the shared state. `Backdrop` holds drawing helpers.
- Adding a view: implement `View`, add it to the list in `App.start`, give it a
  lay-person `description()`.

## Conventions

- Geometric units, G = c = 1, with the mass M = 1. Lengths are in units of M,
  so rs = 2. The mass slider only changes the kilometre labels.
- World coordinates are centred on the body; `Viewport` maps them to pixels.
  Vector drawing goes through `Viewport`; per-pixel rasterizers (fog) derive
  their own mapping from `Viewport.scale()` for speed.
- Simulated motion (geodesics, river dust and flashes) advances in coordinate
  time, i.e. the far-away observer's time, scaled by the speed slider. Do not
  switch to proper time without a reason: slowing down and freezing at the
  horizon is a feature. Purely decorative animation (fog drift, clock hands,
  the tidal "breathing") runs in real seconds.
- Lensing renders off the FX thread with a generation counter and a cancel
  flag; results are applied only if still the newest. The fog texture is
  computed per frame at half resolution in a parallel stream, with the static
  density cached per zoom level; keep anything heavier than that off `render`.
- Launch directions and speeds are those measured by a static local observer,
  see `Geodesic.launch`.

## Verifying physics

Quick checks that have been used and should still hold after engine changes:

- Light deflection tends to 4M/b for large impact parameter b.
- Light with impact parameter below 3√3 M is captured, above it escapes.
- A particle launched with `circularOrbitSpeed(r)` tangentially stays at r.
- Black-hole shadow angle from radius r is asin(3√3 M √(1 − 2M/r) / r).
- `lapse` is continuous at a star's surface and reaches 0 at the centre when
  the radius is exactly 9/8 rs.

## Style

- Plain Java 21, no Lombok, no frameworks beyond JavaFX.
- Keep user-facing text free of jargon; prefer "far-away clock" to "asymptotic
  observer". Jargon belongs in code comments and the README.

## Tests and CI

- `mvn verify` (JDK 21) runs the JUnit 5 engine tests in `src/test/java`.
  They encode the physics checks listed above plus Hamiltonian conservation,
  strong-field deflection against the exact integral and the precession
  magnitude; extend them when touching the engine.
- GitHub Actions (`.github/workflows/ci.yml`) runs the tests and then renders
  all views headless under Xvfb for both a black hole and a neutron star,
  uploading the PNGs as the `snapshots` artifact. Download them to review
  rendering changes from a pull request.
