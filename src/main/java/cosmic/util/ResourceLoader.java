package cosmic.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Utility for loading classpath resources (GLSL shaders, property files, etc.).
 *
 * <p>Demonstrates proper Java I/O, try-with-resources, ClassLoader delegation,
 * and robust exception wrapping.</p>
 */
public final class ResourceLoader {

    private ResourceLoader() {
        // Utility class: prevent instantiation
    }

    /**
     * Loads a text resource from the classpath as a String.
     *
     * @param path path relative to classpath root (e.g., "shaders/blackhole.frag")
     * @return content of the resource
     * @throws ResourceLoadException if resource cannot be found or read
     */
    public static String loadAsString(String path) {
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        ClassLoader classLoader = ResourceLoader.class.getClassLoader();

        try (InputStream in = classLoader.getResourceAsStream(normalizedPath)) {
            if (in == null) {
                throw new ResourceLoadException(path, "Resource not found on classpath");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append("\n");
                }
                return builder.toString();
            }
        } catch (IOException e) {
            throw new ResourceLoadException(path, e);
        }
    }

    /**
     * Loads a GLSL shader resource from the classpath, recursively resolving any
     * {@code #include "path"} directives relative to the parent directory or classpath root.
     *
     * @param path resource path to main shader
     * @return fully assembled shader source
     */
    public static String loadShaderWithIncludes(String path) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        return resolveIncludes(path, visited);
    }

    private static String resolveIncludes(String path, java.util.Set<String> visited) {
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        if (visited.contains(normalizedPath)) {
            return "// Already included: " + normalizedPath + "\n";
        }
        visited.add(normalizedPath);

        String rawSource = loadAsString(normalizedPath);
        String parentDir = normalizedPath.contains("/") ? normalizedPath.substring(0, normalizedPath.lastIndexOf('/') + 1) : "";

        StringBuilder resolved = new StringBuilder();
        String[] lines = rawSource.split("\r?\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include ")) {
                int firstQuote = trimmed.indexOf('"');
                int lastQuote = trimmed.lastIndexOf('"');
                if (firstQuote != -1 && lastQuote > firstQuote) {
                    String includePath = trimmed.substring(firstQuote + 1, lastQuote);
                    String targetPath;
                    if (includePath.startsWith("/")) {
                        targetPath = includePath.substring(1);
                    } else if (includePath.startsWith("shaders/")) {
                        targetPath = includePath;
                    } else {
                        targetPath = parentDir + includePath;
                    }
                    resolved.append("// Begin include: ").append(targetPath).append("\n");
                    resolved.append(resolveIncludes(targetPath, visited)).append("\n");
                    resolved.append("// End include: ").append(targetPath).append("\n");
                    continue;
                }
            }
            resolved.append(line).append("\n");
        }

        return resolved.toString();
    }

    /**
     * Obtains an {@link InputStream} for a classpath resource.
     *
     * @param path resource path
     * @return InputStream to the resource
     * @throws ResourceLoadException if resource is not found
     */
    public static InputStream getResourceAsStream(String path) {
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        InputStream in = ResourceLoader.class.getClassLoader().getResourceAsStream(normalizedPath);
        if (in == null) {
            throw new ResourceLoadException(path, "Resource stream not found on classpath");
        }
        return in;
    }
}
