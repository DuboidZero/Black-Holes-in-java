// =========================================================================
// Schwarzschild Spacetime & Inverse Ray Tracing Geodesic Integration
// =========================================================================

// Uniforms
uniform float u_Rs;          // Schwarzschild radius Rs = 2GM / c^2 (normalized, 2.0)
uniform float u_RPhoton;     // Photon sphere radius r_photon = 1.5 * Rs = 3.0
uniform float u_DiskRIn;     // Accretion disk inner boundary (ISCO = 3.0 * Rs = 6.0)
uniform float u_DiskROut;    // Accretion disk outer boundary (e.g. 26.0)
uniform float u_BaseStepSize;// Base integration step size (dλ)
uniform int u_MaxSteps;      // Maximum geodesic steps

// Constants
const float EPSILON_HORIZON = 0.005; // Horizon capture boundary (r <= Rs * 1.005)

/**
 * Calculates relativistic acceleration for null geodesics in Schwarzschild spacetime.
 *
 * Trajectory of light ray determined by distance from singularity (r).
 * The user-specified physical acceleration formula:
 * a_gravity = -(3GM / (c^2 * r^5)) * ((p x v) x p)
 *
 * In normalized simulation units:
 * Rs = 2GM / c^2 = 2.0  =>  3GM / c^2 = 1.5 * Rs = 3.0.
 *
 * Vector triple product identity: (p x v) x p = (p . p)*v - (p . v)*p = r^2 * v - (p . v) * p.
 */
vec3 computeGeodesicAcceleration(vec3 pos, vec3 vel) {
    float rSq = dot(pos, pos);
    float r = sqrt(rSq);
    if (r < u_Rs * 0.5) return vec3(0.0);

    vec3 h = cross(pos, vel);
    float hSq = dot(h, h);
    float r5 = rSq * rSq * r;

    // a_gravity = -1.5 * Rs / r^5 * |p x v|^2 * p
    // Directs gravitational acceleration radially inward towards black hole center (-pos).
    // Exact general relativistic null geodesic equation that creates the photon sphere
    // and bends light from the back of the accretion disk over the top of the black hole.
    vec3 aGravity = -1.5 * u_Rs * hSq * pos / max(r5, 1e-5);
    return aGravity;
}

/**
 * 4th-Order Runge-Kutta (RK4) numerical integrator for inverse ray tracing.
 * Advances ray state (position p, tangent velocity v) along curved spacetime by step dlambda.
 */
void rk4GeodesicStep(inout vec3 p, inout vec3 v, float dlambda) {
    vec3 a1 = computeGeodesicAcceleration(p, v);
    vec3 p1 = p + 0.5 * dlambda * v;
    vec3 v1 = v + 0.5 * dlambda * a1;

    vec3 a2 = computeGeodesicAcceleration(p1, v1);
    vec3 p2 = p + 0.5 * dlambda * v1;
    vec3 v2 = v + 0.5 * dlambda * a2;

    vec3 a3 = computeGeodesicAcceleration(p2, v2);
    vec3 p3 = p + dlambda * v2;
    vec3 v3 = v + dlambda * a3;

    vec3 a4 = computeGeodesicAcceleration(p3, v3);

    p += (dlambda / 6.0) * (v + 2.0 * v1 + 2.0 * v2 + v3);
    v += (dlambda / 6.0) * (a1 + 2.0 * a2 + 2.0 * a3 + a4);
}

/**
 * Spatial Acceleration: tests if an unbent initial camera ray intersects
 * the bounding sphere of the black hole and accretion disk system.
 */
bool rayIntersectsBoundingSphere(vec3 ro, vec3 rd, float radius, out float tNear, out float tFar) {
    float b = dot(ro, rd);
    float c = dot(ro, ro) - radius * radius;
    float d = b * b - c;
    if (d < 0.0) {
        tNear = -1.0;
        tFar = -1.0;
        return false;
    }
    float sqrtD = sqrt(d);
    tNear = -b - sqrtD;
    tFar = -b + sqrtD;
    return (tFar >= 0.0);
}
