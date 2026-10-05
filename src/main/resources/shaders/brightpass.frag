#version 330 core

in vec2 v_UV;
out vec4 fragColor;

uniform sampler2D u_SceneTexture;
uniform float u_Threshold; // typically 0.85

void main() {
    vec3 color = texture(u_SceneTexture, v_UV).rgb;
    // Perceptual luminance calculation (Rec. 709)
    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));

    // Smooth knee curve around threshold
    float knee = 0.3;
    float soft = luminance - u_Threshold + knee;
    soft = clamp(soft, 0.0, 2.0 * knee);
    soft = (soft * soft) / (4.0 * knee + 1e-4);

    float contribution = max(soft, luminance - u_Threshold);
    contribution = clamp(contribution / max(luminance, 1e-4), 0.0, 1.0);

    fragColor = vec4(color * contribution, 1.0);
}
