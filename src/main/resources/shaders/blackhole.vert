#version 330 core

layout(location = 0) in vec2 in_Position;

out vec2 v_UV;
out vec2 v_NDC;

void main() {
    v_NDC = in_Position;
    // Map [-1, 1] to [0, 1] UV coordinates
    v_UV = in_Position * 0.5 + 0.5;
    gl_Position = vec4(in_Position, 0.0, 1.0);
}
