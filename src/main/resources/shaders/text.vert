#version 330 core
layout(location = 0) in vec2 in_Pos;
layout(location = 1) in vec2 in_TexCoord;

uniform vec2 u_ScreenSize;

out vec2 v_TexCoord;

void main() {
    // Convert screen pixel coordinates (0,0 at top-left) to NDC [-1, 1]
    vec2 ndc = (in_Pos / u_ScreenSize) * 2.0 - 1.0;
    ndc.y = -ndc.y; // Invert Y so 0 is at top
    gl_Position = vec4(ndc, 0.0, 1.0);
    v_TexCoord = in_TexCoord;
}
