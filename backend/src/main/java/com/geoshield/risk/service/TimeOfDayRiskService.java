package com.geoshield.risk.service;

import com.geoshield.risk.dto.NormalizedRiskFeature;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.dto.TimeOfDayBand;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class TimeOfDayRiskService {
    private final Clock clock;
    public TimeOfDayRiskService() { this(Clock.systemUTC()); }
    TimeOfDayRiskService(Clock clock) { this.clock = clock; }
    public TimeOfDayBand currentBand() { return bandAt(clock.instant()); }
    TimeOfDayBand bandAt(Instant instant) { int start = instant.atZone(java.time.ZoneOffset.UTC).getHour() / 3 * 3; return new TimeOfDayBand(start, start + 2); }
    public NormalizedRiskFeature currentRisk() {
        TimeOfDayBand band = currentBand();
        return NormalizedRiskFeature.unavailable(RiskFactorType.TIME_OF_DAY, "MoRTH time-band distribution",
                "Current UTC time is in the " + band.startHourUtc() + "–" + band.endHourUtc()
                        + " hour band, but no MoRTH 3-hour distribution is imported.",
                "Requires actual imported national 3-hour time-band data; no score is synthesized.");
    }
}
