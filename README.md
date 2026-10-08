# gravity

A JavaFX application that shows the curvature of spacetime around a single
spherical mass (the Schwarzschild solution of Einstein's field equations) in
ways that make sense without a physics degree.

## Running

JavaFX 21 needs JDK 17 or newer. If your default JDK is older, use the helper
script, which picks the newest JDK 17+ installed by sdkman:

```bash
./run.sh
```

or, by hand:

```bash
JAVA_HOME=~/.sdkman/candidates/java/21.0.11-tem mvn javafx:run
```

Keys: `1`..`5` switch views, `S` saves a PNG of the canvas into `./snapshots`,
mouse wheel zooms the map. Set `GRAVITY_SNAPSHOT_DIR=/some/dir` to render every
view once to PNG files and exit (`GRAVITY_RADIUS_RS=2.4` selects a star of that
radius for the snapshot run).

## The body

Two parameters on the top bar:

- **Mass**, in solar masses. All the pictures scale with the mass, so it only
  changes the kilometre labels on the scale bar.
- **Body radius**, in units of the Schwarzschild radius rs = 2GM/c². Below
  9/8 rs no static star can exist (Buchdahl's bound) and the body is treated
  as a black hole. Presets: black hole, neutron star (2.4 rs), dense star (5 rs).

Everything is computed in geometric units (G = c = 1) with M = 1, so a length
of "10 M" is ten gravitational radii.

## The views

1. **Light & orbits.** Light rays and free-falling particles integrated as
   geodesics of the Schwarzschild metric. Nothing pulls on them; they follow
   the straightest path, which bends. Drag on the map to shoot your own at the
   chosen speed. Presets show a parallel beam, a flashlight, a precessing orbit
   and a ring of dust that gets stretched while falling.
2. **Through a camera.** A ray-traced image of a checkerboard sky seen from a
   camera near the body: the Einstein ring, arcs, mirrored secondary images and
   the black-hole shadow. Move the camera closer to see the whole sky wrapped
   around the photon sphere.
3. **Fog of time.** Gravitational time dilation drawn as fog: the thicker the
   fog, the slower a clock there ticks compared with one far away. Clocks at
   several radii (and inside a star) show the actual rates. At the horizon the
   fog is opaque and the clock stops.
4. **River of space.** The Gullstrand–Painlevé picture: space flows into the
   mass at the escape velocity. Dust is carried by the flow; flashes of light
   swim at c relative to the flow. At the horizon the river reaches c, so even
   outgoing light is carried in.
5. **Stretch & squeeze.** Tidal deformation of small balls of free-falling dust
   (the geodesic deviation, i.e. what the Riemann tensor measures): stretched
   radially and squeezed sideways outside, squeezed in all directions inside a
   star.

## Physics in the engine (`gravity.engine`)

- `Schwarzschild`: metric function f(r) = 1 − rs/r, static clock rate √f
  (interior Schwarzschild solution for a constant-density star inside the body),
  river speed √(rs/r), tidal tensor (+2M/r³ radial, −M/r³ tangential).
- `Geodesic`: equatorial geodesics in Hamiltonian form (r, φ, p_r, t) with
  conserved E and L, integrated with RK4. The step is a few percent of the
  distance to the centre or to the horizon, divided by the coordinate speed, so
  fast particles stay accurate too. Initial conditions come from a position, a
  direction and a speed measured by a static observer, so "shoot at 30% of c"
  means what it says. Animation advances in coordinate time, so an infalling
  particle slows down and creeps toward the horizon as a distant observer would
  see it; it is declared captured once it is within one part in a million of rs.
- Inside a star the clock rate is the exact interior Schwarzschild solution for
  constant density; the tidal tensor there is the Newtonian one (it ignores the
  pressure term), and the river picture is illustrative only.
- `Lensing`: because of spherical symmetry only one geodesic per viewing angle
  is needed; a table of a few thousand rays is rotated around the line of sight
  to produce the full image.

The tests in `src/test/java` check, among other things, that the deflection
tends to 4M/b for large impact parameter and matches the exact elliptic
integral in the strong field, that the capture threshold is 3√3 M, that
circular orbits stay circular and eccentric ones precess by about
6πM/(a(1−e²)), that the Hamiltonian is conserved, and that the shadow angle
matches asin(3√3 M √f / r) to about three digits (the table resolution).
