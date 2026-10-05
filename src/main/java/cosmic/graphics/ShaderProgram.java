package cosmic.graphics;

import cosmic.math.Vec3;
import cosmic.util.ResourceLoader;
import cosmic.util.ShaderCompilationException;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

/**
 * Encapsulates an OpenGL GLSL shader program (Vertex + Fragment shaders).
 *
 * <p>Extends {@link GLResource} to manage GPU program lifecycle.
 * Provides fast uniform caching and diagnostics via {@link ShaderCompilationException}.</p>
 */
public class ShaderProgram extends GLResource {

    private final String name;
    private final Map<String, Integer> uniformLocations;

    /**
     * Creates and compiles a shader program from vertex and fragment source code strings.
     *
     * @param name descriptive name for debugging
     * @param vertexSource vertex shader GLSL source
     * @param fragmentSource fragment shader GLSL source
     */
    public ShaderProgram(String name, String vertexSource, String fragmentSource) {
        this.name = name;
        this.uniformLocations = new HashMap<>();
        this.id = glCreateProgram();

        if (this.id == 0) {
            throw new ShaderCompilationException(name, "Failed to create OpenGL shader program handle");
        }

        int vertShader = compileShader(GL_VERTEX_SHADER, vertexSource, name + ".vert");
        int fragShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource, name + ".frag");

        glAttachShader(this.id, vertShader);
        glAttachShader(this.id, fragShader);
        glLinkProgram(this.id);

        checkLinkStatus(this.id, name);

        // Flag individual shaders for deletion once program is detached
        glDeleteShader(vertShader);
        glDeleteShader(fragShader);
    }

    /**
     * Factory method: loads shader sources from the classpath and creates the program.
     */
    public static ShaderProgram fromClasspath(String name, String vertResourcePath, String fragResourcePath) {
        String vertSource = ResourceLoader.loadShaderWithIncludes(vertResourcePath);
        String fragSource = ResourceLoader.loadShaderWithIncludes(fragResourcePath);
        return new ShaderProgram(name, vertSource, fragSource);
    }

    private static int compileShader(int shaderType, String source, String debugName) {
        int shaderId = glCreateShader(shaderType);
        if (shaderId == 0) {
            throw new ShaderCompilationException(debugName, "Failed to allocate shader object");
        }

        glShaderSource(shaderId, source);
        glCompileShader(shaderId);

        int status = glGetShaderi(shaderId, GL_COMPILE_STATUS);
        if (status == GL_FALSE) {
            String log = glGetShaderInfoLog(shaderId, 4096);
            glDeleteShader(shaderId);
            throw new ShaderCompilationException(debugName, log);
        }

        return shaderId;
    }

    private static void checkLinkStatus(int programId, String debugName) {
        int status = glGetProgrami(programId, GL_LINK_STATUS);
        if (status == GL_FALSE) {
            String log = glGetProgramInfoLog(programId, 4096);
            throw new ShaderCompilationException(debugName, "Program Link Error: " + log);
        }
    }

    public int getUniformLocation(String uniformName) {
        if (uniformLocations.containsKey(uniformName)) {
            return uniformLocations.get(uniformName);
        }
        int loc = glGetUniformLocation(id, uniformName);
        uniformLocations.put(uniformName, loc);
        return loc;
    }

    public void setUniform1i(String name, int value) {
        int loc = getUniformLocation(name);
        if (loc != -1) {
            glUniform1i(loc, value);
        }
    }

    public void setUniform1f(String name, float value) {
        int loc = getUniformLocation(name);
        if (loc != -1) {
            glUniform1f(loc, value);
        }
    }

    public void setUniform2f(String name, float x, float y) {
        int loc = getUniformLocation(name);
        if (loc != -1) {
            glUniform2f(loc, x, y);
        }
    }

    public void setUniform3f(String name, float x, float y, float z) {
        int loc = getUniformLocation(name);
        if (loc != -1) {
            glUniform3f(loc, x, y, z);
        }
    }

    public void setUniform3f(String name, Vec3 vec) {
        setUniform3f(name, vec.xf(), vec.yf(), vec.zf());
    }

    public void setUniform4f(String name, float x, float y, float z, float w) {
        int loc = getUniformLocation(name);
        if (loc != -1) {
            glUniform4f(loc, x, y, z, w);
        }
    }

    public void setUniformBoolean(String name, boolean value) {
        setUniform1i(name, value ? 1 : 0);
    }

    @Override
    public void bind() {
        glUseProgram(id);
    }

    @Override
    public void unbind() {
        glUseProgram(0);
    }

    @Override
    public void destroy() {
        if (id != 0) {
            glDeleteProgram(id);
            id = 0;
            uniformLocations.clear();
        }
    }

    public String getName() { return name; }
}
