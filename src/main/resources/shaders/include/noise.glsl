// =========================================================================
// 3D Procedural Gradient Noise & Volumetric Texture Sampling
// =========================================================================

uniform sampler3D u_NoiseTexture3D;
uniform float u_TurbulenceScale;
uniform float u_TurbulenceStrength;

// Fast deterministic 3D hash
vec3 hash33_stream(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)),
             dot(p, vec3(269.5, 183.3, 246.1)),
             dot(p, vec3(113.5, 271.9, 124.6)));
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

// 3D Gradient Noise with cubic hermite interpolation
float gradientNoise3D(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);

    return mix(mix(mix(dot(hash33_stream(i + vec3(0.0,0.0,0.0)), f - vec3(0.0,0.0,0.0)),
                       dot(hash33_stream(i + vec3(1.0,0.0,0.0)), f - vec3(1.0,0.0,0.0)), u.x),
                   mix(dot(hash33_stream(i + vec3(0.0,1.0,0.0)), f - vec3(0.0,1.0,0.0)),
                       dot(hash33_stream(i + vec3(1.0,1.0,0.0)), f - vec3(1.0,1.0,0.0)), u.x), u.y),
               mix(mix(dot(hash33_stream(i + vec3(0.0,0.0,1.0)), f - vec3(0.0,0.0,1.0)),
                       dot(hash33_stream(i + vec3(1.0,0.0,1.0)), f - vec3(1.0,0.0,1.0)), u.x),
                   mix(dot(hash33_stream(i + vec3(0.0,1.0,1.0)), f - vec3(0.0,1.0,1.0)),
                       dot(hash33_stream(i + vec3(1.0,1.0,1.0)), f - vec3(1.0,1.0,1.0)), u.x), u.y), u.z);
}

// Multi-octave FBM noise for volumetric cloud structures
float fbmCloud3D(vec3 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    float freq = 1.0;
    for (int i = 0; i < 3; i++) {
        if (i >= octaves) break;
        sum += amp * (gradientNoise3D(p * freq) * 0.5 + 0.5);
        freq *= 2.15;
        amp *= 0.48;
    }
    return sum;
}

// Billowy cloud noise: creates billows, sharp wisps, and smoky cavities
float billowNoise3D(vec3 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    float freq = 1.0;
    for (int i = 0; i < 3; i++) {
        if (i >= octaves) break;
        float n = abs(gradientNoise3D(p * freq));
        sum += amp * (1.0 - n);
        freq *= 2.08;
        amp *= 0.5;
    }
    return sum;
}

/**
 * Samples the 3D volumetric noise texture.
 */
vec4 sampleVolumetricNoise(vec3 uvw, int detailLevel) {
    return texture(u_NoiseTexture3D, uvw);
}
