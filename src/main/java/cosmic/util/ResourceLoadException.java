package cosmic.util;

/**
 * Thrown when a classpath resource (shader file, properties, etc.) cannot be loaded.
 */
public class ResourceLoadException extends RuntimeException {

    private final String resourcePath;

    public ResourceLoadException(String resourcePath, String message) {
        super("Failed to load resource '" + resourcePath + "': " + message);
        this.resourcePath = resourcePath;
    }

    public ResourceLoadException(String resourcePath, Throwable cause) {
        super("Failed to load resource '" + resourcePath + "'", cause);
        this.resourcePath = resourcePath;
    }

    public String getResourcePath() { return resourcePath; }
}
