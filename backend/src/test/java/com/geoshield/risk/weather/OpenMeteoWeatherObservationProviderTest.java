package com.geoshield.risk.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Verifies the Open-Meteo provider against a stubbed transport.
 *
 * <p>No test here touches the network: every response is served by {@link MockRestServiceServer},
 * so the suite passes offline and never depends on a third party being reachable.
 */
class OpenMeteoWeatherObservationProviderTest {
    private static final String BASE_URL = "http://weather.test";
    private static final BigDecimal LATITUDE = new BigDecimal("12.9716");
    private static final BigDecimal LONGITUDE = new BigDecimal("77.5946");

    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private OpenMeteoWeatherObservationProvider provider(boolean enabled) {
        return new OpenMeteoWeatherObservationProvider(
                new WeatherProviderPropertiesFixture(enabled).properties(), builder.build());
    }

    private static String body(String time, String weatherCode) {
        return """
                {"latitude":12.97,"longitude":77.56,"timezone":"GMT",
                 "current_units":{"time":"iso8601","weather_code":"wmo code"},
                 "current":{"time":"%s","interval":900,"weather_code":%s}}
                """.formatted(time, weatherCode);
    }

    @Test
    void returnsTheObservedWmoCodeAndTimeFromASuccessfulResponse() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/v1/forecast")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body("2026-08-24T18:45", "3"), MediaType.APPLICATION_JSON));

        WeatherObservationResult result = provider(true).currentWeather(LATITUDE, LONGITUDE);

        assertTrue(result.available());
        assertNull(result.unavailabilityReason());
        assertEquals("Open-Meteo", result.observation().provider());
        assertEquals(3, result.observation().wmoCode());
        assertEquals(Instant.parse("2026-08-24T18:45:00Z"), result.observation().observedAt());
        server.verify();
    }

    @Test
    void requestsOnlyTheWeatherCodeAndSendsDeliberatelyCoarsenedCoordinates() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/v1/forecast")))
                // 12.9716 and 77.5946 are rounded to about 1 km before leaving the system, which is
                // finer than the provider's own grid but far coarser than the stored fix.
                .andExpect(queryParam("latitude", "12.97"))
                .andExpect(queryParam("longitude", "77.59"))
                .andExpect(queryParam("current", "weather_code"))
                .andExpect(queryParam("timezone", "UTC"))
                .andRespond(withSuccess(body("2026-08-24T18:45", "61"), MediaType.APPLICATION_JSON));

        assertTrue(provider(true).currentWeather(LATITUDE, LONGITUDE).available());
        server.verify();
    }

    @Test
    void makesNoOutboundRequestAtAllWhenTheProviderIsDisabled() {
        WeatherObservationResult result = provider(false).currentWeather(LATITUDE, LONGITUDE);

        assertFalse(result.available());
        assertNull(result.observation());
        assertTrue(result.unavailabilityReason().contains("disabled by configuration"));
        // No expectation was registered, so any request would have failed the test outright.
        server.verify();
    }

    @Test
    void makesNoOutboundRequestWhenNoCoordinatesAreAvailable() {
        WeatherObservationResult withoutLatitude = provider(true).currentWeather(null, LONGITUDE);
        WeatherObservationResult withoutLongitude = provider(true).currentWeather(LATITUDE, null);

        assertFalse(withoutLatitude.available());
        assertFalse(withoutLongitude.available());
        assertTrue(withoutLatitude.unavailabilityReason().contains("No current coordinates"));
        server.verify();
    }

    @Test
    void reportsUnavailableOnAServerError() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL))).andRespond(withServerError());

        WeatherObservationResult result = provider(true).currentWeather(LATITUDE, LONGITUDE);

        assertFalse(result.available());
        assertNull(result.observation());
        assertTrue(result.unavailabilityReason().contains("could not be reached or returned an unusable response"));
    }

    @Test
    void reportsUnavailableOnAClientError() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL)))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertFalse(provider(true).currentWeather(LATITUDE, LONGITUDE).available());
    }

    @Test
    void reportsUnavailableOnAReadTimeout() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL))).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        WeatherObservationResult result = provider(true).currentWeather(LATITUDE, LONGITUDE);

        assertFalse(result.available());
        assertTrue(result.unavailabilityReason().contains("could not be reached"));
    }

    @Test
    void reportsUnavailableOnAConnectionFailure() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL))).andRespond(request -> {
            throw new IOException("Connection refused");
        });

        assertFalse(provider(true).currentWeather(LATITUDE, LONGITUDE).available());
    }

    @Test
    void reportsUnavailableOnAnUnparseableBody() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess("this is not json", MediaType.APPLICATION_JSON));

        assertFalse(provider(true).currentWeather(LATITUDE, LONGITUDE).available());
    }

    @ParameterizedTest(name = "a response missing the weather code is unavailable: {0}")
    @ValueSource(strings = {
        "{}",
        "{\"current\":{}}",
        "{\"current\":{\"time\":\"2026-08-24T18:45\"}}",
        "{\"current\":{\"time\":\"2026-08-24T18:45\",\"weather_code\":null}}",
    })
    void reportsUnavailableWhenTheResponseCarriesNoCurrentWeatherCode(String json) {
        server.expect(requestTo(Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        WeatherObservationResult result = provider(true).currentWeather(LATITUDE, LONGITUDE);

        assertFalse(result.available());
        assertTrue(result.unavailabilityReason().contains("no current weather code"));
    }

    @ParameterizedTest(name = "an uninterpretable observation time is unavailable: {0}")
    @ValueSource(strings = {"not-a-time", "", "2026-13-45T99:99"})
    void reportsUnavailableWhenTheObservationTimeCannotBeInterpreted(String time) {
        server.expect(requestTo(Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess(body(time, "3"), MediaType.APPLICATION_JSON));

        WeatherObservationResult result = provider(true).currentWeather(LATITUDE, LONGITUDE);

        assertFalse(result.available());
        assertTrue(result.unavailabilityReason().contains("observation time"));
    }

    @Test
    void neverLeaksTheTouristsCoordinatesIntoTheUnavailabilityReason() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL))).andRespond(withServerError());

        String reason = provider(true).currentWeather(LATITUDE, LONGITUDE).unavailabilityReason();

        assertNotNull(reason);
        assertFalse(reason.contains("12.9"), reason);
        assertFalse(reason.contains("77.5"), reason);
        assertFalse(reason.contains("latitude"), reason);
    }

    @Test
    void namesItselfAsTheProviderForProvenance() {
        assertEquals("Open-Meteo", provider(true).providerName());
        assertEquals("Open-Meteo", OpenMeteoWeatherObservationProvider.PROVIDER_NAME);
    }

    /** Keeps the properties record construction in one place, including its validation rules. */
    private record WeatherProviderPropertiesFixture(boolean enabled) {
        com.geoshield.config.WeatherProviderProperties properties() {
            return new com.geoshield.config.WeatherProviderProperties(enabled, BASE_URL,
                    Duration.ofSeconds(2), Duration.ofSeconds(3));
        }
    }
}
