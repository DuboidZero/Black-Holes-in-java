#version 330 core

in vec2 v_UV;
out vec4 fragColor;

uniform sampler2D u_SceneTexture;
uniform sampler2D u_BloomTexture;
uniform float u_Exposure;
uniform int u_BloomEnabled;
uniform float u_BloomIntensity;

// ACES Filmic Tone Mapping approximation
vec3 acesFilm(vec3 x) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 sceneColor = texture(u_SceneTexture, v_UV).rgb;

    if (u_BloomEnabled == 1) {
        vec3 bloomColor = texture(u_BloomTexture, v_UV).rgb;
        sceneColor += bloomColor * u_BloomIntensity;
    }

    // Apply exposure
    vec3 mapped = sceneColor * u_Exposure;

    // ACES filmic curve
    mapped = acesFilm(mapped);

    // Gamma correction to sRGB (gamma = 2.2)
    mapped = pow(mapped, vec3(1.0 / 2.2));

    fragColor = vec4(mapped, 1.0);
}
