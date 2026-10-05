package cosmic.app;

import cosmic.util.WindowInitializationException;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Manages the GLFW window lifecycle, input callbacks, and OpenGL context
 * creation.
 *
 * <p>
 * Demonstrates native bindings via LWJGL 3, memory stacks, callback management,
 * and robust exception handling via {@link WindowInitializationException}.
 * </p>
 */
public class Window implements AutoCloseable {

    private long windowHandle;
    private int width;
    private int height;
    private final String title;
    private ResizeListener resizeListener;

    @FunctionalInterface
    public interface ResizeListener {
        void onResize(int newWidth, int newHeight);
    }

    public Window(int width, int height, String title) {
        this.width = width;
        this.height = height;
        this.title = title;
        init();
    }

    private void init() {
        // Setup error callback to standard error
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW
        if (!glfwInit()) {
            throw new WindowInitializationException("Unable to initialize GLFW native library");
        }

        // Configure GLFW Window Hints
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        // Create the native window
        windowHandle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (windowHandle == NULL) {
            throw new WindowInitializationException("Failed to create GLFW window with OpenGL 3.3 Core profile");
        }

        // Window resize callback
        glfwSetFramebufferSizeCallback(windowHandle, (window, w, h) -> {
            this.width = w;
            this.height = h;
            if (resizeListener != null) {
                resizeListener.onResize(w, h);
            }
        });

        // Center the window on the primary monitor
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(windowHandle, pWidth, pHeight);

            long monitor = glfwGetPrimaryMonitor();
            if (monitor != NULL) {
                GLFWVidMode vidmode = glfwGetVideoMode(monitor);
                if (vidmode != null) {
                    glfwSetWindowPos(
                            windowHandle,
                            (vidmode.width() - pWidth.get(0)) / 2,
                            (vidmode.height() - pHeight.get(0)) / 2);
                }
            }
        }

        // Make OpenGL context current on the calling thread
        glfwMakeContextCurrent(windowHandle);

        // Enable v-sync
        glfwSwapInterval(0);

        // Critical LWJGL step: initialize OpenGL bindings for this context
        GL.createCapabilities();

        // Make the window visible
        glfwShowWindow(windowHandle);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(windowHandle);
    }

    public void setShouldClose(boolean shouldClose) {
        glfwSetWindowShouldClose(windowHandle, shouldClose);
    }

    public void swapBuffers() {
        glfwSwapBuffers(windowHandle);
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    public void setResizeListener(ResizeListener listener) {
        this.resizeListener = listener;
    }

    public long getHandle() {
        return windowHandle;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public void close() {
        if (windowHandle != NULL) {
            glfwFreeCallbacks(windowHandle);
            glfwDestroyWindow(windowHandle);
            windowHandle = NULL;
        }
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }
}
