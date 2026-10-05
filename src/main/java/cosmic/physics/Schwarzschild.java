package cosmic.physics;

import cosmic.math.MathUtil;
import cosmic.math.Vec3;

/**
 * Mathematical formulation of the Schwarzschild spacetime geometry
 * for a non-rotating (spherically symmetric) static black hole.
 *
 * <p>Key relativistic equations:
 * <ul>
 *   <li><b>Schwarzschild Radius:</b> {@code Rs = 2GM / c²}</li>
 *   <li><b>Photon Sphere:</b> {@code r_photon = 3GM / c² = 1.5 * Rs}</li>
 *   <li><b>Innermost Stable Circular Orbit (ISCO):</b> {@code r_isco = 6GM / c² = 3.0 * Rs}</li>
 *   <li><b>Gravitational Redshift Factor:</b> {@code g_grav = sqrt(1 - Rs / r)}</li>
 *   <li><b>Orbital Velocity (Keplerian in GR):</b> {@code beta = v/c = sqrt(Rs / (2r))}</li>
 *   <li><b>Doppler Factor:</b> {@code delta = sqrt(1 - beta²) / (1 - beta * cos(theta))}</li>
 * </ul>
 * </p>
 */
public final class Schwarzschild {

    /** Standard normalized mass M = 1.0 in geometric units (G = c = 1) */
    public static final double NORMALIZED_M = 1.0;

    /** Standard normalized Schwarzschild radius in geometric units (Rs = 2M = 2.0) */
    public static final double NORMALIZED_RS = 2.0 * NORMALIZED_M;

    private final double massSolarMasses;
    private final double physicalMassKg;
    private final double physicalRsMeters;
    private final double physicalRPhotonMeters;
    private final double physicalRIscoMeters;

    /**
     * Constructs a Schwarzschild spacetime model for a given mass in solar masses.
     *
     * @param massSolarMasses mass in solar masses (M☉)
     */
    public Schwarzschild(double massSolarMasses) {
        if (massSolarMasses <= 0.0) {
            throw new IllegalArgumentException("Mass must be strictly positive: " + massSolarMasses);
        }
        this.massSolarMasses = massSolarMasses;
        this.physicalMassKg = massSolarMasses * MathUtil.SOLAR_MASS_KG;
        this.physicalRsMeters = calculatePhysicalRs(this.physicalMassKg);
        this.physicalRPhotonMeters = 1.5 * this.physicalRsMeters;
        this.physicalRIscoMeters = 3.0 * this.physicalRsMeters;
    }

    /**
     * Calculates the physical Schwarzschild radius in meters:
     * {@code Rs = 2GM / c²}.
     */
    public static double calculatePhysicalRs(double massKg) {
        return (2.0 * MathUtil.G_SI * massKg) / (MathUtil.C_SI * MathUtil.C_SI);
    }

    /**
     * Calculates the physical photon sphere radius in meters:
     * {@code r_photon = 3GM / c² = 1.5 * Rs}.
     */
    public static double calculatePhysicalRPhoton(double massKg) {
        return 1.5 * calculatePhysicalRs(massKg);
    }

    /**
     * Calculates the gravitational redshift factor:
     * {@code g_grav = sqrt(1.0 - Rs / r)}.
     *
     * @param r radial coordinate (must be &gt; Rs)
     * @param rs Schwarzschild radius
     * @return redshift factor in range (0.0, 1.0]
     */
    public static double calculateGravitationalRedshift(double r, double rs) {
        if (r <= rs) return 0.0;
        return Math.sqrt(1.0 - rs / r);
    }

    /**
     * Computes the circular Keplerian orbital velocity (v/c) at radius r:
     * {@code beta = sqrt(Rs / (2r)) = sqrt(M / r)}.
     */
    public static double calculateOrbitalSpeed(double r, double rs) {
        if (r <= 0.5 * rs) return 1.0;
        return Math.min(1.0, Math.sqrt(rs / (2.0 * r)));
    }

    /**
     * Computes relativistic Doppler shift factor:
     * {@code delta = sqrt(1 - beta²) / (1 - v · n_obs)}.
     *
     * @param velocity orbital velocity vector (magnitude = beta &lt; 1)
     * @param viewDir unit vector towards the observer
     * @return Doppler factor delta
     */
    public static double calculateDopplerFactor(Vec3 velocity, Vec3 viewDir) {
        double betaSq = velocity.lengthSquared();
        if (betaSq >= 1.0) {
            betaSq = 0.9999;
        }
        double gammaInv = Math.sqrt(1.0 - betaSq);
        double cosAngle = velocity.dot(viewDir);
        double denom = 1.0 - cosAngle;
        if (denom < 1e-6) denom = 1e-6;
        return gammaInv / denom;
    }

    // Getters for physical parameters
    public double getMassSolarMasses() { return massSolarMasses; }
    public double getPhysicalMassKg() { return physicalMassKg; }
    public double getPhysicalRsMeters() { return physicalRsMeters; }
    public double getPhysicalRPhotonMeters() { return physicalRPhotonMeters; }
    public double getPhysicalRIscoMeters() { return physicalRIscoMeters; }

    @Override
    public String toString() {
        return String.format("Schwarzschild(Mass=%.2f M☉, Rs=%.2f km, R_photon=%.2f km, R_isco=%.2f km)",
            massSolarMasses, physicalRsMeters / 1000.0, physicalRPhotonMeters / 1000.0, physicalRIscoMeters / 1000.0);
    }
}
