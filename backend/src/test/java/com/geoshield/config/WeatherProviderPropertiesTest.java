package com.geoshield.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies the weather provider configuration contract, including what it refuses to accept. */
class WeatherProviderPropertiesTest {

    private WeatherProviderProperties properties(String baseUrl, Duration connect, Duration read) {
        return new WeatherProviderProperties(true, baseUrl, connect, read);
    }

    @Test
    void acceptsAValidConfiguration() {
        WeatherProviderProperties properties = properties("https://api.open-meteo.com",
                Duration.ofSeconds(2), Duration.ofSeconds(3));

        assertTrue(properties.enabled());
        assertEquals("https://api.open-meteo.com", properties.baseUrl());
        assertEquals(Duration.ofSeconds(2), properties.connectTimeout());
        assertEquals(Duration.ofSeconds(3), properties.readTimeout());
    }

    @Test
    void supportsBeingDisabledSoNoOutboundWeatherCallIsEverMade() {
        assertFalse(new WeatherProviderProperties(false, "https://api.open-meteo.com",
                Duration.ofSeconds(2), Duration.ofSeconds(3)).enabled());
    }

    @ParameterizedTest(name = "a blank base URL is rejected: \"{0}\"")
    @ValueSource(strings = {"", "   "})
    void rejectsAMissingEndpoint(String baseUrl) {
        assertThrows(IllegalArgumentException.class,
                () -> properties(baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(3)));
    }

    @Test
    void rejectsANullEndpoint() {
        assertThrows(IllegalArgumentException.class,
                () -> properties(null, Duration.ofSeconds(2), Duration.ofSeconds(3)));
    }

    @Test
    void rejectsNonPositiveOrMissingTimeoutsSoARequestCanNeverHangIndefinitely() {
        String url = "https://api.open-meteo.com";
        assertThrows(IllegalArgumentException.class, () -> properties(url, Duration.ZERO, Duration.ofSeconds(3)));
        assertThrows(IllegalArgumentException.class, () -> properties(url, Duration.ofSeconds(2), Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> properties(url, Duration.ofSeconds(-1), Duration.ofSeconds(3)));
        assertThrows(IllegalArgumentException.class, () -> properties(url, null, Duration.ofSeconds(3)));
        assertThrows(IllegalArgumentException.class, () -> properties(url, Duration.ofSeconds(2), null));
    }

    @Test
    void holdsNoCredentialBecauseTheSelectedProviderNeedsNone() {
        // A guard against a future edit quietly introducing an API key into the record.
        assertTrue(java.util.Arrays.stream(WeatherProviderProperties.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .noneMatch(name -> name.toLowerCase().contains("key")
                        || name.toLowerCase().contains("secret")
                        || name.toLowerCase().contains("token")
                        || name.toLowerCase().contains("password")));
    }
}
