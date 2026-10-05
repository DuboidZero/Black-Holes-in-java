package cosmic.graphics;

/**
 * Encapsulates rendering quality presets balancing visual fidelity and frame rate.
 *
 * <p>Controls maximum numerical geodesic integration steps, inner-disk illumination
 * shadow march steps, base numerical step size, and noise detail levels.</p>
 */
public enum QualityPreset {
    LOW("LOW (Preview)", 96, 2, 0.32f, 1),
    MEDIUM("MEDIUM (Balanced)", 160, 3, 0.24f, 2),
    HIGH("HIGH (Cinematic)", 240, 4, 0.18f, 3);

    private final String displayName;
    private final int maxGeodesicSteps;
    private final int lightSteps;
    private final float baseStepSize;
    private final int noiseDetailLevel;

    QualityPreset(String displayName, int maxGeodesicSteps, int lightSteps, float baseStepSize, int noiseDetailLevel) {
        this.displayName = displayName;
        this.maxGeodesicSteps = maxGeodesicSteps;
        this.lightSteps = lightSteps;
        this.baseStepSize = baseStepSize;
        this.noiseDetailLevel = noiseDetailLevel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxGeodesicSteps() {
        return maxGeodesicSteps;
    }

    public int getLightSteps() {
        return lightSteps;
    }

    public float getBaseStepSize() {
        return baseStepSize;
    }

    public int getNoiseDetailLevel() {
        return noiseDetailLevel;
    }

    /**
     * Cycles to the next quality preset.
     */
    public QualityPreset next() {
        QualityPreset[] vals = values();
        return vals[(ordinal() + 1) % vals.length];
    }
}
