package com.geoshield.risk.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies the documented WMO code to MoRTH category mapping, including what it refuses to map. */
class WmoWeatherCodeMapperTest {

    @ParameterizedTest(name = "WMO {0} -> {1}")
    @CsvSource({
        // Clear through overcast: no precipitation, no obscuration.
        "0,  SUNNY_CLEAR", "1,  SUNNY_CLEAR", "2,  SUNNY_CLEAR", "3,  SUNNY_CLEAR",
        // Fog and rime fog map directly onto the published condition.
        "45, FOGGY_MISTY", "48, FOGGY_MISTY",
        // Drizzle, rain, and rain showers at every published intensity.
        "51, RAINY", "53, RAINY", "55, RAINY", "61, RAINY", "63, RAINY", "65, RAINY",
        "80, RAINY", "81, RAINY", "82, RAINY",
        // A thunderstorm without hail is rain-bearing, so it maps to the liquid-precipitation row.
        "95, RAINY",
        // Freezing precipitation is sleet.
        "56, HAIL_SLEET", "57, HAIL_SLEET", "66, HAIL_SLEET", "67, HAIL_SLEET",
        // MoRTH publishes no snow row, so snow uses its only frozen-precipitation condition.
        "71, HAIL_SLEET", "73, HAIL_SLEET", "75, HAIL_SLEET", "77, HAIL_SLEET",
        "85, HAIL_SLEET", "86, HAIL_SLEET",
        // Thunderstorm with hail is explicit hail.
        "96, HAIL_SLEET", "99, HAIL_SLEET",
    })
    void mapsEveryDocumentedWmoCodeToItsPublishedMorthCondition(int wmoCode, WeatherCategory expected) {
        assertEquals(expected, WmoWeatherCodeMapper.categoryOf(wmoCode).orElseThrow());
    }

    @ParameterizedTest(name = "WMO {0} is deliberately unmapped")
    @ValueSource(ints = {-1, 4, 10, 20, 44, 46, 47, 49, 50, 58, 59, 60, 68, 69, 70, 72, 74, 76, 78, 79,
        83, 84, 87, 90, 93, 94, 97, 98, 100, 999})
    void leavesUndocumentedCodesUnmappedRatherThanApproximatingThem(int wmoCode) {
        assertTrue(WmoWeatherCodeMapper.categoryOf(wmoCode).isEmpty(),
                "WMO " + wmoCode + " must not be forced into a MoRTH category");
    }

    @Test
    void neverMapsAnyCodeOntoMorthsUnclassifiedOthersBucket() {
        // "Others" is published, so it anchors the normalization, but its composition is unknown and
        // it is the most severe row. Mapping an observed condition there would assert the worst
        // severity on a guess about what MoRTH counted.
        assertFalse(WmoWeatherCodeMapper.mapping().containsValue(WeatherCategory.OTHERS));
    }

    @Test
    void coversEveryOtherPublishedCategorySoAnObservationCanReachThem() {
        Set<WeatherCategory> reachable = Set.copyOf(WmoWeatherCodeMapper.mapping().values());

        assertEquals(Set.of(WeatherCategory.SUNNY_CLEAR, WeatherCategory.RAINY,
                WeatherCategory.FOGGY_MISTY, WeatherCategory.HAIL_SLEET), reachable);
    }

    @Test
    void mappingIsImmutableAndDeterministic() {
        assertEquals(WmoWeatherCodeMapper.mapping(), WmoWeatherCodeMapper.mapping());
        assertThrowsUnsupported(() -> WmoWeatherCodeMapper.mapping().put(0, WeatherCategory.OTHERS));
    }

    @Test
    void mapsExactlyTheDocumentedNumberOfCodes() {
        // 4 clear + 2 fog + 10 rainy + 12 hail/sleet, and nothing else.
        assertEquals(28, WmoWeatherCodeMapper.mapping().size());
        assertEquals(28, IntStream.rangeClosed(-1, 1000)
                .filter(code -> WmoWeatherCodeMapper.categoryOf(code).isPresent()).count());
    }

    private void assertThrowsUnsupported(Runnable mutation) {
        try {
            mutation.run();
            throw new AssertionError("the documented mapping must not be mutable");
        } catch (UnsupportedOperationException expected) {
            // The mapping is published as an immutable view, which is what we want.
        }
    }
}
