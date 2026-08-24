/**
 * Weather risk factor: a live weather observation resolved into a MoRTH-published severity.
 *
 * <p>The factor is built from two independent real sources, deliberately kept separate:
 *
 * <ul>
 *   <li><strong>The observation</strong> - what the weather actually is right now at the
 *       tourist's coordinates - comes from an external provider behind
 *       {@link com.geoshield.risk.weather.WeatherObservationProvider}. Architecture v3.2
 *       Section 35 leaves provider selection architecture-open (Section 61, item 1);
 *       {@link com.geoshield.risk.weather.OpenMeteoWeatherObservationProvider} is the selected
 *       implementation and is the only class that knows any vendor detail.
 *   <li><strong>The risk relationship</strong> - how dangerous that condition is - comes from
 *       {@link com.geoshield.risk.weather.MorthWeatherSeverityTable}, a bundled classpath
 *       transcription of MoRTH Table 3.8. No vendor supplies a risk number.
 * </ul>
 *
 * <p>Nothing here synthesizes weather. If the provider is disabled, unreachable, slow, or
 * returns a code outside the documented mapping, the factor reports explicitly unavailable.
 */
package com.geoshield.risk.weather;
