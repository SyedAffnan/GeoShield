package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geoshield.risk.dto.NormalizedRiskFeature;
import com.geoshield.risk.dto.RiskFactorInput;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.weather.MorthWeatherSeverityTable;
import com.geoshield.risk.weather.WeatherObservation;
import com.geoshield.risk.weather.WeatherObservationProvider;
import com.geoshield.risk.weather.WeatherObservationResult;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies that a real observation plus the published MoRTH table produce the weather feature, and
 * that every failure path reports explicitly unavailable instead of assuming a condition.
 *
 * <p>The provider is stubbed, so these tests never require the Internet.
 */
class WeatherRiskServiceTest {
    private static final BigDecimal LATITUDE = new BigDecimal("12.9716");
    private static final BigDecimal LONGITUDE = new BigDecimal("77.5946");
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-24T18:45:00Z");

    private final MorthWeatherSeverityTable severityTable = new MorthWeatherSeverityTable();

    private WeatherRiskService serviceObserving(int wmoCode) {
        return new WeatherRiskService(new StubProvider(WeatherObservationResult.observed(
                new WeatherObservation("Open-Meteo", wmoCode, OBSERVED_AT))), severityTable);
    }

    @ParameterizedTest(name = "WMO {0} -> \"{1}\" -> {2}")
    @CsvSource({
        // One representative code per reachable MoRTH condition, with its published severity.
        "0,  Sunny / clear,  76.01868939",
        "3,  Sunny / clear,  76.01868939",
        "61, Rainy,          79.06699039",
        "95, Rainy,          79.06699039",
        "45, Foggy & misty,  98.05556447",
        "48, Foggy & misty,  98.05556447",
        "71, Hail / sleet,   98.07662938",
        "96, Hail / sleet,   98.07662938",
    })
    void makesTheFactorAvailableWithThePublishedSeverityForTheObservedCondition(int wmoCode,
            String publishedLabel, String normalized) {
        NormalizedRiskFeature feature = serviceObserving(wmoCode).currentRisk(LATITUDE, LONGITUDE);

        assertTrue(feature.available());
        assertEquals(RiskFactorType.WEATHER, feature.factor());
        assertEquals(new BigDecimal(normalized), feature.value());
        assertTrue(feature.reason().contains(publishedLabel), feature.reason());
    }

    @Test
    void carriesBothRealSourcesInTheProvenance() {
        NormalizedRiskFeature feature = serviceObserving(45).currentRisk(LATITUDE, LONGITUDE);

        // Who observed the weather, and who published the risk relationship - never conflated.
        assertTrue(feature.source().contains("Open-Meteo current weather observation"), feature.source());
        assertTrue(feature.source().contains("MoRTH Road Accidents in India 2024, Table 3.8"), feature.source());
        assertEquals(MorthWeatherSeverityTable.NORMALIZATION, feature.normalization());
    }

    @Test
    void explainsTheFullChainFromRawWmoCodeToDerivedValue() {
        String explanation = serviceObserving(48).currentRisk(LATITUDE, LONGITUDE).reason();

        assertTrue(explanation.contains("WMO weather code 48"), explanation);
        assertTrue(explanation.contains("2026-08-24T18:45:00Z"), explanation);
        assertTrue(explanation.contains("Foggy & misty"), explanation);
        assertTrue(explanation.contains("15115 killed in 33955 accidents"), explanation);
        assertTrue(explanation.contains("44.51479900 persons killed per 100 accidents"), explanation);
        // The derived nature of the score must be stated, not implied.
        assertTrue(explanation.contains("MoRTH publishes no 0-100 weather risk score"), explanation);
        assertTrue(explanation.contains("not State/UT-specific"), explanation);
        assertTrue(explanation.contains("not tourist-specific"), explanation);
    }

    @Test
    void neverPutsTheTouristsCoordinatesIntoTheFeature() {
        NormalizedRiskFeature feature = serviceObserving(61).currentRisk(LATITUDE, LONGITUDE);

        for (String text : new String[] {feature.source(), feature.reason(), feature.normalization()}) {
            assertFalse(text.contains("12.9716"), text);
            assertFalse(text.contains("77.5946"), text);
        }
    }

    @Test
    void reportsUnavailableWithoutCallingTheProviderWhenNoLocationIsStored() {
        StubProvider provider = new StubProvider(WeatherObservationResult.observed(
                new WeatherObservation("Open-Meteo", 0, OBSERVED_AT)));
        WeatherRiskService service = new WeatherRiskService(provider, severityTable);

        NormalizedRiskFeature withoutLatitude = service.currentRisk(null, LONGITUDE);
        NormalizedRiskFeature withoutLongitude = service.currentRisk(LATITUDE, null);

        assertFalse(withoutLatitude.available());
        assertFalse(withoutLongitude.available());
        assertNull(withoutLatitude.value());
        assertTrue(withoutLatitude.reason().contains("No current location"), withoutLatitude.reason());
        assertTrue(withoutLongitude.reason().contains("No current location"), withoutLongitude.reason());
        assertEquals(0, provider.calls(), "no observation may be requested without a location");
    }

    @Test
    void reportsUnavailableWithTheProvidersOwnReasonWhenTheObservationFails() {
        WeatherRiskService service = new WeatherRiskService(
                new StubProvider(WeatherObservationResult.unavailable(
                        "The Open-Meteo weather provider could not be reached.")), severityTable);

        NormalizedRiskFeature feature = service.currentRisk(LATITUDE, LONGITUDE);

        assertFalse(feature.available());
        assertNull(feature.value());
        assertTrue(feature.reason().contains("could not be reached"), feature.reason());
    }

    @ParameterizedTest(name = "an unmapped WMO code {0} reports unavailable")
    @ValueSource(ints = {4, 44, 49, 79, 97, 500})
    void reportsUnavailableRatherThanApproximatingAnUnmappedWmoCode(int wmoCode) {
        NormalizedRiskFeature feature = serviceObserving(wmoCode).currentRisk(LATITUDE, LONGITUDE);

        assertFalse(feature.available());
        assertNull(feature.value());
        assertTrue(feature.reason().contains("outside the documented mapping"), feature.reason());
        assertTrue(feature.reason().contains("No category is assumed"), feature.reason());
    }

    @Test
    void reportsUnavailableWhenTheMorthTableCannotBeLoaded() {
        WeatherRiskService service = new WeatherRiskService(
                new StubProvider(WeatherObservationResult.observed(
                        new WeatherObservation("Open-Meteo", 61, OBSERVED_AT))),
                new MorthWeatherSeverityTable("risk/no-such-weather-resource.csv"));

        NormalizedRiskFeature feature = service.currentRisk(LATITUDE, LONGITUDE);

        assertFalse(feature.available());
        assertNull(feature.value());
        assertTrue(feature.reason().contains("could not be loaded"), feature.reason());
    }

    @Test
    void doesNotRequestAnObservationWhenTheRiskMappingIsUnusableAnyway() {
        StubProvider provider = new StubProvider(WeatherObservationResult.observed(
                new WeatherObservation("Open-Meteo", 61, OBSERVED_AT)));
        WeatherRiskService service = new WeatherRiskService(provider,
                new MorthWeatherSeverityTable("risk/no-such-weather-resource.csv"));

        service.currentRisk(LATITUDE, LONGITUDE);

        // Nothing is sent to a third party when the result could not be used regardless.
        assertEquals(0, provider.calls());
    }

    @Test
    void everyUnavailableFeatureStillNamesItsSourceAndRefusesToSynthesize() {
        NormalizedRiskFeature feature = new WeatherRiskService(
                new StubProvider(WeatherObservationResult.unavailable("disabled")), severityTable)
                .currentRisk(LATITUDE, LONGITUDE);

        assertNotNull(feature.source());
        assertTrue(feature.source().contains("Open-Meteo"), feature.source());
        assertTrue(feature.normalization().contains("no score is synthesized"), feature.normalization());
        assertFalse(feature.toRiskFactorInput().available());
        assertNull(feature.toRiskFactorInput().normalizedRisk());
    }

    @ParameterizedTest(name = "WMO {0} produces a fusion input the engine accepts")
    @ValueSource(ints = {0, 45, 61, 71, 95, 99})
    void producesAFusionInputInsideTheRequiredZeroToHundredRange(int wmoCode) {
        RiskFactorInput input = serviceObserving(wmoCode).currentRisk(LATITUDE, LONGITUDE).toRiskFactorInput();

        assertTrue(input.available());
        assertNull(input.unavailabilityReason());
        assertTrue(input.normalizedRisk().signum() >= 0);
        assertTrue(input.normalizedRisk().compareTo(new BigDecimal("100")) <= 0);
    }

    @Test
    void returnsTheSameFeatureForTheSameObservation() {
        WeatherRiskService service = serviceObserving(63);

        assertEquals(service.currentRisk(LATITUDE, LONGITUDE), service.currentRisk(LATITUDE, LONGITUDE));
    }

    @Test
    void scoresAdverseWeatherAboveClearWeatherDespiteClearWeathersLargerAccidentShare() {
        BigDecimal clear = serviceObserving(0).currentRisk(LATITUDE, LONGITUDE).value();
        BigDecimal rain = serviceObserving(61).currentRisk(LATITUDE, LONGITUDE).value();
        BigDecimal fog = serviceObserving(45).currentRisk(LATITUDE, LONGITUDE).value();
        BigDecimal hail = serviceObserving(96).currentRisk(LATITUDE, LONGITUDE).value();

        assertTrue(clear.compareTo(rain) < 0);
        assertTrue(rain.compareTo(fog) < 0);
        assertTrue(fog.compareTo(hail) < 0);
    }

    /** A provider stub that records how often it was asked, so needless calls are caught. */
    private static final class StubProvider implements WeatherObservationProvider {
        private final WeatherObservationResult result;
        private int calls;

        private StubProvider(WeatherObservationResult result) {
            this.result = result;
        }

        @Override
        public WeatherObservationResult currentWeather(BigDecimal latitude, BigDecimal longitude) {
            calls++;
            return result;
        }

        @Override
        public String providerName() {
            return "Open-Meteo";
        }

        private int calls() {
            return calls;
        }
    }
}
