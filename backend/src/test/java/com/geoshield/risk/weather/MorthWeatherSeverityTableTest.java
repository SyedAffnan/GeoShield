package com.geoshield.risk.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Verifies the bundled MoRTH Table 3.8 transcription and the derived severity/normalized columns.
 *
 * <p>The expected values below are computed independently from the published counts, so a change
 * to either the resource or the normalization rule fails here rather than silently shifting scores.
 */
class MorthWeatherSeverityTableTest {
    private final MorthWeatherSeverityTable table = new MorthWeatherSeverityTable();

    @Test
    void loadsTheBundledResourceSuccessfully() {
        assertTrue(table.isLoaded());
        assertNull(table.unavailabilityReason());
        assertEquals(5, table.severities().size());
    }

    @Test
    void reconcilesBothColumnsAgainstTable38sOwnPrintedTotalRow() {
        assertEquals(487_707L, table.totalAccidents());
        assertEquals(177_175L, table.totalKilled());
    }

    @ParameterizedTest(name = "{0} publishes {1} accidents and {2} killed")
    @CsvSource({
        "SUNNY_CLEAR,  372387, 128513",
        "RAINY,         35284,  12665",
        "FOGGY_MISTY,   33955,  15115",
        "HAIL_SLEET,     4310,   1919",
        "OTHERS,        41771,  18963",
    })
    void transcribesEveryPublishedCountVerbatim(WeatherCategory category, long accidents, long killed) {
        WeatherConditionSeverity severity = table.severityOf(category).orElseThrow();

        assertEquals(accidents, severity.accidents());
        assertEquals(killed, severity.killed());
    }

    @ParameterizedTest(name = "{0} -> {1} killed per 100 accidents -> normalized {2}")
    @CsvSource({
        // killed / accidents x 100, then rescaled against the most severe published condition.
        "SUNNY_CLEAR,  34.51060322,  76.01868939",
        "RAINY,        35.89445641,  79.06699039",
        "FOGGY_MISTY,  44.51479900,  98.05556447",
        "HAIL_SLEET,   44.52436195,  98.07662938",
        "OTHERS,       45.39752460, 100.00000000",
    })
    void derivesTheSeverityAndNormalizedValueFromThePublishedCounts(WeatherCategory category,
            String killedPerHundred, String normalized) {
        WeatherConditionSeverity severity = table.severityOf(category).orElseThrow();

        assertEquals(new BigDecimal(killedPerHundred), severity.killedPerHundredAccidents());
        assertEquals(new BigDecimal(normalized), severity.normalizedRisk());
    }

    @Test
    void doesNotRankClearWeatherAsTheMostDangerousConditionDespiteItsLargestAccidentShare() {
        WeatherConditionSeverity clear = table.severityOf(WeatherCategory.SUNNY_CLEAR).orElseThrow();
        WeatherConditionSeverity fog = table.severityOf(WeatherCategory.FOGGY_MISTY).orElseThrow();
        WeatherConditionSeverity hail = table.severityOf(WeatherCategory.HAIL_SLEET).orElseThrow();

        // Clear weather holds 76.4% of accidents yet must score lowest, because that share is
        // exposure. Normalizing the raw share instead would invert the whole factor.
        assertTrue(clear.accidents() > fog.accidents() + hail.accidents());
        assertTrue(clear.normalizedRisk().compareTo(fog.normalizedRisk()) < 0);
        assertTrue(clear.normalizedRisk().compareTo(hail.normalizedRisk()) < 0);
    }

    @Test
    void ordersTheConditionsByPublishedSeverityWithClearLowestAndTheResidualBucketHighest() {
        List<BigDecimal> ascending = List.of(
                table.severityOf(WeatherCategory.SUNNY_CLEAR).orElseThrow().normalizedRisk(),
                table.severityOf(WeatherCategory.RAINY).orElseThrow().normalizedRisk(),
                table.severityOf(WeatherCategory.FOGGY_MISTY).orElseThrow().normalizedRisk(),
                table.severityOf(WeatherCategory.HAIL_SLEET).orElseThrow().normalizedRisk(),
                table.severityOf(WeatherCategory.OTHERS).orElseThrow().normalizedRisk());

        for (int i = 1; i < ascending.size(); i++) {
            assertTrue(ascending.get(i).compareTo(ascending.get(i - 1)) > 0,
                    "severity must increase strictly: " + ascending);
        }
    }

    @ParameterizedTest(name = "{0} normalizes inside the fusion range")
    @EnumSource(WeatherCategory.class)
    void keepsEveryNormalizedValueInsideTheRequiredZeroToHundredRange(WeatherCategory category) {
        BigDecimal normalized = table.severityOf(category).orElseThrow().normalizedRisk();

        assertTrue(normalized.signum() > 0);
        assertTrue(normalized.compareTo(new BigDecimal("100")) <= 0);
    }

    @Test
    void mapsExactlyOneConditionToOneHundredSoTheScaleIsAnchoredToRealPublishedData() {
        assertEquals(1, table.severities().stream()
                .filter(severity -> severity.normalizedRisk().compareTo(new BigDecimal("100.00000000")) == 0)
                .count());
    }

    @Test
    void returnsSeveritiesInPublishedTable38RowOrder() {
        assertEquals(List.of(WeatherCategory.SUNNY_CLEAR, WeatherCategory.RAINY, WeatherCategory.FOGGY_MISTY,
                        WeatherCategory.HAIL_SLEET, WeatherCategory.OTHERS),
                table.severities().stream().map(WeatherConditionSeverity::category).toList());
    }

    @Test
    void quotesThePublishedLabelForEachCondition() {
        assertEquals("Foggy & misty", table.severityOf(WeatherCategory.FOGGY_MISTY).orElseThrow().label());
        assertEquals("Hail / sleet", table.severityOf(WeatherCategory.HAIL_SLEET).orElseThrow().label());
    }

    @Test
    void carriesTheTable38ProvenanceAndTheNormalizationActuallyApplied() {
        assertEquals("MoRTH Road Accidents in India 2024, Table 3.8 (national, 2024)",
                MorthWeatherSeverityTable.SOURCE);
        assertEquals(2024, MorthWeatherSeverityTable.SOURCE_YEAR);
        assertTrue(MorthWeatherSeverityTable.NORMALIZATION.contains("killed per 100 accidents"));
        assertTrue(MorthWeatherSeverityTable.NORMALIZATION.contains("maximum"));
    }

    @Test
    void isDeterministicAcrossReloads() {
        MorthWeatherSeverityTable reloaded = new MorthWeatherSeverityTable();

        for (WeatherCategory category : WeatherCategory.values()) {
            assertEquals(table.severityOf(category).orElseThrow(), reloaded.severityOf(category).orElseThrow());
        }
    }

    @Test
    void reportsUnavailableWithoutThrowingWhenTheResourceIsMissing() {
        MorthWeatherSeverityTable missing = new MorthWeatherSeverityTable("risk/no-such-weather-resource.csv");

        assertFalse(missing.isLoaded());
        assertNotNull(missing.unavailabilityReason());
        assertTrue(missing.unavailabilityReason().contains("could not be loaded"));
        assertTrue(missing.severityOf(WeatherCategory.RAINY).isEmpty());
    }

    @ParameterizedTest(name = "{0} is rejected with a reason mentioning {1}")
    @CsvSource({
        "risk/test-weather-too-few-rows.csv,              'but found 4'",
        "risk/test-weather-unknown-category.csv,          'not a published MoRTH'",
        "risk/test-weather-bad-total.csv,                 'instead of the published Total'",
        "risk/test-weather-killed-exceeds-accidents.csv,  'more persons killed than accidents'",
    })
    void refusesAnInvalidTableInsteadOfNormalizingAgainstBadData(String resourcePath, String expectedReason) {
        MorthWeatherSeverityTable invalid = new MorthWeatherSeverityTable(resourcePath);

        assertFalse(invalid.isLoaded());
        assertTrue(invalid.unavailabilityReason().contains(expectedReason),
                invalid.unavailabilityReason());
    }
}
