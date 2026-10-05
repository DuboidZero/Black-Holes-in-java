package cosmic.util;

/**
 * Thrown when GLFW window or OpenGL context initialization fails.
 */
public class WindowInitializationException extends RuntimeException {

    public WindowInitializationException(String message) {
        super("Window initialization failed: " + message);
    }

    public WindowInitializationException(String message, Throwable cause) {
        super("Window initialization failed: " + message, cause);
    }
}
