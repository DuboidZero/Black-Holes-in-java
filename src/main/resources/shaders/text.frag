#version 330 core
in vec2 v_TexCoord;

uniform sampler2D u_FontTexture;
uniform vec4 u_TextColor;
uniform int u_IsSolid; // If 1, render solid box without sampling font texture

out vec4 out_Color;

void main() {
    if (u_IsSolid == 1) {
        out_Color = u_TextColor;
    } else {
        float alpha = texture(u_FontTexture, v_TexCoord).r;
        if (alpha < 0.02) {
            discard;
        }
        out_Color = vec4(u_TextColor.rgb, u_TextColor.a * alpha);
    }
}
