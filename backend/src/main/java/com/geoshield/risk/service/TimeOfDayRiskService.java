package com.geoshield.risk.service;

import com.geoshield.risk.dto.NormalizedRiskFeature;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.dto.TimeOfDayBand;
import com.geoshield.risk.timeofday.MorthTimeOfDayDistribution;
import com.geoshield.risk.timeofday.TimeOfDayInterval;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Maps the current time to its MoRTH 3-hour interval and normalizes that interval's published
 * national accident count into the time-of-day risk feature with full provenance.
 *
 * <p>The distribution is a national aggregate for India. It is not State/UT-specific and not
 * tourist-specific, matching Architecture v3.2 Section 29's {@code timeIntervalRiskShare}
 * definition ("MoRTH, national % share by 3-hour band").
 *
 * <p>Bands are resolved in {@link MorthTimeOfDayDistribution#SOURCE_ZONE_ID} because MoRTH's
 * times of occurrence are Indian local time. Resolving them in UTC instead would shift every
 * lookup by 5.5 hours and silently report the wrong published interval.
 *
 * <p>In production the {@link Clock} is the real system clock; tests inject a fixed clock rather
 * than changing system time.
 */
@Service
public class TimeOfDayRiskService {
    public static final String SOURCE_TYPE = "TEMPORAL_RULE";
    public static final String GEOGRAPHIC_SCOPE = "NATIONAL";

    private final Clock clock;
    private final MorthTimeOfDayDistribution distribution;

    /** Production wiring: MoRTH's own zone, driven by the real system clock. */
    @Autowired
    public TimeOfDayRiskService(MorthTimeOfDayDistribution distribution) {
        this(Clock.system(ZoneId.of(MorthTimeOfDayDistribution.SOURCE_ZONE_ID)), distribution);
    }

    /** Test wiring: a fixed clock, so system time is never modified. */
    TimeOfDayRiskService(Clock clock, MorthTimeOfDayDistribution distribution) {
        this.clock = clock;
        this.distribution = distribution;
    }

    /** The MoRTH interval containing the current instant, in Indian local time. */
    public TimeOfDayBand currentBand() {
        return bandAt(clock.instant());
    }

    TimeOfDayBand bandAt(Instant instant) {
        int start = sourceHourOf(instant) / 3 * 3;
        return new TimeOfDayBand(start, start + 3);
    }

    /**
     * The normalized time-of-day risk for the current instant.
     */
    public NormalizedRiskFeature currentRisk() {
        return riskAt(clock.instant());
    }

    NormalizedRiskFeature riskAt(Instant instant) {
        TimeOfDayBand band = bandAt(instant);
        if (!distribution.isLoaded()) {
            String reason = distribution.unavailabilityReason();
            String reasonCode = NormalizedRiskFeature.defaultReasonCode(RiskFactorType.TIME_OF_DAY, reason);
            return NormalizedRiskFeature.unavailable(
                    RiskFactorType.TIME_OF_DAY,
                    MorthTimeOfDayDistribution.SOURCE,
                    reason,
                    "Requires the verified MoRTH 3-hour interval distribution; no score is synthesized.",
                    reasonCode,
                    null,
                    SOURCE_TYPE,
                    null,
                    null,
                    null,
                    GEOGRAPHIC_SCOPE,
                    "Requires the verified MoRTH 3-hour interval distribution; no score is synthesized.");
        }
        Optional<TimeOfDayInterval> interval = distribution.intervalAtHour(sourceHourOf(instant));
        if (interval.isEmpty()) {
            String reason = "MoRTH Table 7.3 publishes no time interval covering the " + band.startHour() + "–"
                    + band.endHour() + " hour band in Indian local time.";
            String reasonCode = NormalizedRiskFeature.defaultReasonCode(RiskFactorType.TIME_OF_DAY, reason);
            return NormalizedRiskFeature.unavailable(
                    RiskFactorType.TIME_OF_DAY,
                    MorthTimeOfDayDistribution.SOURCE,
                    reason,
                    "Requires a published MoRTH interval for the current hour; no score is synthesized.",
                    reasonCode,
                    null,
                    SOURCE_TYPE,
                    null,
                    null,
                    null,
                    GEOGRAPHIC_SCOPE,
                    "Requires a published MoRTH interval for the current hour; no score is synthesized.");
        }
        TimeOfDayInterval published = interval.get();
        String rawValue = published.label() + " (" + published.publishedSharePercent().stripTrailingZeros().toPlainString() + "% accident share)";
        String sourceId = "MoRTH-2024-Table-7.3-" + published.label();

        return new NormalizedRiskFeature(
                RiskFactorType.TIME_OF_DAY,
                published.normalizedRisk(),
                true,
                MorthTimeOfDayDistribution.SOURCE,
                explain(published),
                MorthTimeOfDayDistribution.NORMALIZATION,
                null,
                rawValue,
                SOURCE_TYPE,
                sourceId,
                null,
                null,
                GEOGRAPHIC_SCOPE,
                MorthTimeOfDayDistribution.NORMALIZATION);
    }

    /** Names the interval, its published values, and its national, non-tourist-specific scope. */
    private String explain(TimeOfDayInterval published) {
        return "Indian local time falls in MoRTH's " + published.label() + " interval ("
                + published.dayNight() + "), which recorded " + published.accidents() + " road accidents in 2024, "
                + published.publishedSharePercent().stripTrailingZeros().toPlainString()
                + "% of the national total. National aggregate for 2024; not State/UT-specific and not"
                + " tourist-specific.";
    }

    /** The hour-of-day in MoRTH's own reference zone, which is Indian local time. */
    private int sourceHourOf(Instant instant) {
        return instant.atZone(ZoneId.of(MorthTimeOfDayDistribution.SOURCE_ZONE_ID)).getHour();
    }
}
