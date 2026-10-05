package cosmic.math;

/**
 * Mathematical utilities and constants for numerical integration,
 * geometric transformations, and astrophysical approximations.
 */
public final class MathUtil {

    private MathUtil() {
        // Utility class: prevent instantiation
    }

    /** Gravitational constant G in SI units (m^3 kg^-1 s^-2) */
    public static final double G_SI = 6.67430e-11;

    /** Speed of light in vacuum c in SI units (m s^-1) */
    public static final double C_SI = 299792458.0;

    /** Solar mass in kilograms */
    public static final double SOLAR_MASS_KG = 1.98847e30;

    /**
     * Clamps a value to the specified range [min, max].
     */
    public static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    /**
     * Clamps a float value to [min, max].
     */
    public static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    /**
     * Linear interpolation between a and b by factor t.
     */
    public static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    /**
     * Smoothstep interpolation between edge0 and edge1.
     */
    public static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Converts degrees to radians.
     */
    public static double toRadians(double degrees) {
        return degrees * (Math.PI / 180.0);
    }

    /**
     * Converts radians to degrees.
     */
    public static double toDegrees(double radians) {
        return radians * (180.0 / Math.PI);
    }
}
