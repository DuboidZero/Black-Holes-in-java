#version 330 core

in vec2 v_UV;
in vec2 v_NDC;

out vec4 fragColor;

// Camera uniforms
uniform vec3 u_CameraPos;
uniform vec3 u_CameraForward;
uniform vec3 u_CameraRight;
uniform vec3 u_CameraUp;
uniform float u_Fov;
uniform vec2 u_Resolution;
uniform vec3 u_SkyBaseColor;
uniform vec3 u_SkyDustColor;
uniform vec3 u_StarColorDim;
uniform vec3 u_StarColorBright;

// Quality and Debug Uniforms
uniform int u_DebugMode;         // 0=Final, 1=Density, 2=Temp, 3=OpticalDepth, 4=Emission, 5=Absorption, 6=Lensing, 7=Geodesic
uniform int u_NoiseDetailLevel;  // 1=Low, 2=Medium, 3=High

// Modular GLSL Includes
#include "include/schwarzschild.glsl"
#include "include/noise.glsl"
#include "include/accretion.glsl"
#include "include/blackbody.glsl"
#include "include/radiative_transfer.glsl"

// =========================================================================
// Procedural Background Sky & Starfield
// =========================================================================
vec3 renderSky(vec3 dir) {
    dir = normalize(dir);

    vec3 col = u_SkyBaseColor;

    // Subtle galactic dust lane
    float lat = dir.y;
    float galacticBand = exp(-lat * lat * 22.0);
    vec3 nebColor = u_SkyDustColor * galacticBand;
    col += nebColor;

    // High-frequency pinpoint starfield
    vec3 p = dir * 200.0;
    vec3 ip = floor(p);
    float starRnd = hash33_stream(ip).x * 0.5 + 0.5;

    if (starRnd > 0.980) {
        vec3 fp = fract(p) - 0.5;
        float d = length(fp);
        float brightness = smoothstep(0.25, 0.0, d) * pow((starRnd - 0.980) / 0.020, 2.8);
        vec3 starCol = mix(u_StarColorDim, u_StarColorBright, hash33_stream(ip + 11.0).y * 0.5 + 0.5);
        col += starCol * brightness * 2.5;
    }

    return col;
}

// Colormap for false-color diagnostics (Turbo / Thermal)
vec3 turboColormap(float t) {
    t = clamp(t, 0.0, 1.0);
    vec3 c0 = vec3(0.05, 0.08, 0.20);
    vec3 c1 = vec3(0.12, 0.48, 0.95);
    vec3 c2 = vec3(0.95, 0.60, 0.12);
    vec3 c3 = vec3(1.0, 0.98, 0.92);

    if (t < 0.33) {
        return mix(c0, c1, t / 0.33);
    } else if (t < 0.66) {
        return mix(c1, c2, (t - 0.33) / 0.33);
    } else {
        return mix(c2, c3, (t - 0.66) / 0.34);
    }
}

// =========================================================================
// Main Traversal: Numerical Geodesics + Volumetric Radiative Transfer
// =========================================================================
void main() {
    // Screen coordinates normalized with correct aspect ratio
    vec2 uv = (gl_FragCoord.xy - 0.5 * u_Resolution) / u_Resolution.y;

    // Primary camera ray direction
    float tanHalfFov = tan(radians(u_Fov * 0.5));
    vec3 rayDir = normalize(u_CameraForward + uv.x * tanHalfFov * u_CameraRight + uv.y * tanHalfFov * u_CameraUp);
    vec3 rayPos = u_CameraPos;
    vec3 rayVel = rayDir; // Affine null-geodesic tangent vector dx/dλ

    vec3 accumColor = vec3(0.0);
    float transmittance = 1.0;
    float accumOpticalDepth = 0.0;
    float maxSampledDensity = 0.0;
    float maxSampledTemp = 0.0;
    bool horizonCaptured = false;
    int stepsTaken = 0;

    float minR = length(rayPos);
    float escapeRadius = max(110.0, length(u_CameraPos) * 1.8);

    // -------------------------------------------------------------
    // Numerical Schwarzschild Null-Geodesic Integration Loop
    // -------------------------------------------------------------
    for (int step = 0; step < u_MaxSteps; step++) {
        stepsTaken = step + 1;
        vec3 pOld = rayPos;

        float rCurrent = length(rayPos);
        minR = min(minR, rCurrent);
        // 1. Event Horizon Capture Condition (r <= Rs * (1 + epsilon))
        if (rCurrent <= u_Rs * (1.0 + EPSILON_HORIZON)) {
            horizonCaptured = true;
            transmittance = 0.0;
            break;
        }

        // 2. Deep Space Escape Condition (r >= escapeRadius and heading outward)
        if (rCurrent >= escapeRadius && dot(rayPos, rayVel) > 0.0) {
            break;
        }

        // 3. Adaptive Integration Step Size (dλ):
        float distToHorizon = max(0.015, rCurrent - u_Rs);
        float nearScale = clamp(distToHorizon / (u_Rs * 2.0), 0.05, 1.0);
        float farScale = max(1.0, rCurrent / (u_Rs * 3.5));

        bool inDiskEnvelope = isInsideAccretionEnvelope(rayPos);
        float diskScale = inDiskEnvelope ? 0.42 : 1.0;
        float dt = clamp(u_BaseStepSize * nearScale * farScale * diskScale, 0.02, 3.8);

        // Advance geodesic via 4th-Order Runge-Kutta
        rk4GeodesicStep(rayPos, rayVel, dt);

        vec3 pNew = rayPos;
        float deltaS = length(pNew - pOld);

        // 4. Volumetric Participating Medium Step Integration:
        if (u_DebugMode != 6) { // Mode 6 is pure lensing (naked black hole)
            if (isInsideAccretionEnvelope(pOld) || isInsideAccretionEnvelope(pNew)) {
                integrateVolumetricMediumStep(
                    pOld, pNew, rayVel, deltaS, u_NoiseDetailLevel,
                    accumColor, transmittance, accumOpticalDepth,
                    maxSampledDensity, maxSampledTemp
                );

                // Early exit: optically opaque medium
                if (transmittance < 0.005) {
                    transmittance = 0.0;
                    break;
                }
            }
        }
    }

    // 5. Razor-Sharp Photon Ring Enhancement
    // Outlines the critical orbit with a radiant white-cyan halo
    if (!horizonCaptured && minR < u_RPhoton * 1.25 && minR > u_Rs) {
        float ringDist = abs(minR - u_RPhoton);
        float photonRingGlow = exp(-ringDist * 28.0) * 1.5;
        vec3 ringCol = vec3(4.2, 3.7, 3.1) * photonRingGlow;
        accumColor += transmittance * ringCol;
    }

    // 6. Gravitationally Lensed Background Starfield (for escaped rays)
    if (!horizonCaptured && transmittance > 0.001) {
        vec3 skyColor = renderSky(rayVel);
        accumColor += transmittance * skyColor;
    }

    // -------------------------------------------------------------
    // Debug Mode Output Selection
    // -------------------------------------------------------------
    if (u_DebugMode == 0) {
        // D0: Final HDR Composite
        fragColor = vec4(accumColor, 1.0);
    } else if (u_DebugMode == 1) {
        // D1: Density Field Visualization
        float dNorm = clamp(maxSampledDensity / (u_DensityScale * 1.2), 0.0, 1.0);
        fragColor = vec4(turboColormap(dNorm), 1.0);
    } else if (u_DebugMode == 2) {
        // D2: Temperature Field
        float tNorm = clamp(maxSampledTemp / (u_DiskTemp * 1.8), 0.0, 1.0);
        fragColor = vec4(turboColormap(tNorm), 1.0);
    } else if (u_DebugMode == 3) {
        // D3: Optical Depth (1 - T)
        float opacity = clamp(1.0 - transmittance, 0.0, 1.0);
        fragColor = vec4(vec3(opacity), 1.0);
    } else if (u_DebugMode == 4) {
        // D4: Radiative Emission Only
        fragColor = vec4(accumColor, 1.0);
    } else if (u_DebugMode == 5) {
        // D5: Transmittance Only (Beer-Lambert attenuation map)
        fragColor = vec4(vec3(transmittance), 1.0);
    } else if (u_DebugMode == 6) {
        // D6: Pure Gravitational Lensing (Naked Black Hole + Lensed Sky)
        if (horizonCaptured) {
            fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        } else {
            fragColor = vec4(renderSky(rayVel), 1.0);
        }
    } else if (u_DebugMode == 7) {
        // D7: Geodesic Integration Diagnostics
        float stepNorm = float(stepsTaken) / float(u_MaxSteps);
        vec3 diagColor = turboColormap(stepNorm);
        if (horizonCaptured) {
            diagColor *= vec3(0.3, 0.05, 0.05);
        }
        fragColor = vec4(diagColor, 1.0);
    } else {
        fragColor = vec4(accumColor, 1.0);
    }
}
