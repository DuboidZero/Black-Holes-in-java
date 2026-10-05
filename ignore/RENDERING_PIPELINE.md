# How the Black Hole Renderer Works

This guide describes the implementation that currently produces the image. It follows the live Java/OpenGL path and GLSL code, and distinguishes that code from older comments and model helpers where they disagree.

## The short mental model

The black hole and disk are not polygon meshes. The GPU draws one oversized triangle covering the window. For every pixel, the fragment shader launches a ray from the camera, bends it numerically, samples a procedural 3D plasma volume along its path, and accumulates emitted light and absorption. A ray crossing the horizon returns no background light; an escaping ray sees the starfield in its bent direction. The HDR result is bloomed and tone-mapped.

The central shader is [blackhole.frag](src/main/resources/shaders/blackhole.frag). Its modules are [schwarzschild.glsl](src/main/resources/shaders/include/schwarzschild.glsl), [accretion.glsl](src/main/resources/shaders/include/accretion.glsl), [noise.glsl](src/main/resources/shaders/include/noise.glsl), [blackbody.glsl](src/main/resources/shaders/include/blackbody.glsl), and [radiative_transfer.glsl](src/main/resources/shaders/include/radiative_transfer.glsl). Java initializes the window and models, supplies uniforms, and runs post-processing in [Renderer.java](src/main/java/cosmic/graphics/Renderer.java).

## 1. Startup, units, and defaults

[BlackHoleApp.java](src/main/java/cosmic/app/BlackHoleApp.java) loads [blackhole.properties](src/main/resources/blackhole.properties), creates the GLFW window and OpenGL context, constructs the camera and models, then loops over input, rendering, buffer swapping, and event polling. The window requests OpenGL 3.3 Core; the 4.6 in the title is not the context version requested.

The shader uses geometric coordinates with normalized mass M=1, so its normalized Schwarzschild radius is Rs=2. Radii passed to the shader are in those same coordinate units:

| Feature | Shader coordinate | In units of Rs |
| --- | ---: | ---: |
| Event horizon | 2 | 1 |
| Photon sphere | 3 | 1.5 |
| Classical Schwarzschild ISCO | 6 | 3 |
| Configured disk inner edge | 5.2 | 2.6 |
| Configured disk outer edge | 22 | 11 |

The configured inner edge is intentionally 5.2, not the classical ISCO at 6. The shader uses those values directly. There is a labeling mismatch in comments/HUD: camera distance 95 means 95 shader coordinate units, or 47.5 Rs with Rs=2, although the HUD labels it 95 Rs.

Mass defaults to 10 solar masses. Java calculates a physical Schwarzschild radius (about 29.5 km) for display, but the renderer sends normalized Rs=2 to the shader. Changing mass therefore changes the physical readout, not the normalized image scale or ray paths.

Current values loaded from the properties file:

| Parameter | Value |
| --- | ---: |
| Camera distance / roll | 95 coordinate units / -34 degrees |
| Initial azimuth / elevation / FOV | 25 degrees / 18 degrees / 50 degrees |
| Disk inner / outer radius | 5.2 / 22 coordinate units |
| Reference temperature | 50,000 K |
| Base thickness | 0.62 coordinate units |
| Density / absorption | 1.15 / 0.85 |
| Turbulence scale / strength | 1.00 / 0.75 |
| Rotation speed / spiral strength | 1.20 / 2.80 |
| Emission / inner illumination | 1.20 / 0.90 |
| Palette / quality / exposure / bloom | Gargantua Amber / Medium / 1.15 / on |
| Window size | 1280 x 720 |

The camera starts aimed at the origin. Mouse drag and A/D orbit it horizontally; arrow keys change elevation; Q/E roll the view; scroll and W/S zoom. The camera basis and framebuffer resolution are sent to the shader every frame.

## 2. One ray per pixel

The vertex shader only places a fullscreen triangle over the viewport. For each fragment, blackhole.frag converts the pixel coordinate into a view-plane offset:

    uv = (pixel - 0.5 * resolution) / resolution.y

Dividing both axes by height preserves aspect ratio. With tanHalfFov = tan(FOV/2), the initial direction is:

    rayDir = normalize(forward
                       + uv.x * tanHalfFov * right
                       + uv.y * tanHalfFov * up)

The ray begins at the camera position. Its state is 3D position p and tangent/direction v. This is inverse ray tracing: start at the observer and trace backward, rather than launching photons from the disk toward the camera.

## 3. How gravity bends the ray

At each integration point, the shader computes:

    h = p x v
    r = length(p)
    a = -1.5 * Rs * dot(h,h) * p / r^5

h is the ray's angular-momentum-like vector. The acceleration points toward the origin; its magnitude depends on angular momentum and falls rapidly with radius. With Rs=2, the coefficient 1.5*Rs is 3. The second-order equation is represented as a first-order state system:

    dp/dlambda = v
    dv/dlambda = a(p,v)

Each step uses classical fourth-order Runge-Kutta (RK4): it evaluates the derivative at four intermediate states, k1 through k4, then combines them with weights 1, 2, 2, 1 divided by 6. This is more accurate per step than Euler integration, especially where the ray curves sharply.

The step length is adaptive, but is a hand-designed heuristic, not an error-estimating ODE solver:

    distanceFromHorizon = max(0.015, r - Rs)
    nearScale = clamp(distanceFromHorizon / (2*Rs), 0.05, 1)
    farScale = max(1, r / (3.5*Rs))
    diskScale = 0.42 if inside disk envelope, otherwise 1
    deltaLambda = clamp(baseStep * nearScale * farScale * diskScale, 0.02, 3.8)

Steps shrink near the horizon, grow in distant space, and shrink inside the disk envelope. Presets set the base step and maximum loop count:

| Preset | Max geodesic steps per pixel | Base step | Secondary light samples | Noise octaves |
| --- | ---: | ---: | ---: | ---: |
| Low | 96 | 0.32 | 2 | 1 |
| Medium | 160 | 0.24 | 3 | 2 |
| High | 240 | 0.18 | 4 | 3 |

These are maximums; rays often stop earlier. A ray is captured at r <= Rs*1.005. It formally escapes when beyond max(110, 1.8*cameraDistance) and moving outward. It also stops at the preset step limit or when plasma transmittance falls below 0.005. There is no separate escaped flag: after the loop, any non-captured ray with enough transmittance is given the background. Thus a ray that exhausts its step budget before reaching the escape radius also samples the sky from its current direction.

## 4. The disk is a sampled 3D medium

The disk lies in the XZ plane; Y is vertical. Its cylindrical radius is rDisk=length(p.xz). There is no disk mesh. At selected ray segments the shader samples density at the segment midpoint.

### Shape and broad density envelope

The GPU flared half-height is:

    normR = max(1, rDisk/Rin)
    h(rDisk) = thickness * (1 + 0.35*sqrt(normR - 1))

The inexpensive envelope test admits radii from 0.94*Rin to 1.03*Rout and vertical positions within 2.2*h. Actual density uses the stricter radius interval [Rin,Rout] and rejects points with abs(y)/h > 2.

The smooth large-scale density fades are:

    verticalFade = exp(-3.8*yNorm^2)
    innerFade = smoothstep(Rin, 1.12*Rin, rDisk)
    outerFade = 1 - smoothstep(0.62*Rout, Rout, rDisk)
    radialFade = innerFade * outerFade

These make a tapered, vertically concentrated volume rather than a hard-edged sheet.

### Spiral structure and animated clouds

The shader forms spiral-following coordinates:

    phi = atan(z,x)
    rotationPhase = mod(rotationSpeed*time, 2*pi)
    thetaOrbit = phi - rotationPhase
    spiralAngle = thetaOrbit - spiralStrength*log(max(rDisk/Rin,1))
    flowPosition = rDisk*(cos(spiralAngle), sin(spiralAngle))

It maps that position and normalized height into 3D noise coordinates. Two smooth gradient-noise samples warp the coordinates; multi-octave billow noise makes cloud lobes and cavities; lower-frequency noise makes broad cloud masses. Noise detail selects one, two, or three billow octaves.

The 3D texture coordinates are mapped approximately as (0.5 + 0.5*flowX/Rout, 0.5 + 0.5*yNorm, 0.5 + 0.5*flowZ/Rout), so the lookup is in the disk's flow coordinates rather than screen space. The texture-sampling helper ignores its detail-level argument; quality changes billow octaves, not texture resolution or lookup.

The result is thresholded with smoothstep(0.65, 0.88, billow + 0.70*cloudMacro). A 64x64x64 periodic RGBA 3D texture, generated deterministically on the CPU and linearly sampled on the GPU, adds smaller variation. In the current density code, red modulates the main cloud density by 0.70+0.30*red; green contributes to subtle diffuse-gap fill, capped by a factor 0.08. Blue and alpha are generated but unused by this density function.

The final density is:

    rho = max(0, radialFade * verticalFade * cloudDensity * densityScale)

The expensive medium evaluation is gated by a broad envelope test at the old and new ray positions. It runs when either endpoint is in that envelope and samples only the segment midpoint. A very long step that enters and exits the envelope while both endpoints remain outside can therefore skip that disk crossing; finite ray steps also mean thin features are not guaranteed to be hit.

Although comments describe differential Keplerian rotation, the animated pattern currently uses one rotationSpeed*time phase at every radius. The spiral geometry changes with radius, but animation is coherent phase rotation, not radius-dependent Keplerian shear.

## 5. Temperature, color, and the bright side

At a sample, temperature depends on cylindrical disk radius only. It does not depend on camera angle. Between the configured disk edges, the shader calculates:

    q = Rin/rDisk
    Tinner = Tref*q^1.25*max(0, 1-sqrt(q))^0.25*2.2
    Touter = 2400*(1 - (rDisk-Rin)/(Rout-Rin))
    outerCooling = 1 - smoothstep(0.62*Rout, Rout, rDisk)
    T = max(Tinner,Touter)*outerCooling

Outside [Rin,Rout], temperature is zero. The outer cooling factor rolls both density and temperature toward the outer edge.

Color has two layers:

1. blackbodyToRGB(T) approximates blackbody color, clamping temperature to 800-95,000 K.
2. The selected PalettePreset supplies five HDR stops, interpolated by thermalPosition=clamp(T/Tref,0,1). The default Gargantua Amber palette preserves the original stops: outer orange (1.10,0.18,0.018), light orange (1.62,0.34,0.055), amber (2.30,0.64,0.12), pale gold (3.10,1.48,0.48), and hot white (3.80,3.40,2.85). Overlapping smoothstep ranges make transitions continuous. Normalized blackbody hue tints the authored palette by 10%; it does not replace it.

The other palettes are White Hot, Blue Plasma, Verdelite Green, and Violet Nebula. Each supplies both the five disk stops and four sky colors (base, dust band, dim stars, bright stars). Palette data lives in [PalettePreset.java](src/main/java/cosmic/graphics/PalettePreset.java); values reach the shader as uniforms, so switching does not recompile the shader. Press C to open the picker, use left/right to preview palettes live, and press Enter, C, or Escape to close. The optional colorPalette property chooses the startup palette; live picker changes are not automatically saved to the properties file.

Emission fades in with smoothstep(0.02,0.34,thermalPosition). The underlying temperature/color field is radial and stays put as the camera moves.

The shader separately estimates relativistic brightness. It uses tangential velocity direction normalize(-z,0,x) and:

    beta = min(sqrt(Rs/(2*rDisk)), 0.82)
    doppler = sqrt(1-beta^2) / max(0.05, 1-beta*dot(vDisk,rayToObserver))
    gravitationalFactor = sqrt(max(0.01, 1-Rs/rDisk))
    g = gravitationalFactor*doppler
    beaming = clamp(g^2.6, 0.06, 5)

This lets the approaching side brighten and the receding side dim as the camera orbits. In the current shader, g scales brightness through beaming; it is not used to shift temperature or palette hue per pixel. The code is not doing a full observed-spectrum transformation T_observed=g*T.

## 6. Emission, scattering, and absorption

For each ray segment through the envelope, the shader samples density and temperature at the midpoint. Densities below 0.003 are skipped. Segment length is deltaS=length(pNew-pOld).

Local emission is approximately:

    emission = plasmaColor(T,beaming) * density * emissionStrength

There is also lightweight inner-disk illumination/scattering. It points a straight secondary sample ray approximately toward the center, in direction (-x,-0.3*y,-z), takes up to the preset's 2-4 samples, and accumulates low-frequency bulk density. Secondary transmission is exp(-opticalDepth*absorption*0.70). This ray does not bend under gravity and does not sample detailed cloud noise. A Henyey-Greenstein phase function with g=0.50 weights forward scattering. The inner source color is (3.4,2.9,2.2); the contribution is scaled by thermal strength, density, and innerIllumination.

The primary ray uses discrete Beer-Lambert transfer:

    dTau = density * absorption * deltaS
    accumColor += transmittance * sourceRadiance * deltaS
    transmittance *= exp(-dTau)

Transmittance starts at 1. Dense or long paths attenuate more strongly; emission behind them contributes less. The final ray color sums all sampled contributions plus any background visible through remaining transmittance.

## 7. Background and explicit photon ring

The background is procedural, not a sky image. Its palette-provided base color is near black; its dust color is multiplied by a soft horizontal band proportional to exp(-dir.y^2*22). A deterministic hash of direction*200 places sparse point stars, whose colors interpolate between the palette's dim and bright star colors. Since the shader evaluates the sky using the final bent ray direction, stars are gravitationally lensed too.

After ray integration, the shader adds a deliberately thin ring based on the smallest radius visited by the ray. If the ray was not captured and closest approach is near the photon sphere:

    ringDistance = abs(minRadius - Rphoton)
    ringGlow = 1.5*exp(-28*ringDistance)
    ringColor = (4.2,3.7,3.1)*ringGlow*transmittance

This is an artistic enhancement keyed to closest approach. It is not a separately resolved luminous ring in the plasma, and its brightness is not calculated by integrating photon-orbit dwell time. Debug mode 6 bypasses it.

## 8. GPU passes and actual resolution

Renderer runs four stages:

1. **Scene raymarch:** blackhole.frag runs at the full framebuffer/window resolution. sceneFbo is full-size RGBA16F, preserving HDR values above 1.0. At 1280x720 this is 921,600 primary fragment rays per frame. The volume is not currently rendered at half resolution and upscaled.
2. **Bright pass:** If bloom is enabled and debug mode is 0, a half-width/half-height RGBA16F target extracts luminance above 0.85 with a soft knee of 0.3.
3. **Blur:** Horizontal then vertical 9-sample Gaussian blur runs on the half-resolution bloom texture. The reduced resolution saves post-processing work; it does not lower raymarch resolution.
4. **Composite:** The scene and 0.45*bloom are multiplied by exposure (currently 1.15), passed through the ACES approximation in composite.frag, and gamma encoded with exponent 1/2.2. The HUD is drawn afterward.

The window requests swap interval 0 (no VSync cap). Per-ray integration, 3D noise, and secondary samples are the expensive work; bloom is a separate lower-resolution cost.

## 9. Debug modes and controls

Tab cycles through:

| Mode | What the code displays |
| --- | --- |
| 0 | Final accumulated scene; bloom is allowed |
| 1 | Turbo color of clamp(maxDensity/(densityScale*1.2), 0, 1) |
| 2 | Turbo color of clamp(maxTemperature/(diskReferenceTemperature*1.8), 0, 1) |
| 3 | 1-transmittance, accumulated opacity/extinction |
| 4 | accumColor before final compositing |
| 5 | Transmittance as grayscale |
| 6 | Bent-ray starfield only; black for captured rays |
| 7 | Geodesic loop steps used, normalized by preset maximum |

The temperature view is a diagnostic colormap, not the final orange-white palette. It records the maximum temperature at samples whose density passed the 0.003 cutoff; it is not a view-angle temperature map. Mode 4 is labeled “Radiative Emission Only” in the HUD, but actual accumColor may also contain inner illumination/scattering, the photon ring, and escaped sky light.

Other keys: 1/2/3 select Low/Medium/High; C opens the palette picker; B toggles bloom; [ and ] change density by 0.15; - and + change exposure by 0.15; backtick toggles the HUD; R resets camera, models, and palette to Gargantua Amber; F12 captures a screenshot.

## 10. Physical scope and approximations

The renderer includes meaningful relativistic structure: Schwarzschild-scale radii, a numerically bent ray, horizon capture, photon-sphere scale, gravitational lensing of disk and sky, approximate gravitational/Doppler brightness factors, and a volume with emission and Beer-Lambert extinction. It is still a real-time visualization, not a full astrophysical simulation.

- The shader evolves a 3D spatial ray state with a reduced Schwarzschild-inspired acceleration law. It does not integrate all four spacetime coordinates, photon travel time, or a Kerr (spinning black hole) metric.
- The disk is procedural noise plus analytic envelopes and a stylized temperature curve; it has no hydrodynamics, magnetic-field simulation, or time-evolving fluid solver.
- The temperature palette is art-directed. Relativistic factors affect brightness but not emitted hue in the current code.
- Inner illumination is a short straight-line density march, not a second bent photon ray or complete scattering solution.
- The explicit white photon ring is an exponential glow based on closest ray radius.
- Finite ray steps and midpoint density sampling can miss small features or cause sampling artifacts. Quality presets change maximum steps, base step, secondary samples, and billow-noise octaves.

Some older comments and README text describe stronger physical behavior than the live shader implements; this guide follows the current code.

## 11. Similar-looking Java code that is not the live image path

The GLSL shader is the source of truth for rendered pixels:

- [RayIntegrator.java](src/main/java/cosmic/physics/RayIntegrator.java) and [Geodesic.java](src/main/java/cosmic/physics/Geodesic.java) implement CPU ray integration. The app constructs a RayIntegrator and passes it to Renderer, but pixels are integrated in GLSL; the CPU integrator is not called by the frame loop.
- [AccretionDisk.java](src/main/java/cosmic/model/AccretionDisk.java) has Java temperature and thickness helpers, but the shader does not call them. Its flaring coefficient is 0.45 and its temperature constants/exponents differ from the GPU formulas above.
- [CosmicColor.java](src/main/java/cosmic/util/CosmicColor.java) is another Java color utility. Rendered plasma uses blackbody.glsl.
- [PalettePreset.java](src/main/java/cosmic/graphics/PalettePreset.java) supplies the live disk and sky color uniforms and picker preview gradients.
- turbulenceStrength is loaded and sent as u_TurbulenceStrength, and declared in GLSL, but no shader expression uses it. Adjusting it currently does not change the disk.
- raySteps and rayStepSize are parsed into Config, but Renderer sends preset values directly. QualityPreset controls the shader loop.
- AccretionDisk.opacity and brightness, and BlackHole.position, are model fields not used by the shader's rendering equations.

A property or Java getter does not necessarily affect the image: to tune the rendered result, confirm that the value reaches and is actually read by the GLSL path.
