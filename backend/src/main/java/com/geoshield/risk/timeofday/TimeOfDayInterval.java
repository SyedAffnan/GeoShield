package com.geoshield.risk.timeofday;

import java.math.BigDecimal;

/**
 * One 3-hour time interval row of MoRTH Table 7.3, exactly as published.
 *
 * <p>{@code accidents} and {@code publishedSharePercent} are the printed source values and
 * are never recomputed. {@code normalizedRisk} is derived by the loader and is the value the
 * risk engine consumes.
 *
 * @param startHour             inclusive interval start hour in Indian local time, 0-21
 * @param endHour               exclusive interval end hour in Indian local time, 3-24
 * @param accidents             published accident count for this interval (2024 column)
 * @param publishedSharePercent published percentage share for this interval, kept for audit
 * @param dayNight              the source table's own "Day"/"Night" label for this interval
 * @param normalizedRisk        relative-maximum normalized risk in [0,100]
 */
public record TimeOfDayInterval(int startHour, int endHour, long accidents,
        BigDecimal publishedSharePercent, String dayNight, BigDecimal normalizedRisk) {

    /** Whether an Indian-local-time hour-of-day falls in this interval. */
    public boolean containsHour(int hourOfDay) {
        return hourOfDay >= startHour && hourOfDay < endHour;
    }

    /** The interval as printed in Table 7.3, for example {@code 18:00 to 21:00 hrs}. */
    public String label() {
        return "%02d:00 to %02d:00 hrs".formatted(startHour, endHour);
    }
}
