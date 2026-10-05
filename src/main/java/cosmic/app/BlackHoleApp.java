package cosmic.app;

import cosmic.graphics.Camera;
import cosmic.graphics.PalettePreset;
import cosmic.graphics.QualityPreset;
import cosmic.graphics.Renderer;
import cosmic.math.MathUtil;
import cosmic.model.AccretionDisk;
import cosmic.model.BlackHole;
import cosmic.physics.RayIntegrator;
import cosmic.util.Config;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL15.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL15.glBeginQuery;
import static org.lwjgl.opengl.GL15.glDeleteQueries;
import static org.lwjgl.opengl.GL15.glEndQuery;
import static org.lwjgl.opengl.GL15.glGenQueries;
import static org.lwjgl.opengl.GL33.GL_TIME_ELAPSED;
import static org.lwjgl.opengl.GL33.glGetQueryObjectui64;

/**
 * Main application class for the Relativistic Schwarzschild Black Hole Renderer.
 *
 * <p>Initializes the configuration, GLFW window, OpenGL context, domain models,
 * input handling, and runs the interactive real-time simulation loop.</p>
 */
public class BlackHoleApp {

    private final Config config;
    private Window window;
    private Renderer renderer;
    private Camera camera;
    private BlackHole blackHole;
    private AccretionDisk accretionDisk;
    private RayIntegrator rayIntegrator;
    private boolean benchmarkMode;
    private boolean palettePickerOpen;

    // Mouse state
    private boolean mouseDragging = false;
    private double lastMouseX = 0.0;
    private double lastMouseY = 0.0;

    // Input sensitivity
    private static final double MOUSE_SENSITIVITY = 0.005;
    private static final double KEY_ORBIT_SPEED = 1.2; // radians/sec
    private static final double KEY_ROLL_SPEED  = 1.2; // radians/sec
    private static final double KEY_ZOOM_SPEED  = 12.0; // units/sec

    public BlackHoleApp() {
        this.config = new Config();
    }

    public void run() {
        System.out.println("===============================================================");
        System.out.println("  RELATIVISTIC SCHWARZSCHILD BLACK HOLE RENDERER (PASS 2)");
        System.out.println("  Coupled GR Null Geodesics & 3D Volumetric Participating Medium");
        System.out.println("===============================================================");
        System.out.printf("Mass: %.1f M☉ | Distance: %.1f Rs | Preset: %s%n",
            config.getMassSolarMasses(), config.getCameraDistance(), config.getQualityPreset().getDisplayName());

        try {
            init();
            loop();
        } catch (Exception e) {
            System.err.println("Fatal application error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void init() {
        // Create window and OpenGL context
        String title = "Relativistic Schwarzschild Black Hole — 3D Volumetric Raymarching (LWJGL 3 + OpenGL 4.6)";
        window = new Window(config.getWindowWidth(), config.getWindowHeight(), title);

        // Initialize domain models
        blackHole = new BlackHole(config.getMassSolarMasses());
        accretionDisk = new AccretionDisk(
            config.getDiskInnerRadius(),
            config.getDiskOuterRadius(),
            config.getDiskTemperature(),
            config.getDiskThickness(),
            config.getDensityScale(),
            config.getAbsorption()
        );
        accretionDisk.setTurbulenceScale(config.getTurbulenceScale());
        accretionDisk.setTurbulenceStrength(config.getTurbulenceStrength());
        accretionDisk.setRotationSpeed(config.getRotationSpeed());
        accretionDisk.setSpiralStrength(config.getSpiralStrength());
        accretionDisk.setEmissionStrength(config.getEmissionStrength());
        accretionDisk.setInnerIllumination(config.getInnerIllumination());

        // Camera: cinematic slightly inclined perspective with configurable roll
        camera = new Camera(config.getCameraDistance(), config.getCameraRoll());

        // Ray Integrator
        rayIntegrator = new RayIntegrator(
            RayIntegrator.IntegratorType.RK4,
            config.getQualityPreset().getMaxGeodesicSteps(),
            config.getQualityPreset().getBaseStepSize(),
            blackHole.getNormalizedRs()
        );

        // Renderer
        renderer = new Renderer(window.getWidth(), window.getHeight());

        // Attach window resize listener
        window.setResizeListener((w, h) -> {
            if (w > 0 && h > 0) {
                renderer.resize(w, h);
            }
        });

        // Setup input callbacks
        setupInput();
    }

    private void setupInput() {
        long handle = window.getHandle();

        // Mouse button callback
        glfwSetMouseButtonCallback(handle, (win, button, action, mods) -> {
            if (benchmarkMode || palettePickerOpen) return;
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    mouseDragging = true;
                    double[] mx = new double[1];
                    double[] my = new double[1];
                    glfwGetCursorPos(win, mx, my);
                    lastMouseX = mx[0];
                    lastMouseY = my[0];
                } else if (action == GLFW_RELEASE) {
                    mouseDragging = false;
                }
            }
        });

        // Cursor movement callback
        glfwSetCursorPosCallback(handle, (win, xpos, ypos) -> {
            if (benchmarkMode || palettePickerOpen) return;
            if (mouseDragging) {
                double dx = xpos - lastMouseX;
                double dy = ypos - lastMouseY;
                lastMouseX = xpos;
                lastMouseY = ypos;

                // Mouse drag: azimuth and elevation orbit
                camera.orbit(-dx * MOUSE_SENSITIVITY, -dy * MOUSE_SENSITIVITY);
            }
        });

        // Scroll callback (zoom)
        glfwSetScrollCallback(handle, (win, xoffset, yoffset) -> {
            if (benchmarkMode || palettePickerOpen) return;
            camera.zoom(-yoffset * 1.5);
        });

        // Key callback (single-press triggers)
        glfwSetKeyCallback(handle, (win, key, scancode, action, mods) -> {
            if (benchmarkMode) return;
            if (action != GLFW_PRESS) return;

            if (palettePickerOpen) {
                if (key == GLFW_KEY_ESCAPE || key == GLFW_KEY_C
                        || key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) {
                    palettePickerOpen = false;
                    renderer.setPalettePickerOpen(false);
                } else if (key == GLFW_KEY_LEFT || key == GLFW_KEY_DOWN) {
                    config.setColorPalette(config.getColorPalette().cycle(-1));
                } else if (key == GLFW_KEY_RIGHT || key == GLFW_KEY_UP) {
                    config.setColorPalette(config.getColorPalette().cycle(1));
                }
                return;
            }

            if (key == GLFW_KEY_C) {
                palettePickerOpen = true;
                mouseDragging = false;
                renderer.setPalettePickerOpen(true);
            } else if (key == GLFW_KEY_ESCAPE) {
                window.setShouldClose(true);
            } else if (key == GLFW_KEY_1) {
                config.setQualityPreset(QualityPreset.LOW);
                System.out.println("[App] Quality Preset set to LOW (Preview): 96 steps, 2 light steps");
            } else if (key == GLFW_KEY_2) {
                config.setQualityPreset(QualityPreset.MEDIUM);
                System.out.println("[App] Quality Preset set to MEDIUM (Balanced): 160 steps, 3 light steps");
            } else if (key == GLFW_KEY_3) {
                config.setQualityPreset(QualityPreset.HIGH);
                System.out.println("[App] Quality Preset set to HIGH (Cinematic): 240 steps, 4 light steps");
            } else if (key == GLFW_KEY_TAB) {
                config.setDebugMode(config.getDebugMode() + 1);
                System.out.println("[App] Debug mode: " + config.getDebugMode());
            } else if (key == GLFW_KEY_LEFT_BRACKET) {
                double newDens = Math.max(0.05, accretionDisk.getDensityScale() - 0.15);
                accretionDisk.setDensityScale(newDens);
                System.out.printf("[App] Plasma Density scale reduced: %.2f%n", newDens);
            } else if (key == GLFW_KEY_RIGHT_BRACKET) {
                double newDens = accretionDisk.getDensityScale() + 0.15;
                accretionDisk.setDensityScale(newDens);
                System.out.printf("[App] Plasma Density scale increased: %.2f%n", newDens);
            } else if (key == GLFW_KEY_MINUS || key == GLFW_KEY_KP_SUBTRACT) {
                double newExp = Math.max(0.1, config.getExposure() - 0.15);
                config.setExposure(newExp);
                System.out.printf("[App] Exposure reduced: %.2f%n", newExp);
            } else if (key == GLFW_KEY_EQUAL || key == GLFW_KEY_KP_ADD) {
                double newExp = config.getExposure() + 0.15;
                config.setExposure(newExp);
                System.out.printf("[App] Exposure increased: %.2f%n", newExp);
            } else if (key == GLFW_KEY_B) {
                config.setBloom(!config.isBloom());
                System.out.println("[App] Bloom toggled: " + (config.isBloom() ? "ON" : "OFF"));
            } else if (key == GLFW_KEY_GRAVE_ACCENT) {
                config.setShowDebug(!config.isShowDebug());
                System.out.println("[App] HUD " + (config.isShowDebug() ? "visible" : "hidden"));
            } else if (key == GLFW_KEY_R) {
                camera.reset();
                blackHole.reset();
                accretionDisk.reset();
                config.setColorPalette(PalettePreset.GARGANTUA_AMBER);
                config.setExposure(1.2);
                config.setDebugMode(0);
                System.out.println("[App] Camera and parameters reset to default.");
            } else if (key == GLFW_KEY_F12) {
                takeScreenshot();
            }
        });
    }

    private void handleContinuousInput(float dt) {
        if (palettePickerOpen) return;
        long handle = window.getHandle();

        // Keyboard zoom
        if (glfwGetKey(handle, GLFW_KEY_W) == GLFW_PRESS) {
            camera.zoom(-KEY_ZOOM_SPEED * dt);
        }
        if (glfwGetKey(handle, GLFW_KEY_S) == GLFW_PRESS) {
            camera.zoom(KEY_ZOOM_SPEED * dt);
        }

        // Arrow keys + A/D for smooth orbital rotation
        if (glfwGetKey(handle, GLFW_KEY_LEFT) == GLFW_PRESS || glfwGetKey(handle, GLFW_KEY_A) == GLFW_PRESS) {
            camera.orbit(-KEY_ORBIT_SPEED * dt, 0.0);
        }
        if (glfwGetKey(handle, GLFW_KEY_RIGHT) == GLFW_PRESS || glfwGetKey(handle, GLFW_KEY_D) == GLFW_PRESS) {
            camera.orbit(KEY_ORBIT_SPEED * dt, 0.0);
        }
        if (glfwGetKey(handle, GLFW_KEY_UP) == GLFW_PRESS) {
            camera.orbit(0.0, KEY_ORBIT_SPEED * dt);
        }
        if (glfwGetKey(handle, GLFW_KEY_DOWN) == GLFW_PRESS) {
            camera.orbit(0.0, -KEY_ORBIT_SPEED * dt);
        }

        // Q / E for cinematic Dutch roll angle (tilt around view forward axis)
        if (glfwGetKey(handle, GLFW_KEY_Q) == GLFW_PRESS) {
            camera.addRoll(-KEY_ROLL_SPEED * dt);
        }
        if (glfwGetKey(handle, GLFW_KEY_E) == GLFW_PRESS) {
            camera.addRoll(KEY_ROLL_SPEED * dt);
        }
    }

    private void takeScreenshot() {
        File dir = new File("build/captures");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("%s/blackhole_%s_mode%d.png", dir.getAbsolutePath(), timestamp, config.getDebugMode());
        if (renderer.captureScreenshot(filename)) {
            System.out.println("[App] Screenshot captured: " + filename);
        } else {
            System.err.println("[App] Failed to write screenshot to " + filename);
        }
    }

    private void loop() {
        double lastTime = glfwGetTime();
        double fpsTimer = lastTime;
        int frameCount = 0;
        double currentFps = 60.0;

        while (!window.shouldClose()) {
            double currentTime = glfwGetTime();
            float deltaTime = (float) (currentTime - lastTime);
            lastTime = currentTime;

            // Cap large delta times during window drags
            deltaTime = (float) MathUtil.clamp(deltaTime, 0.0001, 0.1);

            // FPS calculation
            frameCount++;
            if (currentTime - fpsTimer >= 0.5) {
                currentFps = frameCount / (currentTime - fpsTimer);
                frameCount = 0;
                fpsTimer = currentTime;
            }

            // Process inputs
            handleContinuousInput(deltaTime);

            // Render scene
            renderer.render(camera, blackHole, accretionDisk, rayIntegrator, config, deltaTime, currentFps);

            // Present to screen & poll events
            window.swapBuffers();
            window.pollEvents();
        }
    }

    /**
     * Executes an automated verification benchmark:
     * warms up the GPU, measures steady-state FPS, captures reference screenshots
     * for all major visual modes (Final HDR, Density, Temperature, Optical Depth, Lensing, Geodesic steps),
     * and saves them to the screenshots directory.
     */
    public void runBenchmarkAndCapture() {
        System.out.println("===============================================================");
        System.out.println("  STARTING AUTOMATED VISUAL VERIFICATION & GPU BENCHMARK");
        System.out.println("===============================================================");
        benchmarkMode = true;
        try {
            init();

            File dir = new File("build/benchmark");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            QualityPreset capturePreset = QualityPreset.HIGH;
            int benchFrames = 90;
            int timerQuery = glGenQueries();
            double avgFps = 60.0;
            for (QualityPreset preset : QualityPreset.values()) {
                config.setQualityPreset(preset);
                for (int i = 0; i < 30; i++) {
                    renderer.render(camera, blackHole, accretionDisk, rayIntegrator, config, 0.016f, 60.0);
                    window.swapBuffers();
                    window.pollEvents();
                }

                glBeginQuery(GL_TIME_ELAPSED, timerQuery);
                for (int i = 0; i < benchFrames; i++) {
                    renderer.render(camera, blackHole, accretionDisk, rayIntegrator, config, 0.016f, 60.0);
                    window.swapBuffers();
                    window.pollEvents();
                }
                glEndQuery(GL_TIME_ELAPSED);
                long elapsedNanos = glGetQueryObjectui64(timerQuery, GL_QUERY_RESULT);
                double avgFrameTimeMs = elapsedNanos / 1_000_000.0 / benchFrames;
                avgFps = 1000.0 / avgFrameTimeMs;
                System.out.printf("[Benchmark] %s: %.1f FPS (%.2f GPU ms/frame) at %dx%d%n",
                    preset.name(), avgFps, avgFrameTimeMs, window.getWidth(), window.getHeight());
            }
            glDeleteQueries(timerQuery);
            config.setQualityPreset(capturePreset);

            // Reference screenshot capture suite
            int[] modesToCapture = { 0, 1, 2, 3, 6, 7 };
            String[] modeNames = {
                "P12_final_composite",
                "P2_plasma_density",
                "P5_temperature_field",
                "P6_optical_depth",
                "P8_gravitational_lensing",
                "P12_geodesic_steps"
            };

            for (int i = 0; i < modesToCapture.length; i++) {
                int mode = modesToCapture[i];
                String name = modeNames[i];
                config.setDebugMode(mode);

                // Render 5 frames in this mode
                for (int f = 0; f < 5; f++) {
                    renderer.render(camera, blackHole, accretionDisk, rayIntegrator, config, 0.016f, avgFps);
                    window.swapBuffers();
                    window.pollEvents();
                }

                String filename = String.format("%s/%s.png", dir.getAbsolutePath(), name);
                if (renderer.captureScreenshot(filename)) {
                    System.out.println("[Benchmark] Successfully saved verification image: " + filename);
                } else {
                    System.err.println("[Benchmark] Failed to capture: " + filename);
                }
            }

            config.setDebugMode(0);
            config.setShowDebug(false);
            camera.reset();
            camera.zoom(35.0);
            for (int frame = 0; frame < 5; frame++) {
                renderer.render(camera, blackHole, accretionDisk, rayIntegrator, config, 0.016f, avgFps);
                window.swapBuffers();
                window.pollEvents();
            }
            renderer.captureScreenshot(String.format("%s/P14_side_view.png", dir.getAbsolutePath()));

            camera.reset();
            camera.zoom(35.0);
            camera.setRoll(0.0);
            camera.orbit(0.0, Math.toRadians(68.0));
            for (int frame = 0; frame < 5; frame++) {
                renderer.render(camera, blackHole, accretionDisk, rayIntegrator, config, 0.016f, avgFps);
                window.swapBuffers();
                window.pollEvents();
            }
            renderer.captureScreenshot(String.format("%s/P15_top_view.png", dir.getAbsolutePath()));
            config.setDebugMode(1);
            for (int frame = 0; frame < 5; frame++) {
                renderer.render(camera, blackHole, accretionDisk, rayIntegrator, config, 0.016f, avgFps);
                window.swapBuffers();
                window.pollEvents();
            }
            renderer.captureScreenshot(String.format("%s/P16_top_density.png", dir.getAbsolutePath()));
            config.setDebugMode(0);
            camera.reset();

            // Capture HUD overlay verification image
            config.setDebugMode(0);
            config.setShowDebug(true);
            for (int f = 0; f < 5; f++) {
                renderer.render(camera, blackHole, accretionDisk, rayIntegrator, config, 0.016f, avgFps);
                window.swapBuffers();
                window.pollEvents();
            }
            String hudFilename = String.format("%s/%s.png", dir.getAbsolutePath(), "P13_hud_telemetry");
            if (renderer.captureScreenshot(hudFilename)) {
                System.out.println("[Benchmark] Successfully saved HUD verification image: " + hudFilename);
            }
            config.setShowDebug(false);

            System.out.println("[Benchmark] Verification screenshots generated successfully!");
        } catch (Exception e) {
            System.err.println("Benchmark error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        if (renderer != null) {
            renderer.destroy();
        }
        if (window != null) {
            window.close();
        }
        System.out.println("[App] Relativistic black hole application closed cleanly.");
    }

    public static void main(String[] args) {
        BlackHoleApp app = new BlackHoleApp();
        if (args.length > 0 && args[0].equals("--capture-benchmark")) {
            app.runBenchmarkAndCapture();
        } else {
            app.run();
        }
    }
}
