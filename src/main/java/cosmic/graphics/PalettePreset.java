package cosmic.graphics;

/** Coordinated HDR color stops for the disk and procedural sky. */
public enum PalettePreset {
    GARGANTUA_AMBER(
        "GARGANTUA AMBER",
        stops(
            rgb(1.10f, 0.18f, 0.018f), rgb(1.62f, 0.34f, 0.055f),
            rgb(2.30f, 0.64f, 0.12f), rgb(3.10f, 1.48f, 0.48f),
            rgb(3.80f, 3.40f, 2.85f)
        ),
        rgb(0.0007f, 0.00028f, 0.00006f), rgb(0.014f, 0.0045f, 0.0008f),
        rgb(0.95f, 0.55f, 0.18f), rgb(1.35f, 0.88f, 0.34f)
    ),
    WHITE_HOT(
        "WHITE HOT",
        stops(
            rgb(0.90f, 0.91f, 0.95f), rgb(1.55f, 1.62f, 1.78f),
            rgb(2.35f, 2.45f, 2.65f), rgb(3.25f, 3.38f, 3.62f),
            rgb(4.00f, 4.00f, 4.00f)
        ),
        rgb(0.00035f, 0.00048f, 0.0008f), rgb(0.003f, 0.006f, 0.016f),
        rgb(0.72f, 0.84f, 1.05f), rgb(1.10f, 1.32f, 1.70f)
    ),
    BLUE_PLASMA(
        "BLUE PLASMA",
        stops(
            rgb(0.08f, 0.12f, 0.62f), rgb(0.10f, 0.30f, 1.05f),
            rgb(0.22f, 0.85f, 2.00f), rgb(1.18f, 2.50f, 3.85f),
            rgb(3.65f, 4.05f, 5.00f)
        ),
        rgb(0.00012f, 0.00022f, 0.0010f), rgb(0.001f, 0.004f, 0.022f),
        rgb(0.30f, 0.58f, 1.30f), rgb(0.72f, 1.15f, 2.00f)
    ),
    VERDELITE_GREEN(
        "VERDELITE GREEN",
        stops(
            rgb(0.035f, 0.32f, 0.075f), rgb(0.07f, 0.92f, 0.22f),
            rgb(0.30f, 1.95f, 0.68f), rgb(1.42f, 3.30f, 2.05f),
            rgb(3.75f, 4.15f, 3.65f)
        ),
        rgb(0.00010f, 0.00055f, 0.00022f), rgb(0.001f, 0.018f, 0.006f),
        rgb(0.28f, 0.92f, 0.42f), rgb(0.72f, 1.48f, 0.90f)
    ),
    VIOLET_NEBULA(
        "VIOLET NEBULA",
        stops(
            rgb(0.20f, 0.025f, 0.52f), rgb(0.62f, 0.055f, 1.18f),
            rgb(1.48f, 0.32f, 2.45f), rgb(2.85f, 1.55f, 3.75f),
            rgb(4.00f, 3.70f, 4.65f)
        ),
        rgb(0.00035f, 0.00010f, 0.00075f), rgb(0.006f, 0.001f, 0.020f),
        rgb(0.62f, 0.34f, 1.10f), rgb(1.15f, 0.78f, 1.75f)
    );

    private final String displayName;
    private final Rgb[] diskStops;
    private final Rgb skyBase;
    private final Rgb skyDust;
    private final Rgb starColorDim;
    private final Rgb starColorBright;
    private final Rgb[] skyPreviewStops;

    PalettePreset(String displayName, Rgb[] diskStops, Rgb skyBase, Rgb skyDust,
                  Rgb starColorDim, Rgb starColorBright) {
        this.displayName = displayName;
        this.diskStops = diskStops;
        this.skyBase = skyBase;
        this.skyDust = skyDust;
        this.starColorDim = starColorDim;
        this.starColorBright = starColorBright;
        this.skyPreviewStops = new Rgb[] {
            skyBase.visibleTint(0.08f), skyDust.visibleTint(0.45f),
            starColorDim.visibleTint(0.72f), starColorBright.visibleTint(0.95f)
        };
    }

    private static Rgb[] stops(Rgb a, Rgb b, Rgb c, Rgb d, Rgb e) {
        return new Rgb[] { a, b, c, d, e };
    }

    private static Rgb rgb(float r, float g, float b) {
        return new Rgb(r, g, b);
    }

    public String getDisplayName() { return displayName; }
    public Rgb getDiskStop(int index) { return diskStops[index]; }
    public Rgb getSkyBase() { return skyBase; }
    public Rgb getSkyDust() { return skyDust; }
    public Rgb getStarColorDim() { return starColorDim; }
    public Rgb getStarColorBright() { return starColorBright; }

    public PalettePreset cycle(int direction) {
        PalettePreset[] presets = values();
        return presets[Math.floorMod(ordinal() + direction, presets.length)];
    }

    public Rgb diskPreview(float position) {
        float t = clamp(position, 0.0f, 1.0f);
        Rgb tone = mix(diskStops[0], diskStops[1], smoothstep(0.06f, 0.27f, t));
        tone = mix(tone, diskStops[2], smoothstep(0.24f, 0.49f, t));
        tone = mix(tone, diskStops[3], smoothstep(0.46f, 0.73f, t));
        tone = mix(tone, diskStops[4], smoothstep(0.70f, 0.97f, t));
        return tone.visibleTint(0.88f);
    }

    public Rgb skyPreview(float position) {
        float scaled = clamp(position, 0.0f, 1.0f) * (skyPreviewStops.length - 1);
        int index = Math.min(skyPreviewStops.length - 2, (int) scaled);
        return mix(skyPreviewStops[index], skyPreviewStops[index + 1], scaled - index);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Rgb mix(Rgb a, Rgb b, float t) {
        return new Rgb(a.r + (b.r - a.r) * t,
                       a.g + (b.g - a.g) * t,
                       a.b + (b.b - a.b) * t);
    }

    public record Rgb(float r, float g, float b) {
        private Rgb visibleTint(float brightness) {
            float peak = Math.max(r, Math.max(g, b));
            if (peak <= 1e-8f) return new Rgb(brightness, brightness, brightness);
            return new Rgb(r / peak * brightness, g / peak * brightness, b / peak * brightness);
        }
    }
}
