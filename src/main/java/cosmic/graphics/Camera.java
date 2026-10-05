package cosmic.graphics;

import cosmic.math.MathUtil;
import cosmic.math.Vec3;
import cosmic.util.Resettable;

/**
 * Spherical orbital camera for interactive 3D navigation around the black hole.
 *
 * <p>Controls azimuth (horizontal orbit), elevation (pitch), and radial distance.
 * Computes forward, right, and up view vectors for perspective ray generation in GLSL.</p>
 */
public class Camera implements Resettable {

    private final double initialDistance;
    private final double initialAzimuth;
    private final double initialElevation;
    private final double initialRoll;

    private double distance;
    private double azimuth;    // radians
    private double elevation;  // radians
    private double roll;       // radians — cinematic Dutch angle around view forward
    private double fovDegrees;

    private Vec3 target;
    private Vec3 position;
    private Vec3 forward;
    private Vec3 right;
    private Vec3 up;

    public Camera(double initialDistance) {
        this(initialDistance, -34.0);
    }

    public Camera(double initialDistance, double initialRollDegrees) {
        this.initialDistance = initialDistance;
        this.initialAzimuth = MathUtil.toRadians(25.0);
        this.initialElevation = MathUtil.toRadians(18.0); // cinematic pitch matching reference
        this.initialRoll = MathUtil.toRadians(initialRollDegrees);
        this.fovDegrees = 50.0;
        this.target = Vec3.ZERO;

        reset();
    }

    public Camera() {
        this(22.0);
    }

    /**
     * Updates camera orientation based on delta mouse/key input.
     *
     * @param deltaAzimuth horizontal rotation in radians
     * @param deltaElevation vertical pitch in radians
     */
    public void orbit(double deltaAzimuth, double deltaElevation) {
        this.azimuth += deltaAzimuth;
        this.elevation = MathUtil.clamp(
            this.elevation + deltaElevation,
            MathUtil.toRadians(-88.0),
            MathUtil.toRadians(88.0)
        );
        updateVectors();
    }

    /**
     * Adjusts the Dutch roll angle (rotation around view forward axis).
     *
     * @param deltaRoll roll change in radians
     */
    public void addRoll(double deltaRoll) {
        this.roll += deltaRoll;
        updateVectors();
    }

    /**
     * Zooms the camera by adjusting distance from target.
     *
     * @param delta distance change
     */
    public void zoom(double delta) {
        this.distance = MathUtil.clamp(this.distance + delta, 4.0, 250.0);
        updateVectors();
    }

    @Override
    public void reset() {
        this.distance = initialDistance;
        this.azimuth = initialAzimuth;
        this.elevation = initialElevation;
        this.roll = initialRoll;
        updateVectors();
    }

    private void updateVectors() {
        // Spherical to Cartesian coordinates
        double cosElev = Math.cos(elevation);
        double sinElev = Math.sin(elevation);
        double sinAzim = Math.sin(azimuth);
        double cosAzim = Math.cos(azimuth);

        double posX = target.x() + distance * cosElev * sinAzim;
        double posY = target.y() + distance * sinElev;
        double posZ = target.z() + distance * cosElev * cosAzim;

        this.position = new Vec3(posX, posY, posZ);

        // Forward vector (from position towards target)
        this.forward = target.subtract(position).normalize();

        // Right vector (forward x world-up)
        Vec3 worldUp = Vec3.UNIT_Y;
        Vec3 baseRight = forward.cross(worldUp).normalize();
        if (baseRight.lengthSquared() < 1e-6) {
            baseRight = Vec3.UNIT_X;
        }

        // Apply cinematic roll rotation around the forward axis
        double cosRoll = Math.cos(roll);
        double sinRoll = Math.sin(roll);
        Vec3 baseUp = baseRight.cross(forward).normalize();

        this.right = baseRight.multiply(cosRoll).add(baseUp.multiply(sinRoll));
        this.up    = baseUp.multiply(cosRoll).subtract(baseRight.multiply(sinRoll));
    }

    public Vec3 getPosition() { return position; }
    public Vec3 getForward() { return forward; }
    public Vec3 getRight() { return right; }
    public Vec3 getUp() { return up; }
    public double getDistance() { return distance; }
    public double getAzimuth() { return azimuth; }
    public double getElevation() { return elevation; }
    public double getRoll() { return roll; }
    public void setRoll(double rollRadians) { this.roll = rollRadians; updateVectors(); }
    public double getFovDegrees() { return fovDegrees; }
    public void setFovDegrees(double fovDegrees) { this.fovDegrees = fovDegrees; }
}
