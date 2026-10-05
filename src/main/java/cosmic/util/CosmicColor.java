package cosmic.util;

import cosmic.math.MathUtil;
import cosmic.math.Vec3;

/**
 * Utility for converting blackbody radiation temperatures (in Kelvin)
 * to physical RGB color representations based on Planckian locus approximations.
 *
 * <p>Target visual progression:
 * <ul>
 *   <li>T &lt; 2,000 K  : Deep red / infrared edge</li>
 *   <li>2,000 - 4,000 K : Amber / orange</li>
 *   <li>4,000 - 6,500 K : Warm yellow / neutral solar white</li>
 *   <li>6,500 - 10,000 K: Bright white / light cyan</li>
 *   <li>&gt; 12,000 K   : Intense blue-white plasma</li>
 * </ul>
 * </p>
 */
public final class CosmicColor {

    private CosmicColor() {
        // Utility class: prevent instantiation
    }

    /**
     * Converts a blackbody temperature in Kelvin to a normalized linear RGB {@link Vec3}.
     *
     * <p>Uses the Tanner Helland algorithm calibrated for CIE 1931 color space,
     * extended for extreme temperatures typical of accretion disk plasma (up to 100,000 K).</p>
     *
     * @param kelvin blackbody temperature in Kelvin (recommended 1,000 - 80,000 K)
     * @return RGB color vector with components normalized in range [0.0, 1.0]
     */
    public static Vec3 temperatureToRGB(double kelvin) {
        double temp = MathUtil.clamp(kelvin, 1000.0, 100000.0) / 100.0;

        double red;
        double green;
        double blue;

        // Calculate Red
        if (temp <= 66.0) {
            red = 255.0;
        } else {
            red = temp - 60.0;
            red = 329.698727446 * Math.pow(red, -0.1332047592);
            red = MathUtil.clamp(red, 0.0, 255.0);
        }

        // Calculate Green
        if (temp <= 66.0) {
            green = temp;
            green = 99.4708025861 * Math.log(green) - 161.1195681661;
            green = MathUtil.clamp(green, 0.0, 255.0);
        } else {
            green = temp - 60.0;
            green = 288.1221695283 * Math.pow(green, -0.0755148492);
            green = MathUtil.clamp(green, 0.0, 255.0);
        }

        // Calculate Blue
        if (temp >= 66.0) {
            blue = 255.0;
        } else if (temp <= 19.0) {
            blue = 0.0;
        } else {
            blue = temp - 10.0;
            blue = 138.5177312231 * Math.log(blue) - 305.0447927307;
            blue = MathUtil.clamp(blue, 0.0, 255.0);
        }

        // Normalize to [0.0, 1.0]
        return new Vec3(red / 255.0, green / 255.0, blue / 255.0);
    }

    /**
     * Converts a normalized RGB {@link Vec3} into an integer packed ARGB color (0xAARRGGBB).
     */
    public static int toPackedARGB(Vec3 rgb, double alpha) {
        int r = (int) (MathUtil.clamp(rgb.x(), 0.0, 1.0) * 255.0);
        int g = (int) (MathUtil.clamp(rgb.y(), 0.0, 1.0) * 255.0);
        int b = (int) (MathUtil.clamp(rgb.z(), 0.0, 1.0) * 255.0);
        int a = (int) (MathUtil.clamp(alpha, 0.0, 1.0) * 255.0);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
