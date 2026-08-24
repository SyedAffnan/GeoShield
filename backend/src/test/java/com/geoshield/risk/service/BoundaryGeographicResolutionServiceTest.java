package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.risk.dto.GeographicResolution;
import com.geoshield.risk.geo.StateBoundaryIndex;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Point-in-polygon GPS to State/UT resolution against the bundled boundary resource. */
class BoundaryGeographicResolutionServiceTest {
    private static BoundaryGeographicResolutionService service;

    @BeforeAll
    static void loadOnce() {
        service = new BoundaryGeographicResolutionService(new StateBoundaryIndex());
    }

    private static GeographicResolution resolve(String latitude, String longitude) {
        return service.resolve(new BigDecimal(latitude), new BigDecimal(longitude));
    }

    @ParameterizedTest(name = "{2} ({0}, {1}) resolves to {3}")
    @CsvSource({
        // latitude, longitude, place, expected State/UT
        "12.9716, 77.5946, Bengaluru,  Karnataka",
        "19.0760, 72.8777, Mumbai,     Maharashtra",
        "13.0827, 80.2707, Chennai,    Tamil Nadu",
        "28.6139, 77.2090, New Delhi,  Delhi",
        "26.9124, 75.7873, Jaipur,     Rajasthan",
        "22.5726, 88.3639, Kolkata,    West Bengal",
        "17.3850, 78.4867, Hyderabad,  Telangana",
        "15.4989, 73.8278, Panaji,     Goa",
    })
    void resolvesKnownCoordinatesToTheirActualStateUt(String latitude, String longitude, String place, String expected) {
        GeographicResolution resolution = resolve(latitude, longitude);
        assertTrue(resolution.resolved(), place + " should resolve, reason: " + resolution.reason());
        assertEquals(GeographicLevel.STATE_UT, resolution.geographicLevel());
        assertEquals(expected, resolution.geographicUnit());
        assertNull(resolution.reason(), "a resolved result carries no unavailability reason");
    }

    @ParameterizedTest(name = "{2} resolves to the MoRTH spelling {3}")
    @CsvSource({
        // Each row exercises one of the four explicit name normalizations.
        "11.6234, 92.7265, Port Blair, Andaman and Nicobar Islands",
        "27.0844, 93.6053, Itanagar,   Arunachal Pradesh",
        "34.0837, 74.7973, Srinagar,   Jammu and Kashmir",
        "20.3974, 72.8328, Daman,      Dadra and Nagar Haveli and Daman and Diu",
    })
    void resolvesToNormalizedNamesThatMatchTheMorthGeographicUnit(String latitude, String longitude,
            String place, String expected) {
        GeographicResolution resolution = resolve(latitude, longitude);
        assertTrue(resolution.resolved(), place + " should resolve, reason: " + resolution.reason());
        assertEquals(expected, resolution.geographicUnit());
        assertFalse(resolution.geographicUnit().contains("&"), "the MoRTH spelling never uses '&'");
    }

    @Test
    void resolvesDiscontiguousUnionTerritoriesThroughMultiPolygonHandling() {
        // Lakshadweep is a separate island group far from the mainland; it only resolves
        // if every polygon of a MultiPolygon feature is tested, not just the first.
        GeographicResolution lakshadweep = resolve("10.5593", "72.6417");
        assertTrue(lakshadweep.resolved(), "reason: " + lakshadweep.reason());
        assertEquals("Lakshadweep", lakshadweep.geographicUnit());
    }

    @ParameterizedTest(name = "{2} is explicitly unresolved")
    @CsvSource({
        "15.0000, 85.0000, Bay of Bengal",
        "15.0000, 68.0000, Arabian Sea",
        "48.8566,  2.3522, Paris",
        "-33.8688, 151.2093, Sydney",
    })
    void returnsExplicitUnresolvedForCoordinatesOutsideEveryStateUtPolygon(String latitude, String longitude,
            String place) {
        GeographicResolution resolution = resolve(latitude, longitude);
        assertFalse(resolution.resolved(), place + " must not resolve to a State/UT");
        assertNull(resolution.geographicUnit(), "an unresolved result must never guess a State/UT");
        assertNull(resolution.geographicLevel());
        assertTrue(resolution.reason().contains("No India State/UT boundary contains"));
    }

    @ParameterizedTest(name = "latitude {0} is rejected as out of range")
    @CsvSource({"91", "-91", "200", "-1000"})
    void returnsUnresolvedForInvalidLatitudeWithoutThrowing(String latitude) {
        GeographicResolution resolution = resolve(latitude, "77.5946");
        assertFalse(resolution.resolved());
        assertNull(resolution.geographicUnit());
        assertTrue(resolution.reason().contains("Latitude"));
        assertTrue(resolution.reason().contains("-90 to 90"));
    }

    @ParameterizedTest(name = "longitude {0} is rejected as out of range")
    @CsvSource({"181", "-181", "500", "-1000"})
    void returnsUnresolvedForInvalidLongitudeWithoutThrowing(String longitude) {
        GeographicResolution resolution = resolve("12.9716", longitude);
        assertFalse(resolution.resolved());
        assertNull(resolution.geographicUnit());
        assertTrue(resolution.reason().contains("Longitude"));
        assertTrue(resolution.reason().contains("-180 to 180"));
    }

    @Test
    void treatsExactRangeLimitsAsValidInputRatherThanErrors() {
        // The poles and the antimeridian are valid coordinates that simply resolve to nothing.
        for (String[] pair : new String[][] {{"90", "180"}, {"-90", "-180"}}) {
            GeographicResolution resolution = resolve(pair[0], pair[1]);
            assertFalse(resolution.resolved());
            assertTrue(resolution.reason().contains("No India State/UT boundary contains"),
                    "in-range limits are not a validation failure, got: " + resolution.reason());
        }
    }

    @Test
    void returnsUnresolvedRatherThanThrowingWhenACoordinateIsAbsent() {
        for (GeographicResolution resolution : new GeographicResolution[] {
            service.resolve(null, new BigDecimal("77.5946")),
            service.resolve(new BigDecimal("12.9716"), null),
            service.resolve(null, null),
        }) {
            assertFalse(resolution.resolved());
            assertNull(resolution.geographicUnit());
            assertTrue(resolution.reason().contains("both required"));
        }
    }

    @Test
    void resolvesTheSameCoordinateDeterministicallyOnRepeatedCalls() {
        GeographicResolution first = resolve("12.9716", "77.5946");
        for (int attempt = 0; attempt < 25; attempt++) {
            assertEquals(first, resolve("12.9716", "77.5946"));
        }
    }

    @Test
    void doesNotConfuseLatitudeAndLongitudeAxisOrder() {
        // Bengaluru is (lat 12.97, lon 77.59). Swapping the axes gives (lat 77.59, lon 12.97),
        // which is in the Arctic Ocean and must not resolve to any State/UT.
        assertEquals("Karnataka", resolve("12.9716", "77.5946").geographicUnit());
        assertFalse(resolve("77.5946", "12.9716").resolved(), "swapped axes must not resolve");
    }
}
