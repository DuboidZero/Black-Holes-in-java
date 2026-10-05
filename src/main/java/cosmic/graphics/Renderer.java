package cosmic.graphics;

import cosmic.math.Vec3;
import cosmic.model.AccretionDisk;
import cosmic.model.BlackHole;
import cosmic.physics.RayIntegrator;
import cosmic.util.Config;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBImageWrite.stbi_write_png;

/**
 * Orchestrates multi-pass rendering for the Schwarzschild black hole simulation.
 *
 * <p>Pipeline execution:
 * <ol>
 *   <li><b>Numerical Schwarzschild Geodesics + Volumetric Radiative Transfer:</b>
 *       Generates HDR scene in offscreen floating-point {@link Framebuffer}.</li>
 *   <li><b>Bloom Extraction:</b> Extracts high-luminance pixels via threshold filter.</li>
 *   <li><b>Two-Pass Gaussian Blur:</b> Separable horizontal and vertical blurring.</li>
 *   <li><b>Composite &amp; Tone Mapping:</b> Blends scene + bloom with ACES filmic curve and gamma.</li>
 *   <li><b>Debug Telemetry HUD:</b> Renders interactive telemetry overlay.</li>
 * </ol>
 * </p>
 */
public class Renderer extends GLResource {

    private final FullscreenTriangle quad;
    private final ShaderProgram blackholeShader;
    private final ShaderProgram brightpassShader;
    private final ShaderProgram blurShader;
    private final ShaderProgram compositeShader;
    private final TextOverlay textOverlay;
    private final Texture3D noiseTexture;

    private Framebuffer sceneFbo;
    private Framebuffer brightFbo;
    private Framebuffer blurFbo;

    private int width;
    private int height;
    private float totalTime;
    private boolean palettePickerOpen;

    private static final String[] DEBUG_MODE_NAMES = {
        "D0: Final HDR Composite",
        "D1: Plasma Density Field",
        "D2: Disk Temperature (False Color)",
        "D3: Optical Depth (1 - T)",
        "D4: Radiative Emission Only",
        "D5: Transmittance / Extinction Map",
        "D6: Gravitational Lensing (Naked BH)",
        "D7: Geodesic Step Diagnostics Heatmap"
    };

    public Renderer(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.totalTime = 0.0f;

        this.quad = new FullscreenTriangle();

        // 3D Volumetric Noise Texture (64^3 seamless periodic RGBA8)
        this.noiseTexture = new Texture3D(64);

        // Shaders
        this.blackholeShader = ShaderProgram.fromClasspath("BlackHoleShader", "shaders/blackhole.vert", "shaders/blackhole.frag");
        this.brightpassShader = ShaderProgram.fromClasspath("BrightpassShader", "shaders/blackhole.vert", "shaders/brightpass.frag");
        this.blurShader = ShaderProgram.fromClasspath("BlurShader", "shaders/blackhole.vert", "shaders/blur.frag");
        this.compositeShader = ShaderProgram.fromClasspath("CompositeShader", "shaders/blackhole.vert", "shaders/composite.frag");

        // Text & Telemetry HUD
        this.textOverlay = new TextOverlay();

        // Framebuffers
        int halfW = Math.max(1, width / 2);
        int halfH = Math.max(1, height / 2);
        this.sceneFbo = new Framebuffer(width, height, true);
        this.brightFbo = new Framebuffer(halfW, halfH, true);
        this.blurFbo = new Framebuffer(halfW, halfH, true);
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) return;
        this.width = newWidth;
        this.height = newHeight;

        sceneFbo.resize(width, height);
        brightFbo.resize(Math.max(1, width / 2), Math.max(1, height / 2));
        blurFbo.resize(Math.max(1, width / 2), Math.max(1, height / 2));
    }

    /**
     * Executes the complete multi-pass render cycle.
     */
    public void render(Camera camera, BlackHole blackHole, AccretionDisk disk,
                       RayIntegrator integrator, Config config, float deltaTime, double fps) {
        totalTime += deltaTime;

        // -------------------------------------------------------------
        // Pass 1: Numerical Geodesics + Volumetric Radiative Transfer (HDR FBO)
        // -------------------------------------------------------------
        sceneFbo.bind();
        glDisable(GL_DEPTH_TEST);
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        // Bind 3D noise texture to unit 2
        noiseTexture.bindTexture(2);

        blackholeShader.bind();
        blackholeShader.setUniform3f("u_CameraPos", camera.getPosition());
        blackholeShader.setUniform3f("u_CameraForward", camera.getForward());
        blackholeShader.setUniform3f("u_CameraRight", camera.getRight());
        blackholeShader.setUniform3f("u_CameraUp", camera.getUp());
        blackholeShader.setUniform1f("u_Fov", (float) camera.getFovDegrees());
        blackholeShader.setUniform2f("u_Resolution", (float) width, (float) height);
        setPaletteUniforms(blackholeShader, config.getColorPalette());

        // Black Hole parameters
        blackholeShader.setUniform1f("u_Rs", (float) blackHole.getNormalizedRs());
        blackholeShader.setUniform1f("u_RPhoton", (float) blackHole.getNormalizedRPhoton());

        // Accretion disk volumetric parameters
        blackholeShader.setUniform1f("u_DiskRIn", (float) disk.getInnerRadius());
        blackholeShader.setUniform1f("u_DiskROut", (float) disk.getOuterRadius());
        blackholeShader.setUniform1f("u_DiskTemp", (float) disk.getReferenceTemperature());
        blackholeShader.setUniform1f("u_DiskThickness", (float) disk.getThickness());
        blackholeShader.setUniform1f("u_DensityScale", (float) disk.getDensityScale());
        blackholeShader.setUniform1f("u_Absorption", (float) disk.getAbsorption());
        blackholeShader.setUniform1f("u_TurbulenceScale", (float) disk.getTurbulenceScale());
        blackholeShader.setUniform1f("u_TurbulenceStrength", (float) disk.getTurbulenceStrength());
        blackholeShader.setUniform1f("u_RotationSpeed", (float) disk.getRotationSpeed());
        blackholeShader.setUniform1f("u_SpiralStrength", (float) disk.getSpiralStrength());
        blackholeShader.setUniform1f("u_EmissionStrength", (float) disk.getEmissionStrength());
        blackholeShader.setUniform1f("u_InnerIllumination", (float) disk.getInnerIllumination());

        // Quality preset parameters
        QualityPreset preset = config.getQualityPreset();
        blackholeShader.setUniform1i("u_MaxSteps", preset.getMaxGeodesicSteps());
        blackholeShader.setUniform1f("u_BaseStepSize", preset.getBaseStepSize());
        blackholeShader.setUniform1i("u_LightSteps", preset.getLightSteps());
        blackholeShader.setUniform1i("u_NoiseDetailLevel", preset.getNoiseDetailLevel());
        blackholeShader.setUniform1i("u_DebugMode", config.getDebugMode());

        blackholeShader.setUniform1i("u_NoiseTexture3D", 2);
        blackholeShader.setUniform1f("u_Time", totalTime);

        quad.render();
        blackholeShader.unbind();
        sceneFbo.unbind();

        // -------------------------------------------------------------
        // Pass 2: Bloom Post-Processing (Threshold -> H-Blur -> V-Blur)
        // -------------------------------------------------------------
        boolean applyBloom = config.isBloom() && config.getDebugMode() == 0;
        if (applyBloom) {
            // Bright-pass filter
            brightFbo.bind();
            glClear(GL_COLOR_BUFFER_BIT);
            brightpassShader.bind();
            brightpassShader.setUniform1i("u_SceneTexture", 0);
            brightpassShader.setUniform1f("u_Threshold", 0.85f);
            sceneFbo.bindTexture(0);
            quad.render();
            brightpassShader.unbind();
            brightFbo.unbind();

            // Horizontal Gaussian Blur
            blurFbo.bind();
            glClear(GL_COLOR_BUFFER_BIT);
            blurShader.bind();
            blurShader.setUniform1i("u_Texture", 0);
            blurShader.setUniform2f("u_Direction", 1.0f / (float) brightFbo.getWidth(), 0.0f);
            brightFbo.bindTexture(0);
            quad.render();
            blurShader.unbind();
            blurFbo.unbind();

            // Vertical Gaussian Blur
            brightFbo.bind();
            glClear(GL_COLOR_BUFFER_BIT);
            blurShader.bind();
            blurShader.setUniform1i("u_Texture", 0);
            blurShader.setUniform2f("u_Direction", 0.0f, 1.0f / (float) brightFbo.getHeight());
            blurFbo.bindTexture(0);
            quad.render();
            blurShader.unbind();
            brightFbo.unbind();
        }

        // -------------------------------------------------------------
        // Pass 3: Composite + ACES Filmic Tone Mapping + Gamma Correction
        // -------------------------------------------------------------
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, width, height);
        glClear(GL_COLOR_BUFFER_BIT);

        compositeShader.bind();
        compositeShader.setUniform1i("u_SceneTexture", 0);
        compositeShader.setUniform1i("u_BloomTexture", 1);
        compositeShader.setUniform1f("u_Exposure", (float) config.getExposure());
        compositeShader.setUniform1i("u_BloomEnabled", applyBloom ? 1 : 0);
        compositeShader.setUniform1f("u_BloomIntensity", 0.45f);

        sceneFbo.bindTexture(0);
        if (applyBloom) {
            brightFbo.bindTexture(1);
        }

        quad.render();
        compositeShader.unbind();

        // -------------------------------------------------------------
        // Pass 4: Telemetry HUD Overlay
        // -------------------------------------------------------------
        if (config.isShowDebug()) {
            renderTelemetry(camera, blackHole, disk, integrator, config, fps);
        }
        if (palettePickerOpen) {
            renderPalettePicker(config.getColorPalette());
        }
    }

    public void setPalettePickerOpen(boolean open) {
        palettePickerOpen = open;
    }

    private void setPaletteUniforms(ShaderProgram shader, PalettePreset palette) {
        for (int i = 0; i < 5; i++) {
            PalettePreset.Rgb color = palette.getDiskStop(i);
            shader.setUniform3f("u_DiskColor" + i, color.r(), color.g(), color.b());
        }

        setColorUniform(shader, "u_SkyBaseColor", palette.getSkyBase());
        setColorUniform(shader, "u_SkyDustColor", palette.getSkyDust());
        setColorUniform(shader, "u_StarColorDim", palette.getStarColorDim());
        setColorUniform(shader, "u_StarColorBright", palette.getStarColorBright());
    }

    private void setColorUniform(ShaderProgram shader, String name, PalettePreset.Rgb color) {
        shader.setUniform3f(name, color.r(), color.g(), color.b());
    }

    private void renderPalettePicker(PalettePreset selected) {
        float panelWidth = Math.min(560.0f, Math.max(1.0f, width - 24.0f));
        float panelHeight = Math.min(260.0f, Math.max(1.0f, height - 24.0f));
        float scale = Math.min(panelWidth / 560.0f, panelHeight / 260.0f);
        float x = (width - panelWidth) * 0.5f;
        float y = (height - panelHeight) * 0.5f;

        textOverlay.begin(width, height);
        textOverlay.drawPanel(x, y, panelWidth, panelHeight);
        textOverlay.drawText("COLOR PALETTES", x + 18.0f * scale, y + 14.0f * scale,
            0.40f * scale, 0.82f, 0.93f, 1.0f, 1.0f);
        textOverlay.drawText("LEFT / RIGHT: PREVIEW    ENTER / C / ESC: CLOSE",
            x + 18.0f * scale, y + 40.0f * scale, 0.27f * scale,
            0.68f, 0.78f, 0.86f, 0.95f);

        PalettePreset[] presets = PalettePreset.values();
        int index = selected.ordinal();
        PalettePreset previous = presets[Math.floorMod(index - 1, presets.length)];
        PalettePreset next = presets[(index + 1) % presets.length];

        textOverlay.drawText(String.format("%d / %d   %s", index + 1, presets.length, selected.getDisplayName()),
            x + 18.0f * scale, y + 72.0f * scale, 0.38f * scale,
            1.0f, 0.88f, 0.58f, 1.0f);
        textOverlay.drawText("PREV: " + previous.getDisplayName() + "     NEXT: " + next.getDisplayName(),
            x + 18.0f * scale, y + 96.0f * scale, 0.25f * scale,
            0.55f, 0.68f, 0.78f, 0.90f);

        float labelX = x + 18.0f * scale;
        float stripX = x + 80.0f * scale;
        float stripWidth = Math.max(1.0f, panelWidth - 98.0f * scale);
        textOverlay.drawText("DISK", labelX, y + 124.0f * scale, 0.25f * scale,
            0.86f, 0.88f, 0.92f, 1.0f);
        drawGradientStrip(stripX, y + 119.0f * scale, stripWidth, 14.0f * scale, selected, true);

        textOverlay.drawText("SKY", labelX, y + 157.0f * scale, 0.25f * scale,
            0.86f, 0.88f, 0.92f, 1.0f);
        drawGradientStrip(stripX, y + 152.0f * scale, stripWidth, 14.0f * scale, selected, false);

        textOverlay.drawText("PALETTE PREVIEW APPLIES IMMEDIATELY",
            x + 18.0f * scale, y + 198.0f * scale, 0.25f * scale,
            0.50f, 0.68f, 0.77f, 0.90f);
        textOverlay.end();
    }

    private void drawGradientStrip(float x, float y, float width, float height,
                                   PalettePreset palette, boolean disk) {
        int segments = 20;
        float segmentWidth = width / segments;
        for (int i = 0; i < segments; i++) {
            float t = (i + 0.5f) / segments;
            PalettePreset.Rgb color = disk ? palette.diskPreview(t) : palette.skyPreview(t);
            textOverlay.drawBox(x + i * segmentWidth, y, segmentWidth + 0.5f, height,
                color.r(), color.g(), color.b(), 1.0f);
        }
    }

    private void renderTelemetry(Camera camera, BlackHole bh, AccretionDisk disk,
                                 RayIntegrator integrator, Config config, double fps) {
        textOverlay.begin(width, height);

        final float PAD   = 16.0f;
        final float INNER = PAD + 14.0f;
        final float ROW   = 18.0f;

        // ── MAIN TELEMETRY PANEL ────────────────────────────────────────────────
        float pw = 440.0f;
        float ph = 186.0f;
        textOverlay.drawPanel(PAD, PAD, pw, ph);

        float curY = PAD + 16.0f;

        // Title and live FPS badge on same row
        textOverlay.drawText("SCHWARZSCHILD BLACK HOLE", INNER, curY, 0.44f,
            0.30f, 0.82f, 1.0f, 1.0f);

        float fpsColor = (float) Math.min(1.0, fps / 60.0);
        textOverlay.drawText(String.format("FPS: %4.1f", fps), INNER + 295.0f, curY, 0.44f,
            1.0f - fpsColor * 0.5f, fpsColor, fpsColor * 0.35f, 1.0f);

        curY += ROW + 2.0f;
        textOverlay.drawText("GR GEODESICS + 3D VOLUMETRIC SIMULATION", INNER, curY, 0.33f,
            0.45f, 0.60f, 0.75f, 0.85f);

        // Subtle divider
        curY += 14.0f;
        textOverlay.drawBox(INNER, curY, pw - 28.0f, 1.0f, 0.20f, 0.35f, 0.50f, 0.40f);
        curY += 7.0f;

        // ── PHYSICS
        textOverlay.drawText(
            String.format("Mass: %4.1f M\u2609           Rs (2GM/c\u00b2): %5.2f km",
                bh.getMassSolarMasses(), bh.getPhysicalRsKm()),
            INNER, curY, 0.37f, 0.88f, 0.92f, 0.96f, 1.0f);
        curY += ROW;

        textOverlay.drawText(
            String.format("Disk: %4.1f - %4.1f Rs   Photon Sphere: %5.2f km",
                disk.getInnerRadius(), disk.getOuterRadius(), bh.getPhysicalPhotonSphereKm()),
            INNER, curY, 0.37f, 0.88f, 0.92f, 0.96f, 1.0f);
        curY += ROW + 2.0f;

        // Subtle divider
        textOverlay.drawBox(INNER, curY, pw - 28.0f, 1.0f, 0.20f, 0.35f, 0.50f, 0.40f);
        curY += 7.0f;

        // ── RENDERER & CAMERA
        QualityPreset preset = config.getQualityPreset();
        float qr = 0.3f, qg = 1.0f, qb = 0.5f;
        if (preset == cosmic.graphics.QualityPreset.LOW)  { qr = 1.0f; qg = 0.75f; qb = 0.25f; }
        if (preset == cosmic.graphics.QualityPreset.HIGH) { qr = 0.4f; qg = 0.70f; qb = 1.0f; }

        textOverlay.drawText(
            String.format("Quality: %-15s Steps: %d / %d",
                preset.getDisplayName(), preset.getMaxGeodesicSteps(), preset.getLightSteps()),
            INNER, curY, 0.37f, qr, qg, qb, 1.0f);
        curY += ROW;

        int dMode = config.getDebugMode();
        String modeName = (dMode >= 0 && dMode < DEBUG_MODE_NAMES.length) ? DEBUG_MODE_NAMES[dMode] : "Unknown";
        textOverlay.drawText(
            String.format("View:    %-15s Bloom: %-3s  Exp: %.2f",
                modeName, config.isBloom() ? "ON" : "OFF", config.getExposure()),
            INNER, curY, 0.37f, 0.92f, 0.88f, 0.60f, 1.0f);
        curY += ROW;

        // Cam Dist and Roll Angle (highlighted for user Q/E feedback)
        textOverlay.drawText(
            String.format("Camera:  Dist %4.1f Rs     Roll Angle (Q/E): %4.0f\u00b0",
                camera.getDistance(), Math.toDegrees(camera.getRoll())),
            INNER, curY, 0.37f, 0.40f, 0.88f, 0.98f, 1.0f);

        // ── CONTROLS FOOTER ────────────────────────────────────────────────────
        float fh = 48.0f;
        float fy = height - fh - 12.0f;
        float fw = width - PAD * 2.0f;
        textOverlay.drawPanel(PAD, fy, fw, fh);

        float fx = PAD + 14.0f;
        textOverlay.drawText(
            "Drag / A / D: Orbit   Up / Down: Pitch   Q / E: Roll (Angle)   W / S: Zoom   1 / 2 / 3: Quality",
            fx, fy + 10.0f, 0.36f, 0.75f, 0.88f, 0.98f, 0.90f);
        textOverlay.drawText(
            "Tab: Debug View   B: Bloom   C: Palettes   [ / ]: Density   - / +: Exposure   R: Reset   F12: Screenshot   ~: HUD",
            fx, fy + 26.0f, 0.34f, 0.48f, 0.65f, 0.80f, 0.75f);

        textOverlay.end();
    }

    /**
     * Captures the current displayed frame buffer to an uncompressed PNG image file.
     *
     * @param filepath absolute or relative file path to save the PNG
     * @return true if write succeeded
     */
    public boolean captureScreenshot(String filepath) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        // Flip image vertically (OpenGL originates bottom-left, PNG expects top-left)
        ByteBuffer flipped = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int srcIdx = ((height - 1 - y) * width + x) * 4;
                int dstIdx = (y * width + x) * 4;
                flipped.put(dstIdx, buffer.get(srcIdx));
                flipped.put(dstIdx + 1, buffer.get(srcIdx + 1));
                flipped.put(dstIdx + 2, buffer.get(srcIdx + 2));
                flipped.put(dstIdx + 3, buffer.get(srcIdx + 3));
            }
        }

        return stbi_write_png(filepath, width, height, 4, flipped, width * 4);
    }

    @Override
    public void bind() {}

    @Override
    public void unbind() {}

    @Override
    public void destroy() {
        noiseTexture.destroy();
        quad.destroy();
        blackholeShader.destroy();
        brightpassShader.destroy();
        blurShader.destroy();
        compositeShader.destroy();
        textOverlay.destroy();
        sceneFbo.destroy();
        brightFbo.destroy();
        blurFbo.destroy();
    }
}
