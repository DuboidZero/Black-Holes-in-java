package cosmic.physics;

import cosmic.math.Vec3;

/**
 * Represents a single photon light-ray null geodesic trajectory through
 * curved Schwarzschild spacetime.
 *
 * <p>Contains the differential equation of motion for null geodesics in 3D:
 * {@code d²x/dλ² = -(3/2) * Rs * (|x × v|² / r⁵) * x}.
 * This equation rigorously preserves orbital angular momentum L = x × v
 * and exactly reproduces the general relativistic deflection angle and photon sphere orbit at r = 1.5 * Rs.</p>
 */
public class Geodesic {

    private Vec3 position;
    private Vec3 velocity;
    private double affineParam;
    private boolean captured;
    private boolean escaped;

    public Geodesic(Vec3 origin, Vec3 direction) {
        this.position = origin;
        this.velocity = direction.normalize();
        this.affineParam = 0.0;
        this.captured = false;
        this.escaped = false;
    }

    /**
     * Computes the general relativistic acceleration for a null geodesic at state (x, v):
     * {@code a = -1.5 * Rs * (|x × v|² / |x|⁵) * x}.
     *
     * @param pos ray position relative to black hole center
     * @param vel ray tangent velocity vector
     * @param rs Schwarzschild radius
     * @return 3D acceleration vector
     */
    public static Vec3 computeAcceleration(Vec3 pos, Vec3 vel, double rs) {
        double rSq = pos.lengthSquared();
        double r = Math.sqrt(rSq);
        if (r < 1e-6) {
            return Vec3.ZERO;
        }

        // Angular momentum vector h = pos × vel
        Vec3 h = pos.cross(vel);
        double hSq = h.lengthSquared();

        // Acceleration scalar factor: -(3/2) * Rs * h² / r⁵
        double r5 = rSq * rSq * r;
        double factor = -1.5 * rs * hSq / r5;

        return pos.multiply(factor);
    }

    /**
     * Advances the geodesic ray state by one step using Euler's method.
     */
    public void stepEuler(double dt, double rs) {
        Vec3 acc = computeAcceleration(position, velocity, rs);
        position = position.add(velocity.multiply(dt));
        velocity = velocity.add(acc.multiply(dt));
        affineParam += dt;
        checkBounds(rs, 100.0);
    }

    /**
     * Advances the geodesic ray state by one step using 4th-order Runge-Kutta (RK4).
     */
    public void stepRK4(double dt, double rs) {
        // k1
        Vec3 x0 = position;
        Vec3 v0 = velocity;
        Vec3 a1 = computeAcceleration(x0, v0, rs);
        Vec3 dx1 = v0;
        Vec3 dv1 = a1;

        // k2
        Vec3 x1 = x0.add(dx1.multiply(0.5 * dt));
        Vec3 v1 = v0.add(dv1.multiply(0.5 * dt));
        Vec3 a2 = computeAcceleration(x1, v1, rs);
        Vec3 dx2 = v1;
        Vec3 dv2 = a2;

        // k3
        Vec3 x2 = x0.add(dx2.multiply(0.5 * dt));
        Vec3 v2 = v0.add(dv2.multiply(0.5 * dt));
        Vec3 a3 = computeAcceleration(x2, v2, rs);
        Vec3 dx3 = v2;
        Vec3 dv3 = a3;

        // k4
        Vec3 x3 = x0.add(dx3.multiply(dt));
        Vec3 v3 = v0.add(dv3.multiply(dt));
        Vec3 a4 = computeAcceleration(x3, v3, rs);
        Vec3 dx4 = v3;
        Vec3 dv4 = a4;

        // Update position and velocity
        Vec3 dx = dx1.add(dx2.multiply(2.0)).add(dx3.multiply(2.0)).add(dx4).multiply(dt / 6.0);
        Vec3 dv = dv1.add(dv2.multiply(2.0)).add(dv3.multiply(2.0)).add(dv4).multiply(dt / 6.0);

        position = position.add(dx);
        velocity = velocity.add(dv);
        affineParam += dt;

        checkBounds(rs, 100.0);
    }

    private void checkBounds(double rs, double rEscape) {
        double r = position.length();
        if (r <= rs * 1.001) {
            captured = true;
        } else if (r >= rEscape && position.dot(velocity) > 0.0) {
            escaped = true;
        }
    }

    public Vec3 getPosition() { return position; }
    public Vec3 getVelocity() { return velocity; }
    public double getAffineParam() { return affineParam; }
    public boolean isCaptured() { return captured; }
    public boolean isEscaped() { return escaped; }
    public boolean isTerminated() { return captured || escaped; }
}
