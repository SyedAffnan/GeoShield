package com.geoshield.risk.service;

import com.geoshield.incident.dto.IncidentResponse;
import com.geoshield.incident.service.IncidentService;
import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.service.LocationService;
import com.geoshield.risk.dto.BaselineRiskCalculationRequest;
import com.geoshield.risk.dto.GeographicResolution;
import com.geoshield.risk.dto.NormalizedRiskFeature;
import com.geoshield.risk.dto.RiskFactorInput;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.dto.RiskFeatureVector;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds only source-supported baseline inputs through module service boundaries. */
@Service
public class RiskContextAssembler {
    private final LocationService locationService;
    private final IncidentService incidentService;
    private final GeographicResolutionService geographicResolutionService;
    private final HistoricalRiskFeatureService historicalRiskFeatureService;
    private final IncidentRiskFeatureService incidentRiskFeatureService;
    private final TimeOfDayRiskService timeOfDayRiskService;
    private final WeatherRiskService weatherRiskService;

    public RiskContextAssembler(LocationService locationService, IncidentService incidentService,
            GeographicResolutionService geographicResolutionService, HistoricalRiskFeatureService historicalRiskFeatureService,
            IncidentRiskFeatureService incidentRiskFeatureService, TimeOfDayRiskService timeOfDayRiskService,
            WeatherRiskService weatherRiskService) {
        this.locationService = locationService;
        this.incidentService = incidentService;
        this.geographicResolutionService = geographicResolutionService;
        this.historicalRiskFeatureService = historicalRiskFeatureService;
        this.incidentRiskFeatureService = incidentRiskFeatureService;
        this.timeOfDayRiskService = timeOfDayRiskService;
        this.weatherRiskService = weatherRiskService;
    }

    @Transactional(readOnly = true)
    public BaselineRiskCalculationRequest assembleForCurrentUser(UUID userId) {
        // Enforces the approved precondition and avoids direct Location repository access.
        LocationResponse location = locationService.getCurrentLocation(userId);
        List<IncidentResponse> userReports = incidentService.getActiveIncidents();
        GeographicResolution resolution = geographicResolutionService.resolve(location.latitude(), location.longitude());
        NormalizedRiskFeature historical = historicalRiskFeatureService.historicalIncidentRisk(resolution);
        NormalizedRiskFeature incidents = incidentRiskFeatureService.userReportRisk(userReports, location.latitude(),
                location.longitude());
        NormalizedRiskFeature timeOfDay = timeOfDayRiskService.currentRisk();
        // The coordinates go no further than the weather service boundary; the feature it returns
        // carries the observed condition, never the location it was observed for.
        NormalizedRiskFeature weather = weatherRiskService.currentRisk(location.latitude(), location.longitude());
        RiskFeatureVector vector = new RiskFeatureVector(List.of(historical, weather, timeOfDay,
                unavailable(RiskFactorType.SERVICE_PROXIMITY, "Emergency services", "No emergency-service proximity data source is configured."),
                incidents, unavailable(RiskFactorType.CONNECTIVITY, "Client connectivity", "No client connectivity input contract is configured."),
                unavailable(RiskFactorType.OTHER_CONTEXT, "Other context", "No other approved contextual signal is available.")));

        return new BaselineRiskCalculationRequest(userId, vector.feature(RiskFactorType.HISTORICAL_INCIDENT).toRiskFactorInput(),
                vector.feature(RiskFactorType.WEATHER).toRiskFactorInput(), vector.feature(RiskFactorType.TIME_OF_DAY).toRiskFactorInput(),
                vector.feature(RiskFactorType.SERVICE_PROXIMITY).toRiskFactorInput(), vector.feature(RiskFactorType.USER_REPORT).toRiskFactorInput(),
                vector.feature(RiskFactorType.CONNECTIVITY).toRiskFactorInput(), vector.feature(RiskFactorType.OTHER_CONTEXT).toRiskFactorInput());
    }

    private NormalizedRiskFeature unavailable(RiskFactorType type, String source, String reason) {
        return NormalizedRiskFeature.unavailable(type, source, reason, "No approved normalization is configured.");
    }
}
