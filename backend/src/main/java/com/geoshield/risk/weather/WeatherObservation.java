package com.geoshield.risk.weather;

import java.time.Instant;

/**
 * A single real weather observation, as reported by a provider.
 *
 * <p>Carries no risk number: providers observe weather, they do not score it. The risk
 * relationship comes from {@link MorthWeatherSeverityTable}.
 *
 * <p>Deliberately holds no coordinates. The observation is requested for the tourist's location
 * but the location is not carried onward into logs, explanations, or the risk audit record.
 *
 * @param provider   the provider that supplied the observation, for provenance
 * @param wmoCode    the raw WMO present-weather code, preserved unmapped for provenance
 * @param observedAt the instant the provider states the observation is for
 */
public record WeatherObservation(String provider, int wmoCode, Instant observedAt) {
}
