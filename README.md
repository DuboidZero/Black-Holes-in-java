# Schwarzschild Black Hole Renderer

An interactive black hole visualisation built with **Java 17**, **LWJGL 3**, **OpenGL**, and **GLSL**. It traces light rays through a Schwarzschild-inspired gravitational field to render a dark black hole shadow, a glowing accretion disk, gravitational lensing, and a procedural starfield.

The project is a real-time graphics demonstration. Java manages the application, input, configuration, and GPU resources; GLSL runs the per-pixel ray integration and disk shading on the GPU.

## Screenshots

![Black hole render, view 1](images/Black%20Hole%20-%201.jpeg)

![Black hole render, view 2](images/Black%20Hole%20-%202.jpeg)

![Black hole render, view 3](images/Black%20Hole%20-%203.jpeg)

![Black hole render, view 4](images/Black%20Hole%20-%204.jpeg)

![Black hole render, view 5](images/Black%20Hole%20-%205.jpeg)

## Features

- Schwarzschild-style ray bending with RK4 integration in a GLSL fragment shader.
- Procedural volumetric accretion disk with thermal colours, absorption, and emission.
- Lensed starfield, photon-ring enhancement, and HDR bloom post-processing.
- Orbiting camera, quality presets, colour palettes, diagnostic views, and telemetry HUD.
- Automated benchmark mode that captures scene and diagnostic images.

## Requirements

- Java 17 or newer.
- A desktop environment and graphics driver that supports OpenGL 3.3 Core.
- Gradle wrapper is included. The current Gradle configuration packages Linux LWJGL natives.

## Run

On Linux, from the project root:

```bash
./gradlew run
```

Build the application or run its unit tests:

```bash
./gradlew build
./gradlew test
```

Generate the automated benchmark and visual captures:

```bash
./gradlew run --args="--capture-benchmark"
```

The benchmark outputs are written under `build/benchmark/`. The application can save a screenshot with `F12` to `build/captures/`.

## Controls

| Input | Action |
| --- | --- |
| Left mouse drag | Orbit the camera |
| Mouse wheel | Zoom |
| `A` / `D` | Orbit horizontally |
| Arrow keys | Change camera elevation |
| `Q` / `E` | Roll the camera |
| `W` / `S` | Zoom in / out |
| `1` / `2` / `3` | Select low / medium / high quality |
| `Tab` | Cycle diagnostic render modes |
| `B` | Toggle bloom |
| `C` | Open the colour palette picker |
| `[` / `]` | Decrease / increase disk density |
| `-` / `+` | Decrease / increase exposure |
| Grave accent (`) | Toggle telemetry HUD |
| `R` | Reset camera and scene settings |
| `F12` | Save a screenshot |
| `Esc` | Exit |

## How it works

The Java application creates the GLFW window and OpenGL context, loads configuration and shader resources, handles input, and passes camera and scene parameters to the renderer. The main fragment shader launches one backward ray per screen pixel, integrates its path with RK4-style steps, and accumulates light and absorption along its path through the disk. Rays captured by the event horizon produce the shadow; escaping rays sample the bent procedural starfield.

After the main scene pass, the renderer extracts bright pixels, blurs them, then composites the result with exposure, tone mapping, and gamma correction. Java draws the optional HUD and palette picker over the final image.

The live image is calculated in GLSL. Java classes under `cosmic.physics` provide CPU-side geodesic and Schwarzschild calculations used by the domain code and tests; they are not the per-pixel renderer in the frame loop.

## Project layout

```text
src/main/java/cosmic/
  app/       Application lifecycle, window, and input
  graphics/  Camera, OpenGL resources, renderer, palettes, and HUD
  math/      Vector and numeric helpers
  model/     Black hole and accretion disk state
  physics/   Schwarzschild and geodesic calculations
  util/      Configuration, resource loading, and shared helpers
src/main/resources/
  shaders/   GLSL render and post-processing shaders
  blackhole.properties
src/test/java/  JUnit tests
images/         Project screenshots
```

## Configuration

Edit [`src/main/resources/blackhole.properties`](src/main/resources/blackhole.properties) to change startup parameters such as black hole mass, camera distance, disk dimensions, quality preset, palette, exposure, and window size. The available quality presets are `LOW`, `MEDIUM`, and `HIGH`.

## Technical notes

- [Rendering pipeline notes](ignore/RENDERING_PIPELINE.md)
- [Implementation and optimization plan](ignore/implementation_plan.md)
- [Project report](ignore/PROJECT_REPORT.md)

The simulation uses a simplified Schwarzschild-inspired spatial ray equation and a procedural disk model. It does not simulate fluid dynamics or a rotating Kerr black hole, and some Java-side model helpers are not wired into the live shader path.
