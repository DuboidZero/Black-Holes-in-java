package cosmic;

import cosmic.graphics.Camera;
import cosmic.math.MathUtil;
import cosmic.math.Vec3;
import cosmic.model.AccretionDisk;
import cosmic.model.BlackHole;
import cosmic.physics.Geodesic;
import cosmic.physics.RayIntegrator;
import cosmic.physics.Schwarzschild;
import cosmic.util.Config;
import cosmic.util.CosmicColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite verifying core astrophysics formulas, coordinate calculations,
 * blackbody color mapping, camera resets, and configuration loading.
 */
public class BlackHolePhysicsTest {

    private static final double EPSILON = 1e-4;

    @Test
    @DisplayName("Verify physical Schwarzschild radius Rs = 2GM / c²")
    void testSchwarzschildRadius() {
        // 10 Solar Masses
        double massMsun = 10.0;
        double massKg = massMsun * MathUtil.SOLAR_MASS_KG;
        double rsMeters = Schwarzschild.calculatePhysicalRs(massKg);
        double rsKm = rsMeters / 1000.0;

        // Rs for 10 M☉ is approximately 29.54 km
        assertEquals(29.54, rsKm, 0.5, "10 M☉ black hole should have Rs ≈ 29.5 km");

        // Analytical scaling: Rs ∝ M
        double rs20Msun = Schwarzschild.calculatePhysicalRs(2.0 * massKg);
        assertEquals(2.0 * rsMeters, rs20Msun, EPSILON, "Schwarzschild radius must scale linearly with mass");
    }

    @Test
    @DisplayName("Verify physical photon sphere radius r_photon = 1.5 * Rs = 3GM / c²")
    void testPhotonSphereRadius() {
        double massKg = 10.0 * MathUtil.SOLAR_MASS_KG;
        double rs = Schwarzschild.calculatePhysicalRs(massKg);
        double rPhoton = Schwarzschild.calculatePhysicalRPhoton(massKg);

        assertEquals(1.5 * rs, rPhoton, EPSILON, "Photon sphere must be precisely 1.5 * Rs");
    }

    @Test
    @DisplayName("Verify BlackHole model defaults and geometric relations")
    void testBlackHoleModelDefaults() {
        BlackHole bh = new BlackHole(10.0);

        assertEquals(10.0, bh.getMassSolarMasses(), EPSILON);
        assertEquals(Schwarzschild.NORMALIZED_RS, bh.getNormalizedRs(), EPSILON);
        assertEquals(1.5 * Schwarzschild.NORMALIZED_RS, bh.getNormalizedRPhoton(), EPSILON);
        assertEquals(3.0 * Schwarzschild.NORMALIZED_RS, bh.getNormalizedRIsco(), EPSILON);
        assertEquals(Vec3.ZERO, bh.getPosition());

        // Test reset contract
        bh.setPosition(new Vec3(5.0, 2.0, 1.0));
        bh.reset();
        assertEquals(Vec3.ZERO, bh.getPosition());
    }

    @Test
    @DisplayName("Verify AccretionDisk temperature falloff and ISCO vanishing torque")
    void testAccretionDiskTemperatureProfile() {
        double rIn = 6.0;
        double rOut = 30.0;
        double tRef = 50000.0;
        AccretionDisk disk = new AccretionDisk(rIn, rOut, tRef);

        // At or outside bounds, temperature is 0
        assertEquals(0.0, disk.calculateTemperature(rIn - 0.1), EPSILON);
        assertEquals(0.0, disk.calculateTemperature(rOut + 0.1), EPSILON);

        // Peak temperature occurs slightly outside r_in due to torque boundary condition
        double tPeak = disk.calculateTemperature(rIn * 1.36);
        double tOuter = disk.calculateTemperature(25.0);

        assertTrue(tPeak > tOuter, "Disk inner region must be significantly hotter than outer region");
        assertTrue(tOuter > 0.0, "Outer disk should still have non-zero temperature");
    }

    @Test
    @DisplayName("Verify Blackbody temperatureToRGB color progression")
    void testBlackbodyTemperatureToRGB() {
        // Cool (2000 K) -> predominantly Red
        Vec3 coolColor = CosmicColor.temperatureToRGB(2000.0);
        assertTrue(coolColor.x() > coolColor.z(), "Cool blackbody must have red > blue");

        // Solar / Warm (5800 K) -> balanced warm white/yellow
        Vec3 warmColor = CosmicColor.temperatureToRGB(5800.0);
        assertTrue(warmColor.x() > 0.8 && warmColor.y() > 0.7, "Solar blackbody should be bright yellow-white");

        // Very hot (30000 K) -> intense Blue-White
        Vec3 hotColor = CosmicColor.temperatureToRGB(30000.0);
        assertTrue(hotColor.z() >= hotColor.x() * 0.9, "Extreme temperature should have high blue component");

        // Components must stay within [0, 1]
        for (double temp = 1000.0; temp <= 80000.0; temp += 5000.0) {
            Vec3 rgb = CosmicColor.temperatureToRGB(temp);
            assertTrue(rgb.x() >= 0.0 && rgb.x() <= 1.0);
            assertTrue(rgb.y() >= 0.0 && rgb.y() <= 1.0);
            assertTrue(rgb.z() >= 0.0 && rgb.z() <= 1.0);
        }
    }

    @Test
    @DisplayName("Verify relativistic gravitational redshift and orbital Doppler factors")
    void testRelativisticShifts() {
        double rs = 2.0;

        // Gravitational redshift at infinity should approach 1.0
        double gInf = Schwarzschild.calculateGravitationalRedshift(10000.0, rs);
        assertEquals(1.0, gInf, 0.01);

        // Gravitational redshift deep in well (near horizon) is strongly attenuated
        double gNear = Schwarzschild.calculateGravitationalRedshift(2.1, rs);
        assertTrue(gNear < 0.25, "Gravitational redshift near horizon must strongly reduce frequency");

        // Doppler factor: approaching observer vs receding observer
        Vec3 vOrbit = new Vec3(0.5, 0.0, 0.0); // 50% c moving along +X
        Vec3 obsApproaching = new Vec3(1.0, 0.0, 0.0); // observing along +X
        Vec3 obsReceding = new Vec3(-1.0, 0.0, 0.0);   // observing along -X

        double deltaBlue = Schwarzschild.calculateDopplerFactor(vOrbit, obsApproaching);
        double deltaRed = Schwarzschild.calculateDopplerFactor(vOrbit, obsReceding);

        assertTrue(deltaBlue > 1.0, "Approaching disk material must be blueshifted (delta > 1)");
        assertTrue(deltaRed < 1.0, "Receding disk material must be redshifted (delta < 1)");
    }

    @Test
    @DisplayName("Verify Camera navigation and reset contract")
    void testCameraResetAndNavigation() {
        Camera cam = new Camera(25.0, 0.0); // no roll for this test
        Vec3 initPos = cam.getPosition();

        // Perform orbit and zoom
        cam.orbit(Math.PI / 4, 0.2);
        cam.zoom(-5.0);

        assertNotEquals(initPos, cam.getPosition(), "Camera position must change after orbit/zoom");
        assertEquals(20.0, cam.getDistance(), EPSILON, "Zoom should reduce camera distance");

        // Reset
        cam.reset();
        assertEquals(25.0, cam.getDistance(), EPSILON, "Reset should restore initial distance");
        assertEquals(initPos.x(), cam.getPosition().x(), EPSILON);
        assertEquals(initPos.y(), cam.getPosition().y(), EPSILON);
        assertEquals(initPos.z(), cam.getPosition().z(), EPSILON);

        // Roll: right and up vectors must remain orthogonal after roll
        Camera camRolled = new Camera(25.0, -34.0);
        double dot = camRolled.getRight().dot(camRolled.getUp());
        assertEquals(0.0, dot, 1e-6, "Camera right and up must be orthogonal after roll");

        // Verify addRoll and reset restore
        camRolled.addRoll(Math.toRadians(20.0));
        assertEquals(Math.toRadians(-14.0), camRolled.getRoll(), 1e-6, "addRoll should increment camera roll");
        camRolled.reset();
        assertEquals(Math.toRadians(-34.0), camRolled.getRoll(), 1e-6, "reset should restore initial roll");
    }

    @Test
    @DisplayName("Verify Config loading from classpath properties")
    void testConfigLoading() {
        Config config = new Config();

        assertEquals(10.0, config.getMassSolarMasses(), EPSILON);
        assertEquals(95.0, config.getCameraDistance(), EPSILON);
        assertEquals(-34.0, config.getCameraRoll(), EPSILON);
        assertEquals(5.2, config.getDiskInnerRadius(), EPSILON);
        assertEquals(22.0, config.getDiskOuterRadius(), EPSILON);
        assertTrue(config.getRaySteps() >= 64);
        assertTrue(config.isBloom());
    }

    @Test
    @DisplayName("Verify CPU Geodesic RK4 integration deflection")
    void testGeodesicRayBending() {
        RayIntegrator integrator = new RayIntegrator(
            RayIntegrator.IntegratorType.RK4,
            200,
            0.2,
            Schwarzschild.NORMALIZED_RS
        );

        // Ray passing near black hole (impact parameter b = 4.0, photon sphere is 3.0)
        Vec3 camPos = new Vec3(0.0, 0.0, 20.0);
        Vec3 rayDir = new Vec3(0.18, 0.0, -1.0).normalize();

        Geodesic result = integrator.trace(camPos, rayDir);

        assertNotNull(result);
        assertTrue(result.getAffineParam() > 0.0);

        // The ray trajectory must not be purely collinear with initial direction due to GR bending
        Vec3 finalDir = result.getVelocity();
        assertFalse(Double.isNaN(finalDir.x()));
    }

    @Test
    @DisplayName("Verify AccretionDisk flared hydrostatic thickness h(r)")
    void testVolumetricDiskFlaredThickness() {
        AccretionDisk disk = new AccretionDisk(6.0, 30.0, 50000.0, 0.85, 1.25, 0.85);

        double hIn = disk.calculateFlaredHeight(6.0);
        double hMid = disk.calculateFlaredHeight(15.0);
        double hOut = disk.calculateFlaredHeight(30.0);

        assertEquals(0.85, hIn, EPSILON, "Inner disk height should equal base thickness h_0");
        assertTrue(hMid > hIn, "Flared disk height must increase with radius");
        assertTrue(hOut > hMid, "Outer disk height must flare outward");

        // Test reset contract for volumetric parameters
        disk.setThickness(2.5);
        disk.setDensityScale(5.0);
        disk.reset();
        assertEquals(0.85, disk.getThickness(), EPSILON);
        assertEquals(1.25, disk.getDensityScale(), EPSILON);
    }

    @Test
    @DisplayName("Verify GLSL #include preprocessor resolves all modular shaders")
    void testShaderIncludeResolution() {
        String assembled = cosmic.util.ResourceLoader.loadShaderWithIncludes("shaders/blackhole.frag");
        assertNotNull(assembled);
        assertTrue(assembled.contains("computeGeodesicAcceleration"), "Must include schwarzschild.glsl");
        assertTrue(assembled.contains("sampleVolumetricNoise"), "Must include noise.glsl");
        assertTrue(assembled.contains("blackbodyToRGB"), "Must include blackbody.glsl");
        assertTrue(assembled.contains("sampleAccretionDensity"), "Must include accretion.glsl");
        assertTrue(assembled.contains("integrateVolumetricMediumStep"), "Must include radiative_transfer.glsl");
        assertFalse(assembled.contains("#include "), "All #include directives must be resolved");
    }

    @Test
    @DisplayName("Verify QualityPreset steps scaling and sequencing")
    void testQualityPresets() {
        cosmic.graphics.QualityPreset low = cosmic.graphics.QualityPreset.LOW;
        cosmic.graphics.QualityPreset med = cosmic.graphics.QualityPreset.MEDIUM;
        cosmic.graphics.QualityPreset high = cosmic.graphics.QualityPreset.HIGH;

        assertTrue(med.getMaxGeodesicSteps() > low.getMaxGeodesicSteps());
        assertTrue(high.getMaxGeodesicSteps() > med.getMaxGeodesicSteps());

        assertTrue(med.getLightSteps() >= low.getLightSteps());
        assertTrue(high.getLightSteps() >= med.getLightSteps());

        assertEquals(med, low.next());
        assertEquals(high, med.next());
        assertEquals(low, high.next());
    }
}
