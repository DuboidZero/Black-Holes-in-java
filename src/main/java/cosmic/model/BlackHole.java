package cosmic.model;

import cosmic.math.Vec3;
import cosmic.physics.Schwarzschild;
import cosmic.util.Resettable;

/**
 * High-level domain model of a Schwarzschild black hole.
 *
 * <p>Encapsulates physical parameters, coordinate location,
 * event horizon, photon sphere, and ISCO boundaries.</p>
 */
public class BlackHole implements Resettable {

    private final double initialMassSolarMasses;
    private double massSolarMasses;
    private Schwarzschild spacetime;
    private Vec3 position;

    // Normalized dimensions in shader coordinate space
    private double normalizedRs;
    private double normalizedRPhoton;
    private double normalizedRIsco;

    public BlackHole(double massSolarMasses) {
        this.initialMassSolarMasses = massSolarMasses;
        this.position = Vec3.ZERO;
        this.normalizedRs = Schwarzschild.NORMALIZED_RS; // 2.0
        this.normalizedRPhoton = 1.5 * normalizedRs;     // 3.0
        this.normalizedRIsco = 3.0 * normalizedRs;       // 6.0
        setMassSolarMasses(massSolarMasses);
    }

    public BlackHole() {
        this(10.0);
    }

    public void setMassSolarMasses(double massSolarMasses) {
        this.massSolarMasses = massSolarMasses;
        this.spacetime = new Schwarzschild(massSolarMasses);
    }

    @Override
    public void reset() {
        setMassSolarMasses(initialMassSolarMasses);
        this.position = Vec3.ZERO;
    }

    public double getMassSolarMasses() { return massSolarMasses; }
    public Schwarzschild getSpacetime() { return spacetime; }
    public Vec3 getPosition() { return position; }
    public void setPosition(Vec3 position) { this.position = position; }

    public double getNormalizedRs() { return normalizedRs; }
    public double getNormalizedRPhoton() { return normalizedRPhoton; }
    public double getNormalizedRIsco() { return normalizedRIsco; }

    public double getPhysicalRsKm() {
        return spacetime.getPhysicalRsMeters() / 1000.0;
    }

    public double getPhysicalPhotonSphereKm() {
        return spacetime.getPhysicalRPhotonMeters() / 1000.0;
    }
}
