package cosmic.graphics;

/**
 * Abstract base class for all OpenGL GPU resources (shaders, framebuffers, VAOs, VBOs).
 *
 * <p>Demonstrates abstract classes, resource encapsulation, the {@link AutoCloseable}
 * contract, and defensive destruction checks to prevent native GPU memory leaks.</p>
 */
public abstract class GLResource implements AutoCloseable {

    protected int id;
    protected boolean disposed;

    public GLResource() {
        this.id = 0;
        this.disposed = false;
    }

    /**
     * @return the OpenGL handle / ID for this resource
     */
    public int getId() {
        return id;
    }

    /**
     * @return true if this OpenGL resource has already been destroyed
     */
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Binds this resource to the active OpenGL state.
     */
    public abstract void bind();

    /**
     * Unbinds this resource from the active OpenGL state.
     */
    public abstract void unbind();

    /**
     * Frees underlying native OpenGL memory and invalidates this resource handle.
     */
    public abstract void destroy();

    @Override
    public void close() {
        if (!disposed) {
            destroy();
            disposed = true;
        }
    }
}
