package com.geoshield.risk.weather;

/**
 * The outcome of asking a provider for the current weather: either a real observation, or an
 * explicit reason why none is available.
 *
 * <p>Mirrors the {@code NormalizedRiskFeature} and {@code GeographicResolution} contracts already
 * used in the risk module, so an unavailable outcome is a value the caller must handle rather than
 * an exception or a null to be guessed at.
 *
 * @param observation          the real observation, or {@code null} when unavailable
 * @param available            whether an observation was genuinely obtained
 * @param unavailabilityReason why no observation exists, or {@code null} when available
 */
public record WeatherObservationResult(WeatherObservation observation, boolean available,
        String unavailabilityReason) {

    public static WeatherObservationResult observed(WeatherObservation observation) {
        return new WeatherObservationResult(observation, true, null);
    }

    public static WeatherObservationResult unavailable(String reason) {
        return new WeatherObservationResult(null, false, reason);
    }
}
