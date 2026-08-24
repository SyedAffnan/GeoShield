package com.geoshield.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the selected weather-observation provider.
 *
 * <p>Deliberately has no API-key or credential property. The selected provider (Open-Meteo)
 * requires no account for the project's usage, so GeoShield holds no weather credential to leak,
 * rotate, or accidentally commit.
 *
 * @param enabled        whether the provider may be called at all; when false the weather factor
 *                       reports explicitly unavailable and no outbound request is made
 * @param baseUrl        the provider endpoint, configurable so it can be pointed at a stub
 * @param connectTimeout how long to wait for the connection before reporting unavailable
 * @param readTimeout    how long to wait for the response before reporting unavailable
 */
@ConfigurationProperties(prefix = "geoshield.weather")
public record WeatherProviderProperties(boolean enabled, String baseUrl, Duration connectTimeout,
        Duration readTimeout) {

    public WeatherProviderProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Weather provider base URL must be configured");
        }
        if (isNotPositive(connectTimeout) || isNotPositive(readTimeout)) {
            throw new IllegalArgumentException("Weather provider timeouts must be positive");
        }
    }

    private static boolean isNotPositive(Duration timeout) {
        return timeout == null || timeout.isZero() || timeout.isNegative();
    }
}
