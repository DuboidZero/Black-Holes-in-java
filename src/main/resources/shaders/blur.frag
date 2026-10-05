#version 330 core

in vec2 v_UV;
out vec4 fragColor;

uniform sampler2D u_Texture;
uniform vec2 u_Direction; // (1.0/width, 0.0) or (0.0, 1.0/height)

// 9-tap Gaussian kernel weights
const float weights[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

void main() {
    vec3 result = texture(u_Texture, v_UV).rgb * weights[0];

    for (int i = 1; i < 5; i++) {
        vec2 offset = u_Direction * float(i) * 1.5;
        result += texture(u_Texture, v_UV + offset).rgb * weights[i];
        result += texture(u_Texture, v_UV - offset).rgb * weights[i];
    }

    fragColor = vec4(result, 1.0);
}
