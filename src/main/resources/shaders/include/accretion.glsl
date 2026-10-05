// =========================================================================
// 3D Volumetric Accretion Medium Geometry, Spin & Billowy Cloud Density
// =========================================================================

uniform float u_DiskThickness;     // Base vertical half-thickness h_0 in Rs units
uniform float u_DensityScale;      // Volumetric plasma density scale
uniform float u_RotationSpeed;     // Keplerian angular velocity multiplier
uniform float u_SpiralStrength;    // Spiral arm winding factor
uniform float u_DiskTemp;          // Reference peak temperature in Kelvin
uniform float u_Time;              // Elapsed time in seconds

const float OUTER_DISSIPATION_START = 0.62;

/**
 * Calculates the hydrostatic flared vertical half-height h(r) of the accretion disk.
 */
float computeFlaredHeight(float r) {
    float normR = max(1.0, r / u_DiskRIn);
    return u_DiskThickness * (1.0 + 0.35 * sqrt(normR - 1.0));
}

/**
 * Rapid conservative bounding envelope test.
 * Returns true if point p lies within the flared cylindrical slab of the accretion disk.
 */
bool isInsideAccretionEnvelope(vec3 p) {
    float r = length(p.xz);
    if (r < u_DiskRIn * 0.94 || r > u_DiskROut * 1.03) {
        return false;
    }
    float h = computeFlaredHeight(r) * 2.2;
    return abs(p.y) <= h;
}

/**
 * Evaluates the relativistic thin-disk temperature profile:
 * Blazing hot inner ISCO boundary -> cool outer clouds.
 */
float computeThinDiskTemperature(float r) {
    if (r <= u_DiskRIn || r >= u_DiskROut) return 0.0;
    float ratio = u_DiskRIn / r;
    float torqueFalloff = max(0.0, 1.0 - sqrt(ratio));
    float tInner = u_DiskTemp * pow(ratio, 1.25) * pow(torqueFalloff, 0.25) * 2.2;
    float tOuter = 2400.0 * (1.0 - (r - u_DiskRIn) / (u_DiskROut - u_DiskRIn));
    float outerCooling = 1.0 - smoothstep(u_DiskROut * OUTER_DISSIPATION_START, u_DiskROut, r);
    return max(tOuter, tInner) * outerCooling;
}

/**
 * Evaluates the local 3D plasma density rho(p, t) at coordinate point p.
 *
 * Implements:
 * 1. Differential Keplerian rotation omega(r) proportional to r^(-3/2).
 * 2. Concentric silky spiral filaments: sin(r * k_r - thetaOrbit * k_phi + warp).
 * 3. 3D billow noise and FBM smoke puffs with hollow cavities.
 * 4. Flared vertical Gaussian profile and ISCO plunge cutoff.
 */
float sampleAccretionDensity(vec3 p, int detailLevel) {
    float r = length(p.xz);
    if (r < u_DiskRIn || r > u_DiskROut) {
        return 0.0;
    }

    float h = computeFlaredHeight(r);
    float yNorm = abs(p.y) / max(0.01, h);
    if (yNorm > 2.0) return 0.0;

    // 1. Vertical Gaussian density stratification
    float verticalFade = exp(-3.8 * yNorm * yNorm);

    // 2. Radial boundary falloffs (steep plunge at ISCO, smooth dissipation at outer rim)
    float innerFade = smoothstep(u_DiskRIn, u_DiskRIn * 1.12, r);
    float outerFade = 1.0 - smoothstep(u_DiskROut * OUTER_DISSIPATION_START, u_DiskROut, r);
    float radialFade = innerFade * outerFade;

    // 3. Coherent cloud advection; avoid accumulating differential shear into rings.
    float phi = atan(p.z, p.x);
    float rotationPhase = mod(u_RotationSpeed * u_Time, 6.2831853);
    float thetaOrbit = phi - rotationPhase;

    float spiralAngle = thetaOrbit - u_SpiralStrength * log(max(r / u_DiskRIn, 1.0));
    vec2 flowPosition = r * vec2(cos(spiralAngle), sin(spiralAngle));

    // A seam-free Cartesian embedding carries the same turbulent spiral from
    // face-on to side views without relying on camera-facing filaments.
    vec3 warpCoord = vec3(flowPosition.x * 0.6, yNorm * 2.5, flowPosition.y * 0.6)
        * u_TurbulenceScale;
    float warp1 = gradientNoise3D(warpCoord);
    float warp2 = gradientNoise3D(warpCoord * 2.1 + vec3(1.7, 0.8, 2.3));

    // Billowy smoke puffs and turbulent cavities
    float billow = billowNoise3D(warpCoord * 1.4 + vec3(warp2 * 0.8, 0.0, warp1 * 0.5), detailLevel);

    // Soft, continuous cloud masses follow the winding flow without a filament mask.
    float cloudMacro = gradientNoise3D(warpCoord * 0.32 + vec3(4.7, 1.3, 8.1));
    float cloudDensity = smoothstep(0.65, 0.88, billow + 0.70 * cloudMacro);

    vec3 uvw = vec3(0.5 + 0.5 * flowPosition.x / u_DiskROut,
                    yNorm * 0.5 + 0.5,
                    0.5 + 0.5 * flowPosition.y / u_DiskROut);
    vec4 texNoise = sampleVolumetricNoise(uvw, detailLevel);
    cloudDensity *= 0.70 + 0.30 * texNoise.r;

    float diffuseGapCloud = smoothstep(0.20, 0.68, billow * 0.55 + texNoise.g * 0.45);
    cloudDensity += 0.08 * diffuseGapCloud * (1.0 - cloudDensity);

    float density = radialFade * verticalFade * cloudDensity;
    return max(0.0, density * u_DensityScale);
}

/**
 * Low-frequency density estimate for secondary self-shadowing rays.
 */
float sampleBulkAccretionDensity(vec3 p) {
    float r = length(p.xz);
    if (r < u_DiskRIn || r > u_DiskROut) return 0.0;

    float h = computeFlaredHeight(r);
    float yNorm = abs(p.y) / max(0.01, h);
    if (yNorm > 2.0) return 0.0;

    float verticalFade = exp(-3.8 * yNorm * yNorm);
    float innerFade = smoothstep(u_DiskRIn, u_DiskRIn * 1.12, r);
    float outerFade = 1.0 - smoothstep(u_DiskROut * OUTER_DISSIPATION_START, u_DiskROut, r);
    float radialFade = innerFade * outerFade;
    return radialFade * verticalFade * 0.60 * u_DensityScale;
}
