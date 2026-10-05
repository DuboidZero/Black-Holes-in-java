// =========================================================================
// Volumetric Radiative Transfer, Forward Scattering & Inner-Disk Illumination
// =========================================================================

uniform float u_Absorption;        // Beer-Lambert extinction coefficient kappa_abs
uniform float u_EmissionStrength;  // Radiative emission flux multiplier
uniform float u_InnerIllumination; // Inner accretion-flow irradiation strength
uniform int u_LightSteps;          // Steps for secondary inner-disk illumination march

/**
 * Henyey-Greenstein phase function for forward-scattering smoke and plasma.
 */
float henyeyGreensteinPhase(float cosTheta, float g) {
    float g2 = g * g;
    float denom = 1.0 + g2 - 2.0 * g * cosTheta;
    return (1.0 - g2) / max(0.001, 4.0 * 3.14159265 * denom * sqrt(denom));
}

/**
 * Inner-disk illumination / irradiation proxy (self-shadowing).
 *
 * Traces a secondary inverse light ray from sample point p toward the
 * superheated inner accretion ring to estimate shadowing by intervening plasma.
 */
float computeInnerDiskTransmittance(vec3 p, int detailLevel) {
    if (u_LightSteps <= 0 || u_InnerIllumination <= 0.0) {
        return 1.0;
    }

    vec3 toCenter = vec3(-p.x, -p.y * 0.3, -p.z);
    float distToCenter = length(toCenter);
    if (distToCenter < 1e-4) return 0.0;
    vec3 lightDir = toCenter / distToCenter;

    float shadowStepSize = (distToCenter - u_DiskRIn * 0.95) / float(u_LightSteps + 1);
    shadowStepSize = clamp(shadowStepSize, 0.15, 1.4);

    float opticalDepth = 0.0;
    for (int i = 1; i <= 4; i++) {
        if (i > u_LightSteps) break;
        vec3 shadowPos = p + lightDir * (float(i) * shadowStepSize);
        float d = sampleBulkAccretionDensity(shadowPos);
        opticalDepth += d * shadowStepSize;
    }

    return exp(-opticalDepth * u_Absorption * 0.70);
}

/**
 * Executes a single radiative transfer step through the participating plasma medium
 * along the curved null-geodesic segment [pOld, pNew].
 */
void integrateVolumetricMediumStep(vec3 pOld, vec3 pNew, vec3 vRay, float deltaS, int detailLevel,
                                   inout vec3 accumColor, inout float transmittance,
                                   inout float accumOpticalDepth, inout float maxSampledDensity,
                                   inout float maxSampledTemp) {
    vec3 pMid = 0.5 * (pOld + pNew);

    float density = sampleAccretionDensity(pMid, detailLevel);
    if (density < 0.003) {
        return;
    }

    maxSampledDensity = max(maxSampledDensity, density);

    float r = length(pMid.xz);
    float baseTemp = computeThinDiskTemperature(r);

    // Relativistic Doppler boosting and gravitational redshift
    vec3 rayDirTowardsObs = normalize(pOld - pNew);
    float gFactor = 1.0;
    float beaming = 1.0;
    computeRelativisticShifts(pMid, rayDirTowardsObs, u_Rs, gFactor, beaming);

    maxSampledTemp = max(maxSampledTemp, baseTemp);

    // 1. Relativistic plasma emission from the temperature-driven palette
    vec3 localEmission = evaluatePlasmaSpectrum(baseTemp, beaming) * density * u_EmissionStrength;

    // 2. Inner-disk illumination proxy (self-shadowing + forward scattering)
    float innerTransmittance = computeInnerDiskTransmittance(pMid, detailLevel);
    vec3 lightDir = normalize(vec3(-pMid.x, -pMid.y * 0.3, -pMid.z));
    float cosScatter = dot(lightDir, rayDirTowardsObs);
    float phase = henyeyGreensteinPhase(cosScatter, 0.50);

    vec3 innerRadiance = vec3(3.4, 2.9, 2.2); // Warm-white inner illumination
    float thermalStrength = plasmaThermalStrength(baseTemp);
    vec3 inscatter = innerRadiance * innerTransmittance * phase * density
        * u_InnerIllumination * 0.40 * thermalStrength;

    // Total local source radiance
    vec3 sourceRadiance = localEmission + inscatter;

    // 3. Beer-Lambert extinction: dTau = rho * kappa_abs * deltaS
    float stepOpticalDepth = density * u_Absorption * deltaS;
    accumOpticalDepth += stepOpticalDepth;

    // Radiative accumulation with transmittance attenuation
    accumColor += transmittance * sourceRadiance * deltaS;
    transmittance *= exp(-stepOpticalDepth);
}
