package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geoshield.risk.dto.NormalizedRiskFeature;
import com.geoshield.risk.dto.RiskFactorInput;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.dto.TimeOfDayBand;
import com.geoshield.risk.timeofday.MorthTimeOfDayDistribution;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Verifies that the current time selects the correct MoRTH 3-hour interval and that the feature
 * carries only published values.
 *
 * <p>Tests inject a fixed {@link Clock}. System time is never modified, and the production
 * constructor keeps using the real system clock.
 */
class TimeOfDayRiskServiceTest {
    /** MoRTH publishes times of occurrence in Indian local time. */
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final MorthTimeOfDayDistribution distribution = new MorthTimeOfDayDistribution();

    private TimeOfDayRiskService serviceAtIndianLocalTime(String localDateTime) {
        Instant instant = LocalDateTime.parse(localDateTime).atZone(IST).toInstant();
        return new TimeOfDayRiskService(Clock.fixed(instant, IST), distribution);
    }

    @ParameterizedTest(name = "{0} IST -> {1}:00 to {2}:00 hrs -> {3}")
    @CsvSource({
        // The nine required clock positions, including the 24:00 rollover to the next day.
        "2026-08-24T00:00:00, 0,  3,  24.29711265",
        "2026-08-24T03:00:00, 3,  6,  22.73924410",
        "2026-08-24T06:00:00, 6,  9,  48.00431499",
        "2026-08-24T09:00:00, 9,  12, 66.36442268",
        "2026-08-24T12:00:00, 12, 15, 69.37617229",
        "2026-08-24T15:00:00, 15, 18, 82.61659718",
        "2026-08-24T18:00:00, 18, 21, 100.00000000",
        "2026-08-24T21:00:00, 21, 24, 54.63230221",
        "2026-08-25T00:00:00, 0,  3,  24.29711265",
    })
    void mapsEachRequiredClockPositionToItsPublishedIntervalAndNormalizedValue(String localDateTime,
            int startHour, int endHour, String normalized) {
        TimeOfDayRiskService service = serviceAtIndianLocalTime(localDateTime);

        assertEquals(new TimeOfDayBand(startHour, endHour), service.currentBand());
        NormalizedRiskFeature feature = service.currentRisk();
        assertTrue(feature.available());
        assertEquals(new BigDecimal(normalized), feature.value());
    }

    @ParameterizedTest(name = "{0} IST is inside {1}:00 to {2}:00 hrs")
    @CsvSource({
        // Each interval's last representable instant, then the first instant of the next.
        "2026-08-24T02:59:59.999999999, 0,  3",
        "2026-08-24T03:00:00,           3,  6",
        "2026-08-24T05:59:59.999999999, 3,  6",
        "2026-08-24T06:00:00,           6,  9",
        "2026-08-24T08:59:59.999999999, 6,  9",
        "2026-08-24T09:00:00,           9,  12",
        "2026-08-24T11:59:59.999999999, 9,  12",
        "2026-08-24T12:00:00,           12, 15",
        "2026-08-24T14:59:59.999999999, 12, 15",
        "2026-08-24T15:00:00,           15, 18",
        "2026-08-24T17:59:59.999999999, 15, 18",
        "2026-08-24T18:00:00,           18, 21",
        "2026-08-24T20:59:59.999999999, 18, 21",
        "2026-08-24T21:00:00,           21, 24",
        "2026-08-24T23:59:59.999999999, 21, 24",
    })
    void placesEveryIntervalBoundaryInTheIntervalThatActuallyContainsIt(String localDateTime,
            int startHour, int endHour) {
        assertEquals(new TimeOfDayBand(startHour, endHour),
                serviceAtIndianLocalTime(localDateTime).currentBand());
    }

    @Test
    void rollsOverFromTheLastIntervalOfTheDayToTheFirstIntervalOfTheNext() {
        NormalizedRiskFeature endOfDay = serviceAtIndianLocalTime("2026-08-24T23:59:59.999999999").currentRisk();
        NormalizedRiskFeature startOfNextDay = serviceAtIndianLocalTime("2026-08-25T00:00:00").currentRisk();

        assertEquals(new BigDecimal("54.63230221"), endOfDay.value());
        assertEquals(new BigDecimal("24.29711265"), startOfNextDay.value());
    }

    @Test
    void resolvesTheIntervalInIndianLocalTimeRatherThanUtc() {
        // 13:00Z is 18:30 IST, which is MoRTH's peak 18:00 to 21:00 interval. Reading the UTC hour
        // instead would wrongly select 12:00 to 15:00 and report 69.37617229.
        TimeOfDayRiskService service = new TimeOfDayRiskService(
                Clock.fixed(Instant.parse("2026-08-24T13:00:00Z"), ZoneId.of("UTC")), distribution);

        assertEquals(new TimeOfDayBand(18, 21), service.currentBand());
        assertEquals(new BigDecimal("100.00000000"), service.currentRisk().value());
    }

    @Test
    void carriesTheMorthTableProvenanceAndTheNormalizationActuallyApplied() {
        NormalizedRiskFeature feature = serviceAtIndianLocalTime("2026-08-24T18:30:00").currentRisk();

        assertEquals(RiskFactorType.TIME_OF_DAY, feature.factor());
        assertEquals("MoRTH Road Accidents in India 2024, Table 7.3 (national, 2024)", feature.source());
        assertEquals("interval accident count / maximum interval accident count × 100",
                feature.normalization());
    }

    @Test
    void explainsTheIntervalWithItsPublishedCountShareAndNationalNonTouristSpecificScope() {
        String explanation = serviceAtIndianLocalTime("2026-08-24T18:30:00").currentRisk().reason();

        assertTrue(explanation.contains("18:00 to 21:00 hrs"), explanation);
        assertTrue(explanation.contains("Night"), explanation);
        assertTrue(explanation.contains("102897"), explanation);
        assertTrue(explanation.contains("21.1%"), explanation);
        assertTrue(explanation.contains("National aggregate for 2024"), explanation);
        assertTrue(explanation.contains("not State/UT-specific"), explanation);
        assertTrue(explanation.contains("not tourist-specific"), explanation);
    }

    @ParameterizedTest(name = "{0} IST produces a fusion input the engine accepts")
    @CsvSource({"2026-08-24T00:30:00", "2026-08-24T09:30:00", "2026-08-24T18:30:00", "2026-08-24T23:30:00"})
    void producesAFusionInputInsideTheRequiredZeroToHundredRange(String localDateTime) {
        RiskFactorInput input = serviceAtIndianLocalTime(localDateTime).currentRisk().toRiskFactorInput();

        assertTrue(input.available());
        assertNull(input.unavailabilityReason());
        assertTrue(input.normalizedRisk().signum() >= 0);
        assertTrue(input.normalizedRisk().compareTo(new BigDecimal("100")) <= 0);
    }

    @Test
    void returnsTheSameFeatureEveryTimeForTheSameInstant() {
        TimeOfDayRiskService service = serviceAtIndianLocalTime("2026-08-24T15:45:00");

        assertEquals(service.currentRisk(), service.currentRisk());
        assertEquals(service.currentRisk(), serviceAtIndianLocalTime("2026-08-24T15:45:00").currentRisk());
    }

    @Test
    void producesDifferentValuesForDifferentTimesOfDayFromTheRealDistribution() {
        var values = java.util.List.of("2026-08-24T00:30:00", "2026-08-24T03:30:00", "2026-08-24T06:30:00",
                        "2026-08-24T09:30:00", "2026-08-24T12:30:00", "2026-08-24T15:30:00",
                        "2026-08-24T18:30:00", "2026-08-24T21:30:00").stream()
                .map(localDateTime -> serviceAtIndianLocalTime(localDateTime).currentRisk().value())
                .toList();

        assertEquals(8, values.stream().distinct().count(), "each MoRTH interval must give its own value");
        // The published risk ordering: 18:00-21:00 is the peak and 03:00-06:00 the quietest.
        assertEquals(new BigDecimal("100.00000000"), values.stream().max(BigDecimal::compareTo).orElseThrow());
        assertEquals(new BigDecimal("22.73924410"), values.stream().min(BigDecimal::compareTo).orElseThrow());
    }

    @Test
    void reportsUnavailableWithoutSynthesizingAValueWhenTheDistributionCannotBeLoaded() {
        var service = new TimeOfDayRiskService(Clock.fixed(Instant.parse("2026-08-24T13:00:00Z"), IST),
                new MorthTimeOfDayDistribution("risk/no-such-time-of-day-resource.csv"));

        NormalizedRiskFeature feature = service.currentRisk();

        assertFalse(feature.available());
        assertNull(feature.value());
        assertNotNull(feature.reason());
        assertTrue(feature.reason().contains("could not be loaded"), feature.reason());
        assertFalse(feature.toRiskFactorInput().available());
        assertNull(feature.toRiskFactorInput().normalizedRisk());
    }

    @Test
    void reportsUnavailableWithoutSynthesizingAValueWhenTheDistributionIsInvalid() {
        var service = new TimeOfDayRiskService(Clock.fixed(Instant.parse("2026-08-24T13:00:00Z"), IST),
                new MorthTimeOfDayDistribution("risk/test-time-of-day-not-tiling.csv"));

        NormalizedRiskFeature feature = service.currentRisk();

        assertFalse(feature.available());
        assertNull(feature.value());
        // The band is still reported honestly even though no published value backs it.
        assertEquals(new TimeOfDayBand(18, 21), service.currentBand());
    }

    @Test
    void productionConstructorUsesTheRealCurrentTimeInMorthsOwnZone() {
        Instant before = Instant.now();
        NormalizedRiskFeature feature = new TimeOfDayRiskService(distribution).currentRisk();
        Instant after = Instant.now();

        assertTrue(feature.available());
        // Bracket the call so a band boundary crossed mid-test cannot make this flaky.
        assertTrue(java.util.stream.Stream.of(before, after)
                .map(instant -> distribution.intervalAtHour(instant.atZone(IST).getHour()).orElseThrow())
                .anyMatch(interval -> interval.normalizedRisk().equals(feature.value())),
                "the production clock must select the interval for the real current hour in India");
    }
}
