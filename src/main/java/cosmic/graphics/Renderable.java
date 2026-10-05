package cosmic.graphics;

/**
 * Interface representing any visual element or pass that can be submitted
 * to the rendering pipeline.
 */
public interface Renderable {

    /**
     * Executes the rendering operation.
     */
    void render();
}
