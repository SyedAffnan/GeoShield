package com.geoshield.risk.timeofday;

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
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins the bundled resource to MoRTH "Road Accidents in India 2024" Table 7.3, 2024 column,
 * printed page 98, and pins the derived normalization.
 *
 * <p>Every expected number below is the value printed in the source table. None is interpolated.
 */
class MorthTimeOfDayDistributionTest {
    /** The published Table 7.3 total for all 24 hrs, including the separate "Unknown Time" row. */
    private static final long PUBLISHED_TOTAL_24_HRS = 487_707L;

    /** The published "Unknown Time" row, which is a data-completeness bucket, not an interval. */
    private static final long PUBLISHED_UNKNOWN_TIME = 6_118L;

    private final MorthTimeOfDayDistribution distribution = new MorthTimeOfDayDistribution();

    @Test
    void loadsTheEightPublishedThreeHourIntervalsFromTheClasspath() {
        assertTrue(distribution.isLoaded());
        assertNull(distribution.unavailabilityReason());
        assertEquals(8, distribution.intervals().size());
        assertEquals(List.of(0, 3, 6, 9, 12, 15, 18, 21),
                distribution.intervals().stream().map(TimeOfDayInterval::startHour).toList());
    }

    @ParameterizedTest(name = "{0}:00 to {1}:00 hrs -> {2} accidents, {3}% ({4})")
    @CsvSource({
        "0,  3,  25001, 5.1,  Night",
        "3,  6,  23398, 4.8,  Night",
        "6,  9,  49395, 10.1, Day",
        "9,  12, 68287, 14.0, Day",
        "12, 15, 71386, 14.6, Day",
        "15, 18, 85010, 17.4, Day",
        "18, 21, 102897, 21.1, Night",
        "21, 24, 56215, 11.5, Night",
    })
    void carriesTheExactPublishedAccidentCountShareAndDayNightLabel(int startHour, int endHour,
            long accidents, String share, String dayNight) {
        TimeOfDayInterval interval = distribution.intervalAtHour(startHour).orElseThrow();

        assertEquals(startHour, interval.startHour());
        assertEquals(endHour, interval.endHour());
        assertEquals(accidents, interval.accidents());
        assertEquals(0, interval.publishedSharePercent().compareTo(new BigDecimal(share)));
        assertEquals(dayNight, interval.dayNight());
    }

    @ParameterizedTest(name = "{0}:00 to {1}:00 hrs normalizes to {2}")
    @CsvSource({
        "0,  3,  24.29711265",
        "3,  6,  22.73924410",
        "6,  9,  48.00431499",
        "9,  12, 66.36442268",
        "12, 15, 69.37617229",
        "15, 18, 82.61659718",
        "18, 21, 100.00000000",
        "21, 24, 54.63230221",
    })
    void normalizesEachIntervalAgainstThePeakIntervalAtTheExistingScaleOfEight(int startHour, int endHour,
            String normalized) {
        TimeOfDayInterval interval = distribution.intervalAtHour(startHour).orElseThrow();

        assertEquals(endHour, interval.endHour());
        assertEquals(new BigDecimal(normalized), interval.normalizedRisk());
    }

    @Test
    void mapsTheBusiestPublishedIntervalToExactlyOneHundredAndKeepsEveryValueInRange() {
        TimeOfDayInterval peak = distribution.intervals().stream()
                .max((left, right) -> Long.compare(left.accidents(), right.accidents())).orElseThrow();

        // 18:00 to 21:00 hrs is the published maximum at 1,02,897 accidents (21.1%).
        assertEquals(18, peak.startHour());
        assertEquals(0, peak.normalizedRisk().compareTo(new BigDecimal("100")));
        assertTrue(distribution.intervals().stream().allMatch(interval ->
                interval.normalizedRisk().signum() > 0
                        && interval.normalizedRisk().compareTo(new BigDecimal("100")) <= 0));
    }

    @Test
    void reconcilesTheLoadedIntervalsWithThePublishedTwentyFourHourTotal() {
        // Table 7.3: the eight interval rows sum to 4,81,589, and + 6,118 Unknown = 4,87,707.
        assertEquals(481_589L, distribution.totalAccidents());
        assertEquals(PUBLISHED_TOTAL_24_HRS, distribution.totalAccidents() + PUBLISHED_UNKNOWN_TIME);
    }

    @Test
    void excludesThePublishedUnknownTimeBucketBecauseItIsNotAClockInterval() {
        assertTrue(distribution.intervals().stream()
                .noneMatch(interval -> interval.accidents() == PUBLISHED_UNKNOWN_TIME));
        // Excluding it cannot alter the denominator, because it is not the maximum.
        assertTrue(distribution.intervals().stream()
                .allMatch(interval -> interval.accidents() > PUBLISHED_UNKNOWN_TIME));
    }

    @ParameterizedTest(name = "hour {0} resolves to a published interval")
    @ValueSource(ints = {0, 1, 2, 3, 5, 6, 8, 9, 11, 12, 14, 15, 17, 18, 20, 21, 22, 23})
    void resolvesEveryHourOfTheClockToExactlyOneInterval(int hourOfDay) {
        assertEquals(1, distribution.intervals().stream()
                .filter(interval -> interval.containsHour(hourOfDay)).count());
        assertTrue(distribution.intervalAtHour(hourOfDay).isPresent());
    }

    @ParameterizedTest(name = "hour {0} is outside the clock and resolves to nothing")
    @ValueSource(ints = {-1, 24, 25})
    void resolvesNoIntervalForAnHourOutsideTheClock(int hourOfDay) {
        assertTrue(distribution.intervalAtHour(hourOfDay).isEmpty());
    }

    @Test
    void labelsIntervalsExactlyAsTheSourceTablePrintsThem() {
        assertEquals("18:00 to 21:00 hrs", distribution.intervalAtHour(19).orElseThrow().label());
        assertEquals("21:00 to 24:00 hrs", distribution.intervalAtHour(23).orElseThrow().label());
        assertEquals("00:00 to 03:00 hrs", distribution.intervalAtHour(0).orElseThrow().label());
    }

    @Test
    void reportsUnloadedRatherThanThrowingWhenTheResourceIsMissing() {
        var missing = new MorthTimeOfDayDistribution("risk/no-such-time-of-day-resource.csv");

        assertFalse(missing.isLoaded());
        assertTrue(missing.intervals().isEmpty());
        assertNotNull(missing.unavailabilityReason());
        assertTrue(missing.unavailabilityReason().contains("could not be loaded"));
    }

    @ParameterizedTest(name = "{0} is rejected without throwing")
    @ValueSource(strings = {
        "risk/test-time-of-day-too-few-rows.csv",
        "risk/test-time-of-day-bad-band.csv",
        "risk/test-time-of-day-not-tiling.csv",
    })
    void reportsUnloadedRatherThanThrowingWhenTheResourceIsInvalid(String resourcePath) {
        var invalid = new MorthTimeOfDayDistribution(resourcePath);

        assertFalse(invalid.isLoaded());
        assertTrue(invalid.intervals().isEmpty());
        assertNotNull(invalid.unavailabilityReason());
    }

    @Test
    void producesIdenticalValuesOnEveryLoadOfTheSameResource() {
        assertEquals(distribution.intervals(), new MorthTimeOfDayDistribution().intervals());
    }
}
