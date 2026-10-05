package cosmic.graphics;

import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * High-performance hardware-accelerated text and HUD panel renderer.
 *
 * <p>Generates a high-resolution glyph texture atlas using offscreen Java2D
 * antialiasing with accurate font metrics and renders batched 2D screen-aligned
 * quads via modern OpenGL.</p>
 */
public class TextOverlay extends GLResource {

    private static final int ATLAS_SIZE = 1024;
    private static final int MAX_GLYPHS = 256;
    private static final int MAX_QUADS = 2048;
    private static final int FLOATS_PER_VERTEX = 4; // x, y, u, v
    private static final int FLOATS_PER_QUAD = 6 * FLOATS_PER_VERTEX; // 2 triangles

    private final ShaderProgram shader;
    private int fontTextureId;
    private int vboId;
    private FloatBuffer vertexBuffer;
    private int quadCount;

    // Font metrics and UV coordinates
    private int charW;
    private int charH;
    private final float[] glyphU0 = new float[MAX_GLYPHS];
    private final float[] glyphV0 = new float[MAX_GLYPHS];
    private final float[] glyphU1 = new float[MAX_GLYPHS];
    private final float[] glyphV1 = new float[MAX_GLYPHS];
    private final int[] charMap = new int[65536];

    public TextOverlay() {
        this.shader = ShaderProgram.fromClasspath("TextShader", "shaders/text.vert", "shaders/text.frag");
        initAtlas();
        initBuffers();
    }

    private void initAtlas() {
        BufferedImage img = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        Font font = new Font(Font.MONOSPACED, Font.BOLD, 32);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        charW = fm.charWidth('M');
        charH = fm.getHeight();
        int ascent = fm.getAscent();

        StringBuilder sb = new StringBuilder();
        for (char c = 32; c <= 126; c++) {
            sb.append(c);
        }
        sb.append("°²☉±×←→↑↓");
        String charset = sb.toString();

        Arrays.fill(charMap, -1);

        int pad = 2;
        int cellW = charW + pad * 2;
        int cellH = charH + pad * 2;
        int cols = ATLAS_SIZE / cellW;

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, ATLAS_SIZE, ATLAS_SIZE);
        g.setColor(Color.WHITE);

        for (int i = 0; i < charset.length(); i++) {
            char c = charset.charAt(i);
            charMap[c] = i;

            int col = i % cols;
            int row = i / cols;
            int cellX = col * cellW;
            int cellY = row * cellH;

            int drawX = cellX + pad;
            int drawY = cellY + pad + ascent;
            g.drawString(String.valueOf(c), drawX, drawY);

            glyphU0[i] = (float) drawX / ATLAS_SIZE;
            glyphV0[i] = (float) (cellY + pad) / ATLAS_SIZE;
            glyphU1[i] = (float) (drawX + charW) / ATLAS_SIZE;
            glyphV1[i] = (float) (cellY + pad + charH) / ATLAS_SIZE;
        }
        g.dispose();

        // Extract grayscale bytes
        byte[] pixels = (byte[]) img.getRaster().getDataElements(0, 0, ATLAS_SIZE, ATLAS_SIZE, null);
        ByteBuffer byteBuffer = MemoryUtil.memAlloc(pixels.length);
        byteBuffer.put(pixels).flip();

        this.fontTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, fontTextureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, ATLAS_SIZE, ATLAS_SIZE, 0, GL_RED, GL_UNSIGNED_BYTE, byteBuffer);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glBindTexture(GL_TEXTURE_2D, 0);
        MemoryUtil.memFree(byteBuffer);
    }

    private void initBuffers() {
        this.id = glGenVertexArrays();
        glBindVertexArray(this.id);

        this.vertexBuffer = MemoryUtil.memAllocFloat(MAX_QUADS * FLOATS_PER_QUAD);

        this.vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, (long) MAX_QUADS * FLOATS_PER_QUAD * Float.BYTES, GL_DYNAMIC_DRAW);

        // Attribute 0: vec2 in_Pos
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 0);

        // Attribute 1: vec2 in_TexCoord
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 2 * Float.BYTES);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void begin(int screenWidth, int screenHeight) {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);

        shader.bind();
        shader.setUniform2f("u_ScreenSize", (float) screenWidth, (float) screenHeight);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, fontTextureId);
        shader.setUniform1i("u_FontTexture", 0);

        glBindVertexArray(this.id);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        vertexBuffer.clear();
        quadCount = 0;
    }

    public void drawBox(float x, float y, float w, float h, float r, float g, float b, float a) {
        flush(); // Flush any pending glyphs

        shader.setUniform1i("u_IsSolid", 1);
        shader.setUniform4f("u_TextColor", r, g, b, a);

        addQuad(x, y, x + w, y + h, 0, 0, 1, 1);
        flush();

        shader.setUniform1i("u_IsSolid", 0);
    }

    /**
     * Renders a sleek sci-fi glassmorphism panel with glowing top accent and subtle borders.
     */
    public void drawPanel(float x, float y, float w, float h) {
        // Base dark translucent panel (glassmorphic obsidian slate)
        drawBox(x, y, w, h, 0.02f, 0.04f, 0.08f, 0.85f);
        // High-tech top accent line (radiant electric cyan)
        drawBox(x, y, w, 2.0f, 0.25f, 0.70f, 0.95f, 0.90f);
        // Subtle crisp border frame
        drawBox(x, y + 2.0f, 1.0f, h - 2.0f, 0.15f, 0.30f, 0.45f, 0.30f);
        drawBox(x + w - 1.0f, y + 2.0f, 1.0f, h - 2.0f, 0.15f, 0.30f, 0.45f, 0.30f);
        drawBox(x, y + h - 1.0f, w, 1.0f, 0.15f, 0.30f, 0.45f, 0.30f);
    }

    public void drawText(String text, float x, float y, float scale, float r, float g, float b, float a) {
        if (text == null || text.isEmpty()) return;

        shader.setUniform1i("u_IsSolid", 0);
        shader.setUniform4f("u_TextColor", r, g, b, a);

        float curX = x;
        float renderW = charW * scale;
        float renderH = charH * scale;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                curX = x;
                y += renderH * 1.25f;
                continue;
            }

            int idx = (c < charMap.length) ? charMap[c] : -1;
            if (idx >= 0) {
                if (quadCount >= MAX_QUADS) {
                    flush();
                }
                addQuad(curX, y, curX + renderW, y + renderH, glyphU0[idx], glyphV0[idx], glyphU1[idx], glyphV1[idx]);
            }
            curX += renderW;
        }

        flush();
    }

    public float getCharWidth(float scale) {
        return charW * scale;
    }

    public float getLineHeight(float scale) {
        return charH * scale;
    }

    private void addQuad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        // Triangle 1
        vertexBuffer.put(x0).put(y0).put(u0).put(v0);
        vertexBuffer.put(x1).put(y0).put(u1).put(v0);
        vertexBuffer.put(x0).put(y1).put(u0).put(v1);

        // Triangle 2
        vertexBuffer.put(x1).put(y0).put(u1).put(v0);
        vertexBuffer.put(x1).put(y1).put(u1).put(v1);
        vertexBuffer.put(x0).put(y1).put(u0).put(v1);

        quadCount++;
    }

    private void flush() {
        if (quadCount == 0) return;

        vertexBuffer.flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);
        glDrawArrays(GL_TRIANGLES, 0, quadCount * 6);
        vertexBuffer.clear();
        quadCount = 0;
    }

    public void end() {
        flush();
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        glBindTexture(GL_TEXTURE_2D, 0);
        shader.unbind();
        glDisable(GL_BLEND);
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
    public void destroy() {
        if (vertexBuffer != null) {
            MemoryUtil.memFree(vertexBuffer);
            vertexBuffer = null;
        }
        if (fontTextureId != 0) {
            glDeleteTextures(fontTextureId);
            fontTextureId = 0;
        }
        if (vboId != 0) {
            glDeleteBuffers(vboId);
            vboId = 0;
        }
        if (id != 0) {
            glDeleteVertexArrays(id);
            id = 0;
        }
        shader.destroy();
    }
}
