package com.geoshield.risk.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.service.LocationService;
import com.geoshield.risk.dto.GeographicResolution;
import com.geoshield.risk.dto.HistoricalTrendAdvisoryResponse;
import com.geoshield.risk.dto.ModelProvenanceDto;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HistoricalTrendAdvisoryServiceImpl implements HistoricalTrendAdvisoryService {
    private static final Logger log = LoggerFactory.getLogger(HistoricalTrendAdvisoryServiceImpl.class);

    private final LocationService locationService;
    private final GeographicResolutionService geographicResolutionService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String resourcePath;

    private volatile boolean available = false;
    private Map<String, StateAdvisoryRecord> advisoryRecords = Collections.emptyMap();
    private ModelProvenanceDto provenance;

    public record StateAdvisoryRecord(
            String geographicLevel,
            String geographicUnit,
            String parentUnit,
            int targetYear,
            double actualValue,
            double predictedValue,
            String severityMetricUnit
    ) {}

    @Autowired
    public HistoricalTrendAdvisoryServiceImpl(
            LocationService locationService,
            GeographicResolutionService geographicResolutionService,
            ObjectMapper objectMapper,
            @Value("${geoshield.advisory.historical-trend.enabled:true}") boolean enabled,
            @Value("${geoshield.advisory.historical-trend.resource-path:data/historical_trend_evaluation_2024.json}") String resourcePath) {
        this.locationService = locationService;
        this.geographicResolutionService = geographicResolutionService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.resourcePath = resourcePath;
    }

    /** Convenience constructor for testing with mock dependencies. */
    public HistoricalTrendAdvisoryServiceImpl(
            LocationService locationService,
            GeographicResolutionService geographicResolutionService,
            ObjectMapper objectMapper) {
        this(locationService, geographicResolutionService, objectMapper, true, "data/historical_trend_evaluation_2024.json");
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("HistoricalTrendAdvisoryService is disabled by configuration");
            this.available = false;
            return;
        }

        try {
            loadAdvisoryResource(this.resourcePath);
        } catch (Exception e) {
            log.error("Failed to load historical advisory resource at {}: {}; service will fail-soft and report UNAVAILABLE",
                    this.resourcePath, e.getMessage());
            this.available = false;
            this.advisoryRecords = Collections.emptyMap();
            this.provenance = null;
        }
    }

    public synchronized void loadAdvisoryResource(String path) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is == null) {
            throw new IllegalStateException("Resource not found on classpath: " + path);
        }

        try (is) {
            JsonNode root = objectMapper.readTree(is);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Resource root is not a valid JSON object");
            }

            JsonNode recordsNode = root.get("records");
            if (recordsNode == null || !recordsNode.isArray()) {
                throw new IllegalArgumentException("Missing or invalid 'records' array in advisory resource");
            }

            if (recordsNode.size() != 35) {
                throw new IllegalArgumentException("Expected exactly 35 records, found: " + recordsNode.size());
            }

            Map<String, StateAdvisoryRecord> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (JsonNode recNode : recordsNode) {
                String unit = recNode.path("geographicUnit").asText(null);
                if (unit == null || unit.isBlank()) {
                    throw new IllegalArgumentException("Record contains missing or empty geographicUnit");
                }
                if (map.containsKey(unit)) {
                    throw new IllegalArgumentException("Duplicate geographicUnit found: " + unit);
                }

                StateAdvisoryRecord record = new StateAdvisoryRecord(
                        recNode.path("geographicLevel").asText("STATE_UT"),
                        unit,
                        recNode.path("parentUnit").asText("India"),
                        recNode.path("targetYear").asInt(2024),
                        recNode.path("actualValue").asDouble(),
                        recNode.path("predictedValue").asDouble(),
                        recNode.path("severityMetricUnit").asText("fatalities_per_100_accidents")
                );
                map.put(unit, record);
            }

            Map<String, Object> metrics = root.has("evaluationMetrics")
                    ? objectMapper.convertValue(root.get("evaluationMetrics"), new TypeReference<Map<String, Object>>() {})
                    : Collections.emptyMap();

            Map<String, Object> config = root.has("modelConfiguration")
                    ? objectMapper.convertValue(root.get("modelConfiguration"), new TypeReference<Map<String, Object>>() {})
                    : Collections.emptyMap();

            this.provenance = new ModelProvenanceDto(
                    "RF-STATE-EXP-A",
                    root.path("predictionSource").asText("2024_TEMPORAL_HOLDOUT"),
                    root.path("advisoryReleaseVersion").asText("1.0.0-p0"),
                    null,
                    root.path("modelArtifactStatus").asText("UNAVAILABLE_FOR_THIS_TEMPORAL_HOLDOUT"),
                    root.path("datasetIdentifier").asText("geosheild_ml_lagged_features.csv"),
                    root.path("datasetHashSha256").asText("e2eba6a1f3276d9b345827d22ac3dfd1a524107d6539eb23a96aac211aff5167"),
                    root.path("artifactGeneratedAt").asText(null),
                    null,
                    root.path("trainingTimestampStatus").asText("UNAVAILABLE_NOT_RECORDED_IN_ORIGINAL_EXPERIMENT"),
                    root.path("trainingPeriod").asText("2021-2023"),
                    root.path("trainingRows").asInt(107),
                    root.path("holdoutYear").asInt(2024),
                    root.path("evaluationRows").asInt(35),
                    metrics,
                    config,
                    root.path("limitations").asText(null)
            );

            this.advisoryRecords = Collections.unmodifiableMap(map);
            this.available = true;
            log.info("Successfully loaded {} verified retrospective State/UT advisory records from {}", map.size(), path);
        }
    }

    @Override
    public boolean isAvailable() {
        return this.available && !this.advisoryRecords.isEmpty();
    }

    @Override
    public HistoricalTrendAdvisoryResponse getAdvisoryForUser(UUID userId) {
        if (!isAvailable()) {
            return HistoricalTrendAdvisoryResponse.unavailable(
                    "Historical trend advisory is currently unavailable.");
        }

        if (userId == null) {
            return HistoricalTrendAdvisoryResponse.unavailable(
                    "User identity is required to retrieve advisory.");
        }

        LocationResponse location = (locationService != null) ? locationService.getCurrentLocation(userId) : null;
        if (location == null || location.latitude() == null || location.longitude() == null) {
            return HistoricalTrendAdvisoryResponse.unavailable(
                    "Current tourist GPS location is required to determine the regional advisory.");
        }

        if (geographicResolutionService == null) {
            return HistoricalTrendAdvisoryResponse.unavailable(
                    "Geographic resolution service is unavailable.");
        }

        GeographicResolution resolution = geographicResolutionService.resolve(location.latitude(), location.longitude());
        if (resolution == null || !resolution.resolved() || resolution.geographicUnit() == null) {
            return HistoricalTrendAdvisoryResponse.unavailable(
                    "Location coordinates could not be resolved to a supported Indian State/UT.");
        }

        String targetState;
        boolean isDistrictFallback = false;

        if (resolution.geographicLevel() == GeographicLevel.DISTRICT) {
            targetState = (resolution.parentUnit() != null && !resolution.parentUnit().isBlank())
                    ? resolution.parentUnit()
                    : resolution.geographicUnit();
            isDistrictFallback = true;
        } else {
            targetState = resolution.geographicUnit();
        }

        StateAdvisoryRecord record = advisoryRecords.get(targetState);
        if (record == null) {
            return HistoricalTrendAdvisoryResponse.unavailable(
                    "No 2024 temporal-holdout model estimate is available for " + targetState + ".");
        }

        BigDecimal predicted = BigDecimal.valueOf(record.predictedValue()).setScale(2, RoundingMode.HALF_UP);
        String notice;
        if (isDistrictFallback) {
            notice = "This is a STATE_UT model estimate for " + targetState + "; no district-level model is available.";
        } else {
            notice = "Historical road-safety model provides a 2024 temporal-holdout model estimate of "
                    + predicted.toPlainString() + " fatalities per 100 accidents for " + targetState
                    + " based on annual transport accident data.";
        }

        return HistoricalTrendAdvisoryResponse.available(targetState, predicted, notice, this.provenance);
    }
}
