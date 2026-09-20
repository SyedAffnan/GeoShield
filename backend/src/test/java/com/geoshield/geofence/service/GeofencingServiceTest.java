package com.geoshield.geofence.service;

import com.geoshield.common.util.GeoDistanceUtil;
import com.geoshield.geofence.model.GeofenceEvaluationResult;
import com.geoshield.geofence.model.GeofenceZone;
import com.geoshield.geofence.model.HazardGeometry;
import com.geoshield.geofence.provider.HazardGeometryProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GeofencingServiceTest {

    private GeofencingServiceImpl service;
    private HazardGeometry testHazard;
    private final Instant testNow = Instant.parse("2026-09-20T12:00:00Z");

    @BeforeEach
    void setUp() {
        service = new GeofencingServiceImpl(Clock.fixed(testNow, ZoneOffset.UTC));
        // Placed at (0.0, 0.0) so latitude offset in degrees gives exact spherical distance
        testHazard = HazardGeometry.ofPoint(
                "TEST-HZ-001",
                0.0,
                0.0,
                true,
                "TEST_FIXTURE_DO_NOT_USE_FOR_NAVIGATION"
        );
    }

    /**
     * Calculates latitude corresponding to exact distance d north of (0.0, 0.0),
     * ensuring the computed Haversine distance matches d within floating point limits without overshooting.
     */
    private double latForDistanceMeters(double d) {
        double lat = (d / 6371000.0) * (180.0 / Math.PI);
        while (GeoDistanceUtil.distanceMeters(lat, 0.0, 0.0, 0.0) > d) {
            lat = Math.nextDown(lat);
        }
        return lat;
    }

    @Test
    @DisplayName("Empty production provider returns empty list")
    void emptyProductionProviderReturnsEmptyList() {
        HazardGeometryProvider provider = HazardGeometryProvider.emptyProduction();
        assertNotNull(provider.getActiveHazards());
        assertTrue(provider.getActiveHazards().isEmpty());
    }

    @Test
    @DisplayName("Test fixture is explicitly marked synthetic")
    void testFixtureIsExplicitlySynthetic() {
        assertTrue(testHazard.isSynthetic());
        assertEquals("TEST_FIXTURE_DO_NOT_USE_FOR_NAVIGATION", testHazard.getDataSource());
        assertEquals(500.0, testHazard.getCoreRadiusMeters());
        assertEquals(1000.0, testHazard.getPreWarningRadiusMeters());
    }

    // =========================================================================
    // CANONICAL GOLDEN VECTORS V1 to V11 (accuracy = 80m -> H = 160m)
    // Core Exit = 660m, Pre-Warning Exit = 1160m
    // =========================================================================

    @Test
    @DisplayName("V1: previous=PRE_WARNING, d=550m, acc=80m -> PRE_WARNING (H never expands Core entry)")
    void goldenVector1() {
        double userLat = latForDistanceMeters(550.0);
        GeofenceEvaluationResult result = service.evaluate(
                userLat, 0.0, 80.0, GeofenceZone.PRE_WARNING, testHazard, testNow
        );
        assertEquals(GeofenceZone.PRE_WARNING, result.getNextZone());
        assertFalse(result.isTransitionOccurred());
        assertEquals(550.0, result.getDistanceMeters(), 0.01);
    }

    @Test
    @DisplayName("V2: previous=PRE_WARNING, d=499m, acc=80m -> CORE (Crosses fixed Core entry)")
    void goldenVector2() {
        double userLat = latForDistanceMeters(499.0);
        GeofenceEvaluationResult result = service.evaluate(
                userLat, 0.0, 80.0, GeofenceZone.PRE_WARNING, testHazard, testNow
        );
        assertEquals(GeofenceZone.CORE, result.getNextZone());
        assertTrue(result.isTransitionOccurred());
        assertEquals(499.0, result.getDistanceMeters(), 0.01);
    }

    @Test
    @DisplayName("V3: previous=CORE, d=550m, acc=80m -> CORE (Exit hysteresis retains Core)")
    void goldenVector3() {
        double userLat = latForDistanceMeters(550.0);
        GeofenceEvaluationResult result = service.evaluate(
                userLat, 0.0, 80.0, GeofenceZone.CORE, testHazard, testNow
        );
        assertEquals(GeofenceZone.CORE, result.getNextZone());
        assertFalse(result.isTransitionOccurred());
        assertEquals(550.0, result.getDistanceMeters(), 0.01);
    }

    @Test
    @DisplayName("V4: previous=CORE, d=661m, acc=80m -> PRE_WARNING (Crosses expanded Core exit)")
    void goldenVector4() {
        double userLat = latForDistanceMeters(661.0);
        GeofenceEvaluationResult result = service.evaluate(
                userLat, 0.0, 80.0, GeofenceZone.CORE, testHazard, testNow
        );
        assertEquals(GeofenceZone.PRE_WARNING, result.getNextZone());
        assertTrue(result.isTransitionOccurred());
        assertEquals(661.0, result.getDistanceMeters(), 0.01);
    }

    @Test
    @DisplayName("V5: previous=PRE_WARNING, d=1161m, acc=80m -> OUTSIDE (Crosses expanded Pre-Warning exit)")
    void goldenVector5() {
        double userLat = latForDistanceMeters(1161.0);
        GeofenceEvaluationResult result = service.evaluate(
                userLat, 0.0, 80.0, GeofenceZone.PRE_WARNING, testHazard, testNow
        );
        assertEquals(GeofenceZone.OUTSIDE, result.getNextZone());
        assertTrue(result.isTransitionOccurred());
        assertEquals(1161.0, result.getDistanceMeters(), 0.01);
    }

    @Test
    @DisplayName("V6: previous=PRE_WARNING, d=501m, acc=80m -> PRE_WARNING (Boundary test)")
    void goldenVector6() {
        double userLat = latForDistanceMeters(501.0);
        GeofenceEvaluationResult result = service.evaluate(
                userLat, 0.0, 80.0, GeofenceZone.PRE_WARNING, testHazard, testNow
        );
        assertEquals(GeofenceZone.PRE_WARNING, result.getNextZone());
        assertFalse(result.isTransitionOccurred());
        assertEquals(501.0, result.getDistanceMeters(), 0.01);
    }

    @Test
    @DisplayName("V7: previous=OUTSIDE, d=1001m, acc=80m -> OUTSIDE (Boundary test)")
    void goldenVector7() {
        double userLat = latForDistanceMeters(1001.0);
        GeofenceEvaluationResult result = service.evaluate(
                userLat, 0.0, 80.0, GeofenceZone.OUTSIDE, testHazard, testNow
        );
        assertEquals(GeofenceZone.OUTSIDE, result.getNextZone());
        assertFalse(result.isTransitionOccurred());
        assertEquals(1001.0, result.getDistanceMeters(), 0.01);
    }

    @Test
    @DisplayName("V8: previous=OUTSIDE, d=999m, acc=80m -> PRE_WARNING (Crosses fixed Pre-Warning entry)")
    void goldenVector8() {
        double userLat = latForDistanceMeters(999.0);
        GeofenceEvaluationResult result = service.evaluate(
                userLat, 0.0, 80.0, GeofenceZone.OUTSIDE, testHazard, testNow
        );
        assertEquals(GeofenceZone.PRE_WARNING, result.getNextZone());
        assertTrue(result.isTransitionOccurred());
        assertEquals(999.0, result.getDistanceMeters(), 0.01);
    }

    @Test
    @DisplayName("V9: previous=OUTSIDE, d=450m, acc=80m -> CORE (Direct entry into Core)")
    void goldenVector9() {
        double userLat = latForDistanceMeters(450.0);
        GeofenceEvaluationResult result = service.evaluate(
                userLat, 0.0, 80.0, GeofenceZone.OUTSIDE, testHazard, testNow
        );
        assertEquals(GeofenceZone.CORE, result.getNextZone());
        assertTrue(result.isTransitionOccurred());
        assertEquals(450.0, result.getDistanceMeters(), 0.01);
    }

    @Test
    @DisplayName("V10: previous=CORE, d=1161m, acc=80m -> OUTSIDE (Direct exit to Outside)")
    void goldenVector10() {
        double userLat = latForDistanceMeters(1161.0);
        GeofenceEvaluationResult result = service.evaluate(
                userLat, 0.0, 80.0, GeofenceZone.CORE, testHazard, testNow
        );
        assertEquals(GeofenceZone.OUTSIDE, result.getNextZone());
        assertTrue(result.isTransitionOccurred());
        assertEquals(1161.0, result.getDistanceMeters(), 0.01);
    }

    @Test
    @DisplayName("V11: previous=PRE_WARNING, d=1050m, acc=80m -> PRE_WARNING (Exit hysteresis retains Pre-Warning)")
    void goldenVector11() {
        double userLat = latForDistanceMeters(1050.0);
        GeofenceEvaluationResult result = service.evaluate(
                userLat, 0.0, 80.0, GeofenceZone.PRE_WARNING, testHazard, testNow
        );
        assertEquals(GeofenceZone.PRE_WARNING, result.getNextZone());
        assertFalse(result.isTransitionOccurred());
        assertEquals(1050.0, result.getDistanceMeters(), 0.01);
    }

    // =========================================================================
    // EXACT BOUNDARY POINTS VERIFICATION (500m, 1000m, 660m, 1160m)
    // =========================================================================

    @Test
    @DisplayName("Exact boundary 500m: d <= 500.0m enters CORE; d = 501.0m remains PRE_WARNING")
    void boundary500mCoreEntry() {
        // d <= 500.0m exactly -> CORE entry
        double userLat500 = latForDistanceMeters(500.0);
        GeofenceEvaluationResult res500 = service.evaluate(
                userLat500, 0.0, 20.0, GeofenceZone.PRE_WARNING, testHazard, testNow
        );
        assertEquals(GeofenceZone.CORE, res500.getNextZone());
        assertTrue(res500.isTransitionOccurred());

        // d = 501.0m -> PRE_WARNING remains
        double userLat501 = latForDistanceMeters(501.0);
        GeofenceEvaluationResult res501 = service.evaluate(
                userLat501, 0.0, 20.0, GeofenceZone.PRE_WARNING, testHazard, testNow
        );
        assertEquals(GeofenceZone.PRE_WARNING, res501.getNextZone());
        assertFalse(res501.isTransitionOccurred());
    }

    @Test
    @DisplayName("Exact boundary 1000m: d <= 1000.0m enters PRE_WARNING; d = 1001.0m remains OUTSIDE")
    void boundary1000mPreWarningEntry() {
        // d <= 1000.0m exactly -> PRE_WARNING entry
        double userLat1000 = latForDistanceMeters(1000.0);
        GeofenceEvaluationResult res1000 = service.evaluate(
                userLat1000, 0.0, 20.0, GeofenceZone.OUTSIDE, testHazard, testNow
        );
        assertEquals(GeofenceZone.PRE_WARNING, res1000.getNextZone());
        assertTrue(res1000.isTransitionOccurred());

        // d = 1001.0m -> OUTSIDE remains
        double userLat1001 = latForDistanceMeters(1001.0);
        GeofenceEvaluationResult res1001 = service.evaluate(
                userLat1001, 0.0, 20.0, GeofenceZone.OUTSIDE, testHazard, testNow
        );
        assertEquals(GeofenceZone.OUTSIDE, res1001.getNextZone());
        assertFalse(res1001.isTransitionOccurred());
    }

    @Test
    @DisplayName("Exact boundary 660m (acc=80m, H=160m): d = 660.0m remains CORE; d = 661.0m exits to PRE_WARNING")
    void boundary660mCoreExit() {
        // d = 660.0m (d <= 500 + 160) -> retains CORE (hysteresis holds exit)
        double userLat660 = latForDistanceMeters(660.0);
        GeofenceEvaluationResult res660 = service.evaluate(
                userLat660, 0.0, 80.0, GeofenceZone.CORE, testHazard, testNow
        );
        assertEquals(GeofenceZone.CORE, res660.getNextZone());
        assertFalse(res660.isTransitionOccurred());

        // d = 661.0m (d > 500 + 160) -> exits to PRE_WARNING
        double userLat661 = latForDistanceMeters(661.0);
        GeofenceEvaluationResult res661 = service.evaluate(
                userLat661, 0.0, 80.0, GeofenceZone.CORE, testHazard, testNow
        );
        assertEquals(GeofenceZone.PRE_WARNING, res661.getNextZone());
        assertTrue(res661.isTransitionOccurred());
    }

    @Test
    @DisplayName("Exact boundary 1160m (acc=80m, H=160m): d = 1160.0m remains PRE_WARNING; d = 1161.0m exits to OUTSIDE")
    void boundary1160mPreWarningExit() {
        // d = 1160.0m (d <= 1000 + 160) -> retains PRE_WARNING (hysteresis holds exit)
        double userLat1160 = latForDistanceMeters(1160.0);
        GeofenceEvaluationResult res1160 = service.evaluate(
                userLat1160, 0.0, 80.0, GeofenceZone.PRE_WARNING, testHazard, testNow
        );
        assertEquals(GeofenceZone.PRE_WARNING, res1160.getNextZone());
        assertFalse(res1160.isTransitionOccurred());

        // d = 1161.0m (d > 1000 + 160) -> exits to OUTSIDE
        double userLat1161 = latForDistanceMeters(1161.0);
        GeofenceEvaluationResult res1161 = service.evaluate(
                userLat1161, 0.0, 80.0, GeofenceZone.PRE_WARNING, testHazard, testNow
        );
        assertEquals(GeofenceZone.OUTSIDE, res1161.getNextZone());
        assertTrue(res1161.isTransitionOccurred());
    }

    // =========================================================================
    // ACCURACY VARIATIONS & PHASE 0.2 VALIDITY
    // =========================================================================

    @Test
    @DisplayName("Null accuracy defaults to H = 50.0m and is accepted per Phase 0.2")
    void nullAccuracyDefaultsTo50m() {
        // Core exit with H=50m is 550m. At 551m, previous=CORE should exit to PRE_WARNING
        double userLat = latForDistanceMeters(551.0);
        GeofenceEvaluationResult result = service.evaluate(
                userLat, 0.0, null, GeofenceZone.CORE, testHazard, testNow
        );
        assertEquals(GeofenceZone.PRE_WARNING, result.getNextZone());
        assertTrue(result.isTransitionOccurred());
    }

    @Test
    @DisplayName("Accuracy 25m produces H = max(50, 50) = 50.0m")
    void accuracy25mProduces50mHysteresis() {
        double userLat = latForDistanceMeters(549.0);
        GeofenceEvaluationResult result = service.evaluate(
                userLat, 0.0, 25.0, GeofenceZone.CORE, testHazard, testNow
        );
        assertEquals(GeofenceZone.CORE, result.getNextZone()); // Retained in Core (549 <= 550)
    }

    @Test
    @DisplayName("Accuracy exactly 100m is accepted per Phase 0.2 (produces H = max(50, 200) = 200.0m)")
    void accuracy100mAccepted() {
        double userLat = latForDistanceMeters(699.0);
        GeofenceEvaluationResult result = service.evaluate(
                userLat, 0.0, 100.0, GeofenceZone.CORE, testHazard, testNow
        );
        assertEquals(GeofenceZone.CORE, result.getNextZone()); // Retained in Core (699 <= 700)
    }

    @Test
    @DisplayName("Reject accuracy exceeding 100 meters per Phase 0.2")
    void rejectAccuracyExceeding100m() {
        double userLat = latForDistanceMeters(300.0);
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(
                userLat, 0.0, 100.1, GeofenceZone.OUTSIDE, testHazard, testNow
        ));
    }

    @Test
    @DisplayName("Reject negative accuracy per Phase 0.2")
    void rejectNegativeAccuracy() {
        double userLat = latForDistanceMeters(300.0);
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(
                userLat, 0.0, -1.0, GeofenceZone.OUTSIDE, testHazard, testNow
        ));
    }

    // =========================================================================
    // PHASE 0.2 LOCATION VALIDITY CHECKS
    // =========================================================================

    @Test
    @DisplayName("Rejects stale location fix older than 15 minutes per Phase 0.2")
    void rejectsStaleLocationFix() {
        // Fix older than 15 minutes by 1 second is rejected
        Instant staleTimestamp = testNow.minus(15, ChronoUnit.MINUTES).minusSeconds(1);
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(
                0.0, 0.0, 20.0, GeofenceZone.OUTSIDE, testHazard, staleTimestamp
        ));

        // Fix at exactly 15 minutes is accepted
        Instant boundaryTimestamp = testNow.minus(15, ChronoUnit.MINUTES);
        assertDoesNotThrow(() -> service.evaluate(
                0.0, 0.0, 20.0, GeofenceZone.OUTSIDE, testHazard, boundaryTimestamp
        ));
    }

    @Test
    @DisplayName("Rejects future-dated location fix strictly (including +1 second) per Phase 0.2")
    void rejectsFutureDatedLocationFix() {
        // +1 second future timestamp must be strictly rejected
        Instant future1s = testNow.plusSeconds(1);
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(
                0.0, 0.0, 20.0, GeofenceZone.OUTSIDE, testHazard, future1s
        ));

        // +2 minutes future timestamp rejected
        Instant future2m = testNow.plus(2, ChronoUnit.MINUTES);
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(
                0.0, 0.0, 20.0, GeofenceZone.OUTSIDE, testHazard, future2m
        ));
    }

    // =========================================================================
    // STATELESSNESS & MULTI-HAZARD BATCH EVALUATION
    // =========================================================================

    @Test
    @DisplayName("Stateless backend: repeated identical calls produce identical results with no side effects")
    void statelessRepeatedCallsProduceIdenticalResults() {
        double userLat = latForDistanceMeters(400.0);

        GeofenceEvaluationResult r1 = service.evaluate(userLat, 0.0, 20.0, GeofenceZone.OUTSIDE, testHazard, testNow);
        GeofenceEvaluationResult r2 = service.evaluate(userLat, 0.0, 20.0, GeofenceZone.OUTSIDE, testHazard, testNow);

        assertEquals(r1, r2);
        assertEquals(GeofenceZone.CORE, r1.getNextZone());
        assertTrue(r1.isTransitionOccurred());
    }

    @Test
    @DisplayName("evaluateAll processes multiple hazards in deterministic hazard ID order")
    void evaluateAllProcessesInDeterministicOrder() {
        HazardGeometry hB = HazardGeometry.ofPoint("TEST-HZ-002", 0.0, 0.01, true, "TEST_FIXTURE_DO_NOT_USE_FOR_NAVIGATION");
        HazardGeometry hA = HazardGeometry.ofPoint("TEST-HZ-001", 0.0, 0.02, true, "TEST_FIXTURE_DO_NOT_USE_FOR_NAVIGATION");
        HazardGeometry hC = HazardGeometry.ofPoint("TEST-HZ-003", 0.0, 0.03, true, "TEST_FIXTURE_DO_NOT_USE_FOR_NAVIGATION");

        // Supply in arbitrary order: hC, hA, hB
        List<GeofenceEvaluationResult> results = service.evaluateAll(
                0.0, 0.0, 10.0, Map.of(), List.of(hC, hA, hB), testNow
        );

        assertEquals(3, results.size());
        assertEquals("TEST-HZ-001", results.get(0).getHazardId());
        assertEquals("TEST-HZ-002", results.get(1).getHazardId());
        assertEquals("TEST-HZ-003", results.get(2).getHazardId());
    }
}
