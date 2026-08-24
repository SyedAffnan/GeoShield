package com.geoshield.risk.dto;

/**
 * A MoRTH 3-hour time-of-day interval.
 *
 * <p>Hours are in Indian local time (IST), matching the reference frame of MoRTH's published
 * times of occurrence. {@code startHour} is inclusive and {@code endHour} is exclusive, so the
 * band is written exactly as Table 7.3 prints it: the last band of the day is 21 to 24, not
 * 21 to 23.
 *
 * @param startHour inclusive start hour in Indian local time, 0-21
 * @param endHour   exclusive end hour in Indian local time, 3-24
 */
public record TimeOfDayBand(int startHour, int endHour) { }
