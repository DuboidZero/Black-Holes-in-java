package cosmic.model;

import cosmic.math.MathUtil;
import cosmic.util.Resettable;

/**
 * Domain model representing a 3D volumetric, differentially rotating relativistic
 * accretion plasma field in the equatorial region of the black hole.
 *
 * <p>Implements the standard Shakura-Sunyaev thin-disk temperature profile:
 * {@code T(r) = T_ref * (r_in / r)^(3/4) * (1 - sqrt(r_in / r))^(1/4)},
 * flared vertical disk geometry {@code h(r) = h_0 * (1 + 0.5 * sqrt(r / r_in))},
 * and volumetric parameters for Beer-Lambert extinction, procedural turbulence,
 * differential Keplerian rotation, and inner-disk illumination proxy.</p>
 */
public class AccretionDisk implements Resettable {

    private final double initialInnerRadius;
    private final double initialOuterRadius;
    private final double initialReferenceTemp;
    private final double initialThickness;
    private final double initialDensityScale;
    private final double initialAbsorption;

    private double innerRadius;
    private double outerRadius;
    private double referenceTemperature; // Kelvin
    private double thickness;            // Base half-height h_0 in Rs units
    private double densityScale;        // Overall volumetric density multiplier
    private double absorption;          // Beer-Lambert extinction coefficient
    private double turbulenceScale;     // Spatial noise frequency
    private double turbulenceStrength;  // Procedural noise amplitude
    private double rotationSpeed;       // Differential angular velocity scaling
    private double spiralStrength;      // Logarithmic spiral pitch
    private double emissionStrength;    // Radiative emission scaling
    private double innerIllumination;   // Inner accretion-flow irradiation strength
    private double opacity;
    private double brightness;

    public AccretionDisk(double innerRadius, double outerRadius, double referenceTemperature,
                         double thickness, double densityScale, double absorption) {
        this.initialInnerRadius = innerRadius;
        this.initialOuterRadius = outerRadius;
        this.initialReferenceTemp = referenceTemperature;
        this.initialThickness = thickness;
        this.initialDensityScale = densityScale;
        this.initialAbsorption = absorption;

        this.innerRadius = innerRadius;
        this.outerRadius = outerRadius;
        this.referenceTemperature = referenceTemperature;
        this.thickness = thickness;
        this.densityScale = densityScale;
        this.absorption = absorption;

        this.turbulenceScale = 0.45;
        this.turbulenceStrength = 0.65;
        this.rotationSpeed = 1.20;
        this.spiralStrength = 3.0;
        this.emissionStrength = 1.80;
        this.innerIllumination = 1.40;
        this.opacity = 0.90;
        this.brightness = 1.0;
    }

    public AccretionDisk(double innerRadius, double outerRadius, double referenceTemperature) {
        this(innerRadius, outerRadius, referenceTemperature, 0.85, 1.25, 0.85);
    }

    public AccretionDisk() {
        this(6.0, 30.0, 50000.0, 0.85, 1.25, 0.85);
    }

    /**
     * Calculates the local blackbody temperature at radius r based on the
     * relativistic thin-disk temperature profile:
     * {@code T(r) = T_ref * (r_in / r)^(3/4) * (1 - sqrt(r_in / r))^(1/4)}.
     *
     * @param r radial coordinate in the disk plane
     * @return local plasma temperature in Kelvin
     */
    public double calculateTemperature(double r) {
        if (r < innerRadius || r > outerRadius) {
            return 0.0;
        }
        double ratio = innerRadius / r;
        double torqueFalloff = Math.max(0.0, 1.0 - Math.sqrt(ratio));
        return referenceTemperature * Math.pow(ratio, 0.75) * Math.pow(torqueFalloff, 0.25) * 1.5;
    }

    /**
     * Calculates the flared vertical half-thickness h(r) of the accretion disk.
     * Hydrostatic equilibrium causes accretion disks to flare outward with radius.
     *
     * @param r radial coordinate in disk plane
     * @return vertical half-thickness at radius r
     */
    public double calculateFlaredHeight(double r) {
        if (r <= 0.0) return thickness;
        double normR = Math.max(1.0, r / innerRadius);
        return thickness * (1.0 + 0.45 * Math.sqrt(normR - 1.0));
    }

    @Override
    public void reset() {
        this.innerRadius = initialInnerRadius;
        this.outerRadius = initialOuterRadius;
        this.referenceTemperature = initialReferenceTemp;
        this.thickness = initialThickness;
        this.densityScale = initialDensityScale;
        this.absorption = initialAbsorption;
        this.turbulenceScale = 0.45;
        this.turbulenceStrength = 0.65;
        this.rotationSpeed = 1.20;
        this.spiralStrength = 3.0;
        this.emissionStrength = 1.80;
        this.innerIllumination = 1.40;
        this.opacity = 0.90;
        this.brightness = 1.0;
    }

    public double getInnerRadius() { return innerRadius; }
    public void setInnerRadius(double innerRadius) { this.innerRadius = innerRadius; }

    public double getOuterRadius() { return outerRadius; }
    public void setOuterRadius(double outerRadius) { this.outerRadius = outerRadius; }

    public double getReferenceTemperature() { return referenceTemperature; }
    public void setReferenceTemperature(double referenceTemperature) { this.referenceTemperature = referenceTemperature; }

    public double getThickness() { return thickness; }
    public void setThickness(double thickness) { this.thickness = Math.max(0.05, thickness); }

    public double getDensityScale() { return densityScale; }
    public void setDensityScale(double densityScale) { this.densityScale = Math.max(0.01, densityScale); }

    public double getAbsorption() { return absorption; }
    public void setAbsorption(double absorption) { this.absorption = Math.max(0.0, absorption); }

    public double getTurbulenceScale() { return turbulenceScale; }
    public void setTurbulenceScale(double turbulenceScale) { this.turbulenceScale = turbulenceScale; }

    public double getTurbulenceStrength() { return turbulenceStrength; }
    public void setTurbulenceStrength(double turbulenceStrength) { this.turbulenceStrength = turbulenceStrength; }

    public double getRotationSpeed() { return rotationSpeed; }
    public void setRotationSpeed(double rotationSpeed) { this.rotationSpeed = rotationSpeed; }

    public double getSpiralStrength() { return spiralStrength; }
    public void setSpiralStrength(double spiralStrength) { this.spiralStrength = spiralStrength; }

    public double getEmissionStrength() { return emissionStrength; }
    public void setEmissionStrength(double emissionStrength) { this.emissionStrength = Math.max(0.0, emissionStrength); }

    public double getInnerIllumination() { return innerIllumination; }
    public void setInnerIllumination(double innerIllumination) { this.innerIllumination = Math.max(0.0, innerIllumination); }

    public double getOpacity() { return opacity; }
    public void setOpacity(double opacity) { this.opacity = MathUtil.clamp(opacity, 0.0, 1.0); }

    public double getBrightness() { return brightness; }
    public void setBrightness(double brightness) { this.brightness = Math.max(0.0, brightness); }
}
