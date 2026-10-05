package cosmic.graphics;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * Encapsulates a hardware-accelerated 3D volumetric noise texture.
 *
 * <p>Generates seamless, periodic multi-frequency 3D noise on the CPU at initialization
 * and uploads it to GPU VRAM as a {@code GL_TEXTURE_3D} with trilinear filtering ({@code GL_LINEAR})
 * and repeating wrap modes ({@code GL_REPEAT}).</p>
 *
 * <p>Packs 4 decorrelated volumetric channels into RGBA8:
 * <ul>
 *   <li><b>R:</b> Base 3D multi-octave FBM density structure.</li>
 *   <li><b>G:</b> Medium-frequency turbulent shearing noise.</li>
 *   <li><b>B:</b> High-frequency wispy filamentary detail.</li>
 *   <li><b>A:</b> Cellular clumping and hotspot variation.</li>
 * </ul>
 * This eliminates thousands of redundant procedural noise evaluations per pixel,
 * delivering orders-of-magnitude faster raymarching performance.</p>
 */
public class Texture3D extends GLResource {

    private final int size;

    /**
     * Constructs and initializes a seamless 3D noise texture with dimension size x size x size.
     *
     * @param size cube dimension (typically 32, 64, or 128)
     */
    public Texture3D(int size) {
        this.size = Math.max(8, size);
        this.id = glGenTextures();

        glBindTexture(GL_TEXTURE_3D, id);

        // Trilinear hardware interpolation across all 3 spatial axes
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        // Seamless toroidal wrap for coordinates in [0, 1]
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_REPEAT);

        // Generate and upload procedural 3D noise data
        ByteBuffer buffer = generateNoiseBuffer(this.size);
        glTexImage3D(GL_TEXTURE_3D, 0, GL_RGBA8, this.size, this.size, this.size, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        glBindTexture(GL_TEXTURE_3D, 0);
    }

    /**
     * Default constructor creating a 64x64x64 3D noise texture.
     */
    public Texture3D() {
        this(64);
    }

    /**
     * Binds this 3D texture to the specified texture unit.
     *
     * @param unit texture unit index (0, 1, 2, ...)
     */
    public void bindTexture(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_3D, id);
    }

    @Override
    public void bind() {
        glBindTexture(GL_TEXTURE_3D, id);
    }

    @Override
    public void unbind() {
        glBindTexture(GL_TEXTURE_3D, 0);
    }

    public int getSize() {
        return size;
    }

    @Override
    public void destroy() {
        if (id != 0) {
            glDeleteTextures(id);
            id = 0;
        }
    }

    // =========================================================================
    // CPU Seamless Periodic Noise Generation
    // =========================================================================

    private static ByteBuffer generateNoiseBuffer(int n) {
        int totalBytes = n * n * n * 4;
        ByteBuffer buffer = BufferUtils.createByteBuffer(totalBytes);

        // Permutation table for periodic noise
        int[] perm = createPermutationTable();

        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    double u = (double) x / n;
                    double v = (double) y / n;
                    double w = (double) z / n;

                    // Channel R: Base FBM (octaves 1, 2, 4)
                    double rVal = periodicFbm(u, v, w, 4, 2.0, 0.5, perm);
                    // Channel G: Turbulent noise (frequency shifted)
                    double gVal = periodicTurbulence(u + 0.31, v + 0.17, w + 0.83, 3, 2.0, 0.55, perm);
                    // Channel B: High frequency detail
                    double bVal = periodicFbm(u * 2.0 + 0.5, v * 2.0 + 0.5, w * 2.0 + 0.5, 3, 2.0, 0.5, perm);
                    // Channel A: Cellular / clumping mask
                    double aVal = Math.pow(periodicNoise(u * 3.0, v * 3.0, w * 3.0, perm) * 0.5 + 0.5, 1.5);

                    int rByte = clampToByte(rVal);
                    int gByte = clampToByte(gVal);
                    int bByte = clampToByte(bVal);
                    int aByte = clampToByte(aVal);

                    buffer.put((byte) rByte);
                    buffer.put((byte) gByte);
                    buffer.put((byte) bByte);
                    buffer.put((byte) aByte);
                }
            }
        }

        buffer.flip();
        return buffer;
    }

    private static int clampToByte(double v) {
        int val = (int) Math.round(v * 255.0);
        return Math.max(0, Math.min(255, val));
    }

    private static int[] createPermutationTable() {
        int[] p = new int[512];
        int[] base = new int[256];
        for (int i = 0; i < 256; i++) {
            base[i] = i;
        }
        // Deterministic shuffle
        long seed = 133742069L;
        for (int i = 255; i > 0; i--) {
            seed = (seed * 6364136223846793005L + 1442695040888963407L);
            int j = (int) Math.floorMod(seed, i + 1);
            int tmp = base[i];
            base[i] = base[j];
            base[j] = tmp;
        }
        for (int i = 0; i < 512; i++) {
            p[i] = base[i & 255];
        }
        return p;
    }

    private static double periodicNoise(double x, double y, double z, int[] perm) {
        int xi = ((int) Math.floor(x)) & 255;
        int yi = ((int) Math.floor(y)) & 255;
        int zi = ((int) Math.floor(z)) & 255;

        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double zf = z - Math.floor(z);

        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);

        int aaa = perm[perm[perm[xi] + yi] + zi];
        int aba = perm[perm[perm[xi] + inc(yi)] + zi];
        int aab = perm[perm[perm[xi] + yi] + inc(zi)];
        int abb = perm[perm[perm[xi] + inc(yi)] + inc(zi)];
        int baa = perm[perm[perm[inc(xi)] + yi] + zi];
        int bba = perm[perm[perm[inc(xi)] + inc(yi)] + zi];
        int bab = perm[perm[perm[inc(xi)] + yi] + inc(zi)];
        int bbb = perm[perm[perm[inc(xi)] + inc(yi)] + inc(zi)];

        double x1 = lerp(grad(aaa, xf, yf, zf), grad(baa, xf - 1, yf, zf), u);
        double x2 = lerp(grad(aba, xf, yf - 1, zf), grad(bba, xf - 1, yf - 1, zf), u);
        double y1 = lerp(x1, x2, v);

        double x3 = lerp(grad(aab, xf, yf, zf - 1), grad(bab, xf - 1, yf, zf - 1), u);
        double x4 = lerp(grad(abb, xf, yf - 1, zf - 1), grad(bbb, xf - 1, yf - 1, zf - 1), u);
        double y2 = lerp(x3, x4, v);

        return lerp(y1, y2, w);
    }

    private static double periodicFbm(double x, double y, double z, int octaves, double lacunarity, double gain, int[] perm) {
        double sum = 0.0;
        double amp = 0.5;
        double freq = 1.0;
        double maxAmp = 0.0;

        for (int i = 0; i < octaves; i++) {
            sum += amp * (periodicNoise(x * freq, y * freq, z * freq, perm) * 0.5 + 0.5);
            maxAmp += amp;
            freq *= lacunarity;
            amp *= gain;
        }
        return sum / maxAmp;
    }

    private static double periodicTurbulence(double x, double y, double z, int octaves, double lacunarity, double gain, int[] perm) {
        double sum = 0.0;
        double amp = 0.5;
        double freq = 1.0;
        double maxAmp = 0.0;

        for (int i = 0; i < octaves; i++) {
            double n = Math.abs(periodicNoise(x * freq, y * freq, z * freq, perm));
            sum += amp * n;
            maxAmp += amp;
            freq *= lacunarity;
            amp *= gain;
        }
        return sum / maxAmp;
    }

    private static int inc(int num) {
        return (num + 1) & 255;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
