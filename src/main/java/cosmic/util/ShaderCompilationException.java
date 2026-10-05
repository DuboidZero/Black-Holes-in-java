package cosmic.util;

/**
 * Custom exception thrown when a GLSL shader fails to compile or link.
 *
 * <p>Carries the full GLSL info-log so the developer can diagnose
 * exactly which shader failed and the compiler error message.</p>
 */
public class ShaderCompilationException extends RuntimeException {

    private final String shaderName;
    private final String infoLog;

    public ShaderCompilationException(String shaderName, String infoLog) {
        super("Shader compilation failed [" + shaderName + "]: " + infoLog);
        this.shaderName = shaderName;
        this.infoLog = infoLog;
    }

    /** @return the name/path of the shader that failed */
    public String getShaderName() { return shaderName; }

    /** @return the full GLSL info-log returned by the driver */
    public String getInfoLog() { return infoLog; }
}
