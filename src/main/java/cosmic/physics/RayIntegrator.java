package cosmic.physics;

import cosmic.math.MathUtil;
import cosmic.math.Vec3;

/**
 * Controller for numerical integration of light geodesics in Schwarzschild spacetime.
 * Supports configurable integration algorithms (Euler vs 4th-order Runge-Kutta)
 * and step parameters.
 */
public class RayIntegrator {

    /**
     * Numerical integration scheme selector.
     */
    public enum IntegratorType {
        EULER("Euler (1st Order)"),
        RK4("Runge-Kutta (4th Order)");

        private final String displayName;

        IntegratorType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private IntegratorType type;
    private int maxSteps;
    private double baseStepSize;
    private double rs;
    private double escapeRadius;

    public RayIntegrator(IntegratorType type, int maxSteps, double baseStepSize, double rs) {
        this.type = type;
        this.maxSteps = maxSteps;
        this.baseStepSize = baseStepSize;
        this.rs = rs;
        this.escapeRadius = 150.0;
    }

    public RayIntegrator() {
        this(IntegratorType.RK4, 200, 0.28, Schwarzschild.NORMALIZED_RS);
    }

    /**
     * Integrates a geodesic on the CPU until capture, escape, or step exhaustion.
     * Useful for verification, unit testing, and diagnostic ray tracing.
     *
     * @param origin starting position (e.g. camera location)
     * @param direction initial direction vector (pointing away from camera)
     * @return the integrated geodesic state
     */
    public Geodesic trace(Vec3 origin, Vec3 direction) {
        Geodesic ray = new Geodesic(origin, direction);

        for (int step = 0; step < maxSteps; step++) {
            if (ray.isTerminated()) {
                break;
            }

            // Adaptive step size based on distance from black hole
            double r = ray.getPosition().length();
            double distToHorizon = Math.max(0.02, r - rs);
            double nearScale = MathUtil.clamp(distToHorizon / (rs * 2.0), 0.06, 1.0);
            double farScale = Math.max(1.0, r / (rs * 3.0));
            double dt = MathUtil.clamp(baseStepSize * nearScale * farScale, 0.025, 4.5);

            if (type == IntegratorType.RK4) {
                ray.stepRK4(dt, rs);
            } else {
                ray.stepEuler(dt, rs);
            }
        }

        return ray;
    }

    public IntegratorType getType() { return type; }
    public void setType(IntegratorType type) { this.type = type; }

    public int getMaxSteps() { return maxSteps; }
    public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }

    public double getBaseStepSize() { return baseStepSize; }
    public void setBaseStepSize(double baseStepSize) { this.baseStepSize = baseStepSize; }

    public double getRs() { return rs; }
    public void setRs(double rs) { this.rs = rs; }

    public double getEscapeRadius() { return escapeRadius; }
    public void setEscapeRadius(double escapeRadius) { this.escapeRadius = escapeRadius; }
}
