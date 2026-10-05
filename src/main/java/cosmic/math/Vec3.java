package cosmic.math;

import java.util.Objects;

/**
 * Immutable 3-dimensional vector class representing spatial coordinates,
 * velocities, or color triples in 3D Euclidean and curved spacetime.
 *
 * <p>Demonstrates encapsulation, immutability, method chaining,
 * and standard mathematical vector operations.</p>
 */
public final class Vec3 {

    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);
    public static final Vec3 UNIT_X = new Vec3(1.0, 0.0, 0.0);
    public static final Vec3 UNIT_Y = new Vec3(0.0, 1.0, 0.0);
    public static final Vec3 UNIT_Z = new Vec3(0.0, 0.0, 1.0);

    private final double x;
    private final double y;
    private final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }

    public float xf() { return (float) x; }
    public float yf() { return (float) y; }
    public float zf() { return (float) z; }

    public Vec3 add(Vec3 other) {
        return new Vec3(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    public Vec3 subtract(Vec3 other) {
        return new Vec3(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    public Vec3 multiply(double scalar) {
        return new Vec3(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    public Vec3 divide(double scalar) {
        if (Math.abs(scalar) < 1e-15) {
            throw new ArithmeticException("Division by zero in Vec3");
        }
        return new Vec3(this.x / scalar, this.y / scalar, this.z / scalar);
    }

    public double dot(Vec3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        return new Vec3(
            this.y * other.z - this.z * other.y,
            this.z * other.x - this.x * other.z,
            this.x * other.y - this.y * other.x
        );
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public Vec3 normalize() {
        double len = length();
        if (len < 1e-15) {
            return ZERO;
        }
        return new Vec3(x / len, y / len, z / len);
    }

    public double distanceTo(Vec3 other) {
        return subtract(other).length();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vec3 vec3 = (Vec3) o;
        return Double.compare(vec3.x, x) == 0 &&
               Double.compare(vec3.y, y) == 0 &&
               Double.compare(vec3.z, z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return String.format("Vec3(%.4f, %.4f, %.4f)", x, y, z);
    }
}
