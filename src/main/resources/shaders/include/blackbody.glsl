// =========================================================================
// Relativistic Plasma Palette & Relativistic Doppler / Gravitational Shifts
// =========================================================================

uniform vec3 u_DiskColor0;
uniform vec3 u_DiskColor1;
uniform vec3 u_DiskColor2;
uniform vec3 u_DiskColor3;
uniform vec3 u_DiskColor4;

/**
 * Analytical Planckian blackbody spectrum approximation (Tanner Helland / CIE fit).
 */
vec3 blackbodyToRGB(float kelvin) {
    float temp = clamp(kelvin, 800.0, 95000.0) / 100.0;
    vec3 col;

    if (temp <= 66.0) {
        col.r = 1.0;
    } else {
        col.r = clamp(329.698727446 * pow(temp - 60.0, -0.1332047592) / 255.0, 0.0, 1.0);
    }

    if (temp <= 66.0) {
        col.g = clamp((99.4708025861 * log(temp) - 161.1195681661) / 255.0, 0.0, 1.0);
    } else {
        col.g = clamp(288.1221695283 * pow(temp - 60.0, -0.0755148492) / 255.0, 0.0, 1.0);
    }

    if (temp >= 66.0) {
        col.b = 1.0;
    } else if (temp <= 19.0) {
        col.b = 0.0;
    } else {
        col.b = clamp((138.5177312231 * log(temp - 10.0) - 305.0447927307) / 255.0, 0.0, 1.0);
    }

    return pow(col, vec3(2.2));
}

float plasmaThermalStrength(float intrinsicTemp) {
    float thermalPosition = clamp(intrinsicTemp / max(u_DiskTemp, 1.0), 0.0, 1.0);
    return smoothstep(0.02, 0.34, thermalPosition);
}

/**
 * Calculates relativistic frequency shift (g-factor) and relativistic flux beaming.
 *
 * Orbital velocity v_disk = beta * normalize(-z, 0, x) in Schwarzschild geometry.
 * Doppler factor: delta = sqrt(1 - beta^2) / (1 - beta * cosTheta)
 * Gravitational redshift: g_grav = sqrt(1 - Rs / r)
 * Total shift: g = g_grav * delta
 * Beaming: I_obs = g^3 * I_emit
 */
void computeRelativisticShifts(vec3 pos, vec3 rayDirTowardsObs, float rs,
                               out float outGFactor, out float outBeaming) {
    float r = length(pos.xz);
    if (r <= rs) {
        outGFactor = 0.0;
        outBeaming = 0.0;
        return;
    }

    // Keplerian orbital velocity beta = v / c = sqrt(Rs / (2r))
    float beta = sqrt(rs / (2.0 * r));
    beta = min(beta, 0.82);

    // Tangential orbital direction in disk plane (counterclockwise viewed from +Y)
    vec3 vDir = normalize(vec3(-pos.z, 0.0, pos.x));

    // Relativistic Doppler factor delta = sqrt(1 - beta^2) / (1 - beta * cosTheta)
    float gammaInv = sqrt(max(0.001, 1.0 - beta * beta));
    float cosTheta = dot(vDir, rayDirTowardsObs);
    float dopplerFactor = gammaInv / max(0.05, 1.0 - beta * cosTheta);

    // Gravitational redshift
    float gGrav = sqrt(max(0.01, 1.0 - rs / r));

    // Net shift
    outGFactor = gGrav * dopplerFactor;

    // Relativistic flux beaming (clamped for cinematic HDR balance)
    outBeaming = clamp(pow(outGFactor, 2.6), 0.06, 5.0);
}

/**
 * Intrinsic-temperature palette: layered orange, amber, gold, and warm-white.
 * Relativistic beaming changes brightness, not hue. Edit these stops to retune
 * the full temperature gradient.
 */
vec3 evaluatePlasmaSpectrum(float intrinsicTemp, float beaming) {
    // Thermal blackbody base
    vec3 bb = blackbodyToRGB(intrinsicTemp);

    float thermalPosition = clamp(intrinsicTemp / max(u_DiskTemp, 1.0), 0.0, 1.0);
    vec3 plasmaTone = mix(u_DiskColor0, u_DiskColor1, smoothstep(0.06, 0.27, thermalPosition));
    plasmaTone = mix(plasmaTone, u_DiskColor2, smoothstep(0.24, 0.49, thermalPosition));
    plasmaTone = mix(plasmaTone, u_DiskColor3, smoothstep(0.46, 0.73, thermalPosition));
    plasmaTone = mix(plasmaTone, u_DiskColor4, smoothstep(0.70, 0.97, thermalPosition));

    // Retain a subtle Planckian hue response without overpowering the palette.
    vec3 blackbodyTint = bb / max(max(bb.r, max(bb.g, bb.b)), 1e-3);
    plasmaTone *= mix(vec3(1.0), blackbodyTint, 0.10);
    return plasmaTone * beaming * plasmaThermalStrength(intrinsicTemp);
}
