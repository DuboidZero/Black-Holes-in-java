package cosmic.util;

import cosmic.graphics.QualityPreset;
import cosmic.graphics.PalettePreset;

import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration holder for physical, volumetric, and rendering parameters loaded from
 * {@code blackhole.properties} on the classpath.
 */
public class Config {

    private static final String DEFAULT_CONFIG_FILE = "blackhole.properties";

    private double massSolarMasses = 10.0;
    private double cameraDistance = 95.0;
    private double cameraRoll = -34.0;
    private double diskInnerRadius = 5.2;
    private double diskOuterRadius = 22.0;
    private double diskTemperature = 50000.0;
    private double diskThickness = 0.63;
    private double densityScale = 1.15;
    private double absorption = 0.85;
    private double turbulenceScale = 1.00;
    private double turbulenceStrength = 0.85;
    private double rotationSpeed = 1.20;
    private double spiralStrength = 2.80;
    private double emissionStrength = 1.40;
    private double innerIllumination = 1.20;

    private QualityPreset qualityPreset = QualityPreset.MEDIUM;
    private PalettePreset colorPalette = PalettePreset.GARGANTUA_AMBER;
    private int debugMode = 0; // 0=Final, 1=Density, 2=Temp, 3=OpticalDepth, 4=Emission, 5=Absorption, 6=Lensing, 7=Geodesic
    private int raySteps = 160;
    private double rayStepSize = 0.24;
    private double exposure = 1.2;
    private boolean bloom = true;
    private int windowWidth = 1280;
    private int windowHeight = 720;
    private boolean showDebug = false;

    public Config() {
        loadDefaults();
    }

    /**
     * Loads configuration values from the default properties file.
     * If the file is missing or invalid, fallback defaults are preserved.
     */
    public void loadDefaults() {
        try (InputStream in = ResourceLoader.getResourceAsStream(DEFAULT_CONFIG_FILE)) {
            Properties props = new Properties();
            props.load(in);
            loadFromProperties(props);
        } catch (Exception e) {
            System.err.println("[Config] Note: using default settings (" + e.getMessage() + ")");
        }
    }

    /**
     * Parses configuration from a {@link Properties} instance.
     */
    public void loadFromProperties(Properties props) {
        massSolarMasses = getDoubleProperty(props, "massSolarMasses", massSolarMasses);
        cameraDistance = getDoubleProperty(props, "cameraDistance", cameraDistance);
        cameraRoll = getDoubleProperty(props, "cameraRoll", cameraRoll);
        diskInnerRadius = getDoubleProperty(props, "diskInnerRadius", diskInnerRadius);
        diskOuterRadius = getDoubleProperty(props, "diskOuterRadius", diskOuterRadius);
        diskTemperature = getDoubleProperty(props, "diskTemperature", diskTemperature);
        diskThickness = getDoubleProperty(props, "diskThickness", diskThickness);
        densityScale = getDoubleProperty(props, "densityScale", densityScale);
        absorption = getDoubleProperty(props, "absorption", absorption);
        turbulenceScale = getDoubleProperty(props, "turbulenceScale", turbulenceScale);
        turbulenceStrength = getDoubleProperty(props, "turbulenceStrength", turbulenceStrength);
        rotationSpeed = getDoubleProperty(props, "rotationSpeed", rotationSpeed);
        spiralStrength = getDoubleProperty(props, "spiralStrength", spiralStrength);
        emissionStrength = getDoubleProperty(props, "emissionStrength", emissionStrength);
        innerIllumination = getDoubleProperty(props, "innerIllumination", innerIllumination);

        String presetStr = props.getProperty("qualityPreset");
        if (presetStr != null) {
            try {
                qualityPreset = QualityPreset.valueOf(presetStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        String paletteStr = props.getProperty("colorPalette");
        if (paletteStr != null) {
            try {
                colorPalette = PalettePreset.valueOf(paletteStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        debugMode = getIntProperty(props, "debugMode", debugMode);
        raySteps = getIntProperty(props, "raySteps", qualityPreset.getMaxGeodesicSteps());
        rayStepSize = getDoubleProperty(props, "rayStepSize", qualityPreset.getBaseStepSize());
        exposure = getDoubleProperty(props, "exposure", exposure);
        bloom = getBooleanProperty(props, "bloom", bloom);
        windowWidth = getIntProperty(props, "windowWidth", windowWidth);
        windowHeight = getIntProperty(props, "windowHeight", windowHeight);
        showDebug = getBooleanProperty(props, "showDebug", showDebug);
    }

    private double getDoubleProperty(Properties props, String key, double defaultVal) {
        String val = props.getProperty(key);
        if (val != null) {
            try {
                return Double.parseDouble(val.trim());
            } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    private int getIntProperty(Properties props, String key, int defaultVal) {
        String val = props.getProperty(key);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    private boolean getBooleanProperty(Properties props, String key, boolean defaultVal) {
        String val = props.getProperty(key);
        if (val != null) {
            return Boolean.parseBoolean(val.trim());
        }
        return defaultVal;
    }

    // Getters and Setters
    public double getMassSolarMasses() { return massSolarMasses; }
    public void setMassSolarMasses(double massSolarMasses) { this.massSolarMasses = massSolarMasses; }

    public double getCameraDistance() { return cameraDistance; }
    public void setCameraDistance(double cameraDistance) { this.cameraDistance = cameraDistance; }

    public double getCameraRoll() { return cameraRoll; }
    public void setCameraRoll(double cameraRoll) { this.cameraRoll = cameraRoll; }

    public double getDiskInnerRadius() { return diskInnerRadius; }
    public void setDiskInnerRadius(double diskInnerRadius) { this.diskInnerRadius = diskInnerRadius; }

    public double getDiskOuterRadius() { return diskOuterRadius; }
    public void setDiskOuterRadius(double diskOuterRadius) { this.diskOuterRadius = diskOuterRadius; }

    public double getDiskTemperature() { return diskTemperature; }
    public void setDiskTemperature(double diskTemperature) { this.diskTemperature = diskTemperature; }

    public double getDiskThickness() { return diskThickness; }
    public void setDiskThickness(double diskThickness) { this.diskThickness = diskThickness; }

    public double getDensityScale() { return densityScale; }
    public void setDensityScale(double densityScale) { this.densityScale = densityScale; }

    public double getAbsorption() { return absorption; }
    public void setAbsorption(double absorption) { this.absorption = absorption; }

    public double getTurbulenceScale() { return turbulenceScale; }
    public void setTurbulenceScale(double turbulenceScale) { this.turbulenceScale = turbulenceScale; }

    public double getTurbulenceStrength() { return turbulenceStrength; }
    public void setTurbulenceStrength(double turbulenceStrength) { this.turbulenceStrength = turbulenceStrength; }

    public double getRotationSpeed() { return rotationSpeed; }
    public void setRotationSpeed(double rotationSpeed) { this.rotationSpeed = rotationSpeed; }

    public double getSpiralStrength() { return spiralStrength; }
    public void setSpiralStrength(double spiralStrength) { this.spiralStrength = spiralStrength; }

    public double getEmissionStrength() { return emissionStrength; }
    public void setEmissionStrength(double emissionStrength) { this.emissionStrength = emissionStrength; }

    public double getInnerIllumination() { return innerIllumination; }
    public void setInnerIllumination(double innerIllumination) { this.innerIllumination = innerIllumination; }

    public QualityPreset getQualityPreset() { return qualityPreset; }
    public void setQualityPreset(QualityPreset qualityPreset) {
        this.qualityPreset = qualityPreset;
        this.raySteps = qualityPreset.getMaxGeodesicSteps();
        this.rayStepSize = qualityPreset.getBaseStepSize();
    }

    public PalettePreset getColorPalette() { return colorPalette; }
    public void setColorPalette(PalettePreset colorPalette) {
        if (colorPalette != null) this.colorPalette = colorPalette;
    }

    public int getDebugMode() { return debugMode; }
    public void setDebugMode(int debugMode) { this.debugMode = (debugMode % 8 + 8) % 8; }

    public int getRaySteps() { return raySteps; }
    public void setRaySteps(int raySteps) { this.raySteps = raySteps; }

    public double getRayStepSize() { return rayStepSize; }
    public void setRayStepSize(double rayStepSize) { this.rayStepSize = rayStepSize; }

    public double getExposure() { return exposure; }
    public void setExposure(double exposure) { this.exposure = exposure; }

    public boolean isBloom() { return bloom; }
    public void setBloom(boolean bloom) { this.bloom = bloom; }

    public int getWindowWidth() { return windowWidth; }
    public void setWindowWidth(int windowWidth) { this.windowWidth = windowWidth; }

    public int getWindowHeight() { return windowHeight; }
    public void setWindowHeight(int windowHeight) { this.windowHeight = windowHeight; }

    public boolean isShowDebug() { return showDebug; }
    public void setShowDebug(boolean showDebug) { this.showDebug = showDebug; }
}
