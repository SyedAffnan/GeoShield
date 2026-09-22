package com.geoshield.news.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the Recent Safety Event / News Intelligence module.
 */
@ConfigurationProperties(prefix = "geoshield.news")
public record NewsProperties(
        boolean enabled,
        String provider,
        String apiKey,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int cacheTtlMinutes,
        int cacheMaxSize,
        int lookbackHours,
        int maxResults,
        String language,
        String country
) {
    public NewsProperties {
        if (provider == null || provider.isBlank()) {
            provider = (apiKey != null && !apiKey.isBlank()) ? "gnews" : "mock";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://gnews.io/api/v4";
        }
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
            readTimeout = Duration.ofSeconds(5);
        }
        if (cacheTtlMinutes <= 0) {
            cacheTtlMinutes = 45;
        }
        if (cacheMaxSize <= 0) {
            cacheMaxSize = 200;
        }
        if (lookbackHours <= 0) {
            lookbackHours = 72;
        }
        if (maxResults <= 0) {
            maxResults = 10;
        }
        if (language == null || language.isBlank()) {
            language = "en";
        }
        if (country == null || country.isBlank()) {
            country = "in";
        }
    }
}
