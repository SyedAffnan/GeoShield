package com.geoshield.risk.weather;

import java.math.BigDecimal;

/**
 * The weather-observation service boundary.
 *
 * <p>Architecture v3.2 Section 35 leaves provider selection architecture-open (Section 61,
 * item 1). This interface is that seam: the risk engine depends only on it, so the selected
 * vendor is confined to a single implementation and can be replaced without touching the risk
 * module, the fusion formula, or any weight.
 *
 * <p>Implementations must never throw for a routine failure - an unreachable host, a timeout, a
 * malformed payload, or a disabled provider are all normal outcomes and must be returned as
 * {@link WeatherObservationResult#unavailable(String)}. They must never synthesize an observation,
 * and must not log the coordinates they are given.
 */
public interface WeatherObservationProvider {
    /**
     * The current weather at a coordinate, or an explicit reason why it is unavailable.
     *
     * @param latitude  the tourist's current latitude
     * @param longitude the tourist's current longitude
     */
    WeatherObservationResult currentWeather(BigDecimal latitude, BigDecimal longitude);

    /** The provider name recorded in the feature's provenance. */
    String providerName();
}
