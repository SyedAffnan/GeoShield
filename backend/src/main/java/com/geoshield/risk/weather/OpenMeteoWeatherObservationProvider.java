package com.geoshield.risk.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.geoshield.config.WeatherProviderProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Obtains the current weather from Open-Meteo, the provider selected for Architecture v3.2's
 * architecture-open item 1.
 *
 * <p>Open-Meteo needs no API key or account for this project's usage, so GeoShield stores no
 * weather credential. This is the only class in the codebase aware of any weather vendor.
 *
 * <p><strong>Endpoint.</strong> {@code GET {baseUrl}/v1/forecast} with
 * {@code latitude}, {@code longitude}, {@code current=weather_code}, {@code timezone=UTC}. Only
 * the WMO present-weather code is requested - not temperature, precipitation, humidity, or wind -
 * because the code alone selects the MoRTH category, and requesting variables the feature does not
 * use would transmit and retain more than the factor needs.
 *
 * <p><strong>Location privacy.</strong> Coordinates are rounded to
 * {@value #TRANSMITTED_COORDINATE_SCALE} decimal places (about 1 km) before being sent, because
 * Open-Meteo resolves to a weather grid cell far coarser than that and discards the extra precision
 * anyway. Sharper coordinates would leak the tourist's position to a third party without improving
 * the observation. Coordinates are never written to any log, at any level.
 */
@Component
public class OpenMeteoWeatherObservationProvider implements WeatherObservationProvider {
    /** Recorded in the feature's provenance so the observation's origin is never ambiguous. */
    public static final String PROVIDER_NAME = "Open-Meteo";

    /**
     * Decimal places of coordinate precision actually transmitted. About 1.1 km, which is finer
     * than the provider's own grid resolution.
     */
    static final int TRANSMITTED_COORDINATE_SCALE = 2;

    /** The single provider variable requested. */
    static final String CURRENT_VARIABLE = "weather_code";

    private static final String FORECAST_PATH = "/v1/forecast";
    private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherObservationProvider.class);

    private final RestClient restClient;
    private final boolean enabled;

    /** Production wiring: applies the configured endpoint and timeouts. */
    @Autowired
    public OpenMeteoWeatherObservationProvider(WeatherProviderProperties properties, RestClient.Builder builder) {
        this(properties, builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory(properties))
                .build());
    }

    /** Test wiring: accepts a pre-built client so the transport can be stubbed without a network. */
    OpenMeteoWeatherObservationProvider(WeatherProviderProperties properties, RestClient restClient) {
        this.restClient = restClient;
        this.enabled = properties.enabled();
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public WeatherObservationResult currentWeather(BigDecimal latitude, BigDecimal longitude) {
        if (!enabled) {
            return WeatherObservationResult.unavailable(
                    "The " + PROVIDER_NAME + " weather provider is disabled by configuration.");
        }
        if (latitude == null || longitude == null) {
            return WeatherObservationResult.unavailable(
                    "No current coordinates are available to request weather for.");
        }
        try {
            CurrentWeatherResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(FORECAST_PATH)
                            .queryParam("latitude", transmittable(latitude))
                            .queryParam("longitude", transmittable(longitude))
                            .queryParam("current", CURRENT_VARIABLE)
                            .queryParam("timezone", "UTC")
                            .build())
                    .retrieve()
                    .body(CurrentWeatherResponse.class);
            return interpret(response);
        } catch (Exception exception) {
            // A timeout, DNS failure, 4xx/5xx, or unparseable body are all normal outcomes for an
            // external call. The reason is deliberately generic; the exception message could echo
            // the request URI, which carries the coordinates.
            log.warn("{} weather observation failed ({}); weather risk will report as unavailable.",
                    PROVIDER_NAME, exception.getClass().getSimpleName());
            return WeatherObservationResult.unavailable(
                    "The " + PROVIDER_NAME + " weather provider could not be reached or returned an"
                            + " unusable response.");
        }
    }

    private WeatherObservationResult interpret(CurrentWeatherResponse response) {
        if (response == null || response.current() == null || response.current().weatherCode() == null) {
            return WeatherObservationResult.unavailable(
                    "The " + PROVIDER_NAME + " weather provider returned no current weather code.");
        }
        Instant observedAt = parseObservationTime(response.current().time());
        if (observedAt == null) {
            return WeatherObservationResult.unavailable("The " + PROVIDER_NAME
                    + " weather provider returned an observation time that could not be interpreted.");
        }
        log.debug("{} reported WMO weather code {}", PROVIDER_NAME, response.current().weatherCode());
        return WeatherObservationResult.observed(
                new WeatherObservation(PROVIDER_NAME, response.current().weatherCode(), observedAt));
    }

    /** {@code timezone=UTC} makes {@code current.time} a local-form ISO timestamp already in UTC. */
    private Instant parseObservationTime(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(time.trim()).toInstant(ZoneOffset.UTC);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Deliberately coarsened coordinate, so no more precision leaves the system than is useful. */
    private String transmittable(BigDecimal coordinate) {
        return coordinate.setScale(TRANSMITTED_COORDINATE_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    private static ClientHttpRequestFactory requestFactory(WeatherProviderProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());
        return factory;
    }

    /** Only the fields the feature uses are bound; everything else the provider sends is ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CurrentWeatherResponse(@JsonProperty("current") Current current) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Current(@JsonProperty("time") String time,
                @JsonProperty("weather_code") Integer weatherCode) { }
    }
}
