package cosmic.graphics;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Encapsulates an OpenGL Framebuffer Object (FBO) with attached HDR/RGBA color texture.
 *
 * <p>Extends {@link GLResource} for lifecycle control. Used for multi-pass rendering
 * (initial scene ray tracing, bloom extraction, blur passes, composite).</p>
 */
public class Framebuffer extends GLResource {

    private int colorTextureId;
    private int width;
    private int height;
    private final boolean hdr;

    public Framebuffer(int width, int height, boolean hdr) {
        this.width = width;
        this.height = height;
        this.hdr = hdr;
        init();
    }

    public Framebuffer(int width, int height) {
        this(width, height, true);
    }

    private void init() {
        // Create FBO
        this.id = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, this.id);

        // Create color texture attachment
        this.colorTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, this.colorTextureId);

        int internalFormat = hdr ? GL_RGBA16F : GL_RGBA8;
        int type = hdr ? GL_FLOAT : GL_UNSIGNED_BYTE;

        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, GL_RGBA, type, 0);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        // Attach texture to framebuffer
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.colorTextureId, 0);

        // Verify FBO completeness
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer creation incomplete! Status code: 0x" + Integer.toHexString(status));
        }

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth == width && newHeight == height) return;
        destroy();
        this.width = Math.max(1, newWidth);
        this.height = Math.max(1, newHeight);
        init();
    }

    @Override
    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, id);
        glViewport(0, 0, width, height);
    }

    @Override
    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Binds the color texture attached to this framebuffer to the specified texture unit.
     *
     * @param unit active texture unit offset (e.g. 0 for GL_TEXTURE0)
     */
    public void bindTexture(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, colorTextureId);
    }

    @Override
    public void destroy() {
        if (colorTextureId != 0) {
            glDeleteTextures(colorTextureId);
            colorTextureId = 0;
        }
        if (id != 0) {
            glDeleteFramebuffers(id);
            id = 0;
        }
    }

    public int getColorTextureId() { return colorTextureId; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
