/**
 * Verified MoRTH national time-of-day accident distribution for the Risk module.
 *
 * <p>Data comes from the bundled classpath resource
 * {@code risk/morth-2024-time-of-day.csv}, transcribed verbatim from Table 7.3
 * ("Number of road accidents by time interval of day"), 2024 column, printed page 98
 * of MoRTH "Road Accidents in India 2024".
 *
 * <p>The distribution is a <strong>national aggregate</strong>. It is neither
 * State/UT-specific nor tourist-specific. Architecture v3.2 Section 29 defines the
 * feature as "MoRTH, national % share by 3-hour band" and Section 50.1 confirms MoRTH
 * publishes time-of-day only as 3-hour bands aggregated nationally per year, so no
 * finer granularity is claimed than the source table actually provides.
 *
 * <p>MoRTH times of occurrence are Indian local time, so bands are resolved in
 * {@code Asia/Kolkata}. There is no dependency on any absolute filesystem path.
 */
package com.geoshield.risk.timeofday;
