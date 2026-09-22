package com.geoshield.risk.service;

import com.geoshield.risk.dto.NormalizedRiskFeature;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.weather.MorthWeatherSeverityTable;
import com.geoshield.risk.weather.WeatherCategory;
import com.geoshield.risk.weather.WeatherConditionSeverity;
import com.geoshield.risk.weather.WeatherObservation;
import com.geoshield.risk.weather.WeatherObservationProvider;
import com.geoshield.risk.weather.WeatherObservationResult;
import com.geoshield.risk.weather.WmoWeatherCodeMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Turns a live weather observation into the normalized weather risk feature with full provenance.
 *
 * <p>Composes two real sources and adds nothing of its own: the observation comes from a
 * {@link WeatherObservationProvider}, and the risk relationship comes from the published
 * {@link MorthWeatherSeverityTable}. No provider supplies a risk score and none is invented here.
 *
 * <p>The feature is AVAILABLE only when all four of these hold, matching the approved contract:
 *
 * <ol>
 *   <li>current coordinates exist for the tourist;
 *   <li>the provider successfully returns a current observation;
 *   <li>the reported WMO code is inside the documented {@link WmoWeatherCodeMapper} mapping;
 *   <li>MoRTH publishes a severity for the mapped category.
 * </ol>
 *
 * <p>Any other outcome is reported as explicitly unavailable with the specific reason. Weather is
 * never fabricated and no default condition is ever assumed.
 */
@Service
public class WeatherRiskService {
    public static final String SOURCE_TYPE = "EXTERNAL_API";

    private final WeatherObservationProvider observationProvider;
    private final MorthWeatherSeverityTable severityTable;
    private final Clock clock;

    @Autowired
    public WeatherRiskService(WeatherObservationProvider observationProvider,
            MorthWeatherSeverityTable severityTable,
            @Autowired(required = false) Clock clock) {
        this.observationProvider = observationProvider;
        this.severityTable = severityTable;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public WeatherRiskService(WeatherObservationProvider observationProvider,
            MorthWeatherSeverityTable severityTable) {
        this(observationProvider, severityTable, Clock.systemUTC());
    }

    /**
     * The normalized weather risk at the tourist's current coordinates.
     *
     * @param latitude  current latitude, or {@code null} when no location is stored
     * @param longitude current longitude, or {@code null} when no location is stored
     */
    public NormalizedRiskFeature currentRisk(BigDecimal latitude, BigDecimal longitude) {
        String scope = GeographicProvenanceUtil.toLocationGrid(latitude, longitude);
        if (latitude == null || longitude == null) {
            return unavailable("No current location is available, so no weather observation can be"
                    + " requested for it.", scope);
        }
        if (!severityTable.isLoaded()) {
            return unavailable(severityTable.unavailabilityReason(), scope);
        }
        WeatherObservationResult result = observationProvider.currentWeather(latitude, longitude);
        if (!result.available()) {
            return unavailable(result.unavailabilityReason(), scope);
        }
        WeatherObservation observation = result.observation();
        Optional<WeatherCategory> category = WmoWeatherCodeMapper.categoryOf(observation.wmoCode());
        if (category.isEmpty()) {
            return unavailable(observation.provider() + " reported WMO weather code "
                    + observation.wmoCode() + ", which is outside the documented mapping onto MoRTH's"
                    + " published weather conditions. No category is assumed.", scope);
        }
        Optional<WeatherConditionSeverity> severity = severityTable.severityOf(category.get());
        if (severity.isEmpty()) {
            return unavailable("MoRTH Table 3.8 publishes no accident severity for the \""
                    + category.get().publishedLabel() + "\" condition, so no risk value exists for it.", scope);
        }

        Long freshness = null;
        String normDetails = MorthWeatherSeverityTable.NORMALIZATION;
        if (observation.observedAt() != null) {
            Duration age = Duration.between(observation.observedAt(), clock.instant());
            long ageSec = age.getSeconds();
            if (ageSec < 0) {
                freshness = 0L;
                if (ageSec < -60) {
                    normDetails = normDetails + " [CLOCK_DRIFT_DETECTED]";
                }
            } else {
                freshness = ageSec;
            }
        }

        String rawValue = "WMO " + observation.wmoCode() + " (" + severity.get().label() + ")";
        String sourceId = observation.provider() + "-WMO-" + observation.wmoCode();

        return new NormalizedRiskFeature(
                RiskFactorType.WEATHER,
                severity.get().normalizedRisk(),
                true,
                source(observation),
                explain(observation, severity.get()),
                MorthWeatherSeverityTable.NORMALIZATION,
                null,
                rawValue,
                SOURCE_TYPE,
                sourceId,
                observation.observedAt(),
                freshness,
                scope,
                normDetails);
    }

    /** Names both real sources: who observed the weather, and who published the risk relationship. */
    private String source(WeatherObservation observation) {
        return observation.provider() + " current weather observation; risk mapping from "
                + MorthWeatherSeverityTable.SOURCE;
    }

    /** Records the full provenance chain from raw WMO code through to the derived value. */
    private String explain(WeatherObservation observation, WeatherConditionSeverity severity) {
        return observation.provider() + " reported WMO weather code " + observation.wmoCode() + " at "
                + observation.observedAt() + ", mapped to MoRTH's \"" + severity.label()
                + "\" condition, which recorded " + severity.killedPerHundredAccidents().toPlainString()
                + " persons killed per 100 accidents in " + MorthWeatherSeverityTable.SOURCE_YEAR + " ("
                + severity.killed() + " killed in " + severity.accidents() + " accidents). MoRTH publishes"
                + " no 0-100 weather risk score; this value is derived by relative-maximum rescaling of"
                + " that published severity. National aggregate for "
                + MorthWeatherSeverityTable.SOURCE_YEAR
                + "; not State/UT-specific and not tourist-specific.";
    }

    private NormalizedRiskFeature unavailable(String reason, String scope) {
        String reasonCode = NormalizedRiskFeature.defaultReasonCode(RiskFactorType.WEATHER, reason);
        return NormalizedRiskFeature.unavailable(
                RiskFactorType.WEATHER,
                observationProvider.providerName() + " current weather observation; risk mapping from "
                        + MorthWeatherSeverityTable.SOURCE,
                reason,
                "Requires a real current weather observation mapped onto a published MoRTH"
                        + " weather condition; no score is synthesized.",
                reasonCode,
                null,
                SOURCE_TYPE,
                null,
                null,
                null,
                scope,
                "Requires a real current weather observation mapped onto a published MoRTH weather condition; no score is synthesized.");
    }
}
