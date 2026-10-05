package cosmic.graphics;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders a single large triangle that spans the entire normalized device coordinates (NDC)
 * screen [-1.0, 1.0] in a single draw call without rasterizer seam artifacts.
 *
 * <p>Vertex coordinates: (-1.0, -1.0), (3.0, -1.0), (-1.0, 3.0).
 * Demonstrates VAO/VBO management, Direct Buffers via LWJGL MemoryUtil,
 * and interface implementation of {@link Renderable}.</p>
 */
public class FullscreenTriangle extends GLResource implements Renderable {

    private int vboId;

    public FullscreenTriangle() {
        // Create VAO
        this.id = glGenVertexArrays();
        glBindVertexArray(this.id);

        // 3 2D vertices covering the entire [-1, 1] screen and beyond
        float[] vertices = new float[] {
            -1.0f, -1.0f,
             3.0f, -1.0f,
            -1.0f,  3.0f
        };

        FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.length);
        buffer.put(vertices).flip();

        this.vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

        // Attribute 0: vec2 in_Position
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        MemoryUtil.memFree(buffer);
    }

    @Override
    public void bind() {
        glBindVertexArray(id);
    }

    @Override
    public void unbind() {
        glBindVertexArray(0);
    }

    @Override
    public void render() {
        bind();
        glDrawArrays(GL_TRIANGLES, 0, 3);
        unbind();
    }

    @Override
    public void destroy() {
        if (vboId != 0) {
            glDeleteBuffers(vboId);
            vboId = 0;
        }
        if (id != 0) {
            glDeleteVertexArrays(id);
            id = 0;
        }
    }
}
