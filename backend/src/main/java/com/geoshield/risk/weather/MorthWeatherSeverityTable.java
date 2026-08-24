package com.geoshield.risk.weather;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads the verified MoRTH 2024 weather-condition accident table once at startup and answers
 * severity lookups against it.
 *
 * <p>The resource is read from the classpath, so there is no runtime dependency on any local
 * filesystem or absolute path. Published counts are transcribed, never computed; the only derived
 * quantities are {@link WeatherConditionSeverity#killedPerHundredAccidents()} and
 * {@link WeatherConditionSeverity#normalizedRisk()}.
 *
 * <p><strong>Why severity rather than the published share.</strong> Table 3.8's accident column is
 * an exposure distribution: "Sunny / clear" is the largest simply because most travel happens in
 * clear weather. Normalizing it directly would rank clear weather as maximally dangerous. Persons
 * killed per 100 accidents conditions on an accident having occurred, so the unpublished exposure
 * denominator cancels, leaving a signal that runs in the direction the source actually supports.
 *
 * <p><strong>Normalization is architecture-open.</strong> Architecture v3.2, the SRS, and the SDD
 * require a feature to land in the fusion range but do not prescribe how a weather condition maps
 * onto it, and MoRTH publishes no 0-100 weather risk score. The rule applied here is
 * relative-maximum rescaling - character-for-character the convention already approved and
 * implemented for the historical and time-of-day factors.
 *
 * <p>Like the time-of-day distribution and unlike the boundary index, a load or validation failure
 * <strong>does not throw</strong>. The table reports itself unloaded so the risk engine can return
 * an explicitly unavailable weather factor rather than failing the request or synthesizing a value.
 */
@Component
public class MorthWeatherSeverityTable {
    /** Classpath-relative location. Never an absolute or machine-specific path. */
    static final String RESOURCE_PATH = "risk/morth-2024-weather-condition.csv";

    /** Provenance of the loaded table, reported with every feature it produces. */
    public static final String SOURCE =
            "MoRTH Road Accidents in India 2024, Table 3.8 (national, 2024)";

    /** The normalization actually applied, reported with every feature it produces. */
    public static final String NORMALIZATION = "persons killed per 100 accidents for the observed"
            + " weather condition / maximum same-measure published condition × 100";

    /** The year of the transcribed columns. */
    public static final int SOURCE_YEAR = 2024;

    /** Published "Total" row for accidents, used purely as a transcription cross-check. */
    static final long PUBLISHED_TOTAL_ACCIDENTS = 487_707L;

    /** Published "Total" row for persons killed, used purely as a transcription cross-check. */
    static final long PUBLISHED_TOTAL_KILLED = 177_175L;

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int NORMALIZED_SCALE = 8;
    private static final String HEADER_PREFIX = "category";

    private static final Logger log = LoggerFactory.getLogger(MorthWeatherSeverityTable.class);

    private final Map<WeatherCategory, WeatherConditionSeverity> severities;
    private final String unavailabilityReason;

    public MorthWeatherSeverityTable() {
        this(RESOURCE_PATH);
    }

    /**
     * Loads a specific classpath resource instead of the bundled default.
     *
     * <p>Exposed so callers outside this package can exercise the missing- and invalid-resource
     * paths. The path is always classpath-relative, never an absolute filesystem path.
     */
    public MorthWeatherSeverityTable(String resourcePath) {
        Map<WeatherCategory, WeatherConditionSeverity> loaded = Map.of();
        String failure = null;
        try {
            loaded = load(resourcePath);
            log.info("Loaded {} MoRTH {} weather-condition severities from classpath resource {}",
                    loaded.size(), SOURCE_YEAR, resourcePath);
        } catch (IOException | RuntimeException exception) {
            failure = "The MoRTH " + SOURCE_YEAR + " weather-condition severity table could not be loaded: "
                    + exception.getMessage();
            log.error("Weather risk will report as unavailable. {}", failure, exception);
        }
        this.severities = loaded;
        this.unavailabilityReason = failure;
    }

    /** Whether a valid table is loaded. When false, the feature must report unavailable. */
    public boolean isLoaded() {
        return !severities.isEmpty();
    }

    /** Why the table is unusable, or {@code null} when it loaded successfully. */
    public String unavailabilityReason() {
        return unavailabilityReason;
    }

    /**
     * The published severity for a weather category.
     *
     * <p>Empty only when the resource failed to load, so a caller must still treat it as a
     * genuine unavailability rather than assuming a value exists.
     */
    public Optional<WeatherConditionSeverity> severityOf(WeatherCategory category) {
        return Optional.ofNullable(severities.get(category));
    }

    /** All loaded severities, in published Table 3.8 order. */
    public List<WeatherConditionSeverity> severities() {
        return severities.values().stream().toList();
    }

    /** Total accidents across the loaded rows; must equal the published Total row. */
    public long totalAccidents() {
        return severities.values().stream().mapToLong(WeatherConditionSeverity::accidents).sum();
    }

    /** Total persons killed across the loaded rows; must equal the published Total row. */
    public long totalKilled() {
        return severities.values().stream().mapToLong(WeatherConditionSeverity::killed).sum();
    }

    private static Map<WeatherCategory, WeatherConditionSeverity> load(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalStateException("resource is missing from the classpath: " + resourcePath);
        }
        List<Row> rows = readDataRows(resource, resourcePath);
        if (rows.size() != WeatherCategory.values().length) {
            throw new IllegalStateException("expected " + WeatherCategory.values().length
                    + " published weather rows in " + resourcePath + " but found " + rows.size());
        }
        validateEveryPublishedCategoryIsPresent(rows, resourcePath);
        validateTotalsReconcile(rows, resourcePath);

        Row mostSevere = mostSevere(rows);
        Map<WeatherCategory, WeatherConditionSeverity> loaded = new EnumMap<>(WeatherCategory.class);
        for (Row row : rows) {
            loaded.put(row.category(), new WeatherConditionSeverity(row.category(), row.accidents(), row.killed(),
                    killedPerHundredAccidents(row), normalize(row, mostSevere)));
        }
        // Unmodifiable rather than Map.copyOf: an EnumMap iterates in enum-declaration order, which
        // is Table 3.8's own row order, and Map.copyOf would discard that ordering.
        return Collections.unmodifiableMap(loaded);
    }

    /** The derived severity measure: {@code killed / accidents x 100}. Reported, never fused. */
    private static BigDecimal killedPerHundredAccidents(Row row) {
        return BigDecimal.valueOf(row.killed()).multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(row.accidents()), NORMALIZED_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Relative-maximum normalization, matching the convention already approved and implemented for
     * the historical and time-of-day factors: the most severe published condition maps to exactly
     * 100.
     *
     * <p>Algebraically {@code (k/a) / (kMax/aMax) x 100}, evaluated as a single division of
     * integer products so there is exactly one rounding step. Rounding the two severities first
     * and dividing afterwards would compound rounding error and make the result depend on an
     * intermediate scale.
     */
    private static BigDecimal normalize(Row row, Row mostSevere) {
        BigDecimal numerator = BigDecimal.valueOf(row.killed())
                .multiply(BigDecimal.valueOf(mostSevere.accidents()))
                .multiply(ONE_HUNDRED);
        BigDecimal denominator = BigDecimal.valueOf(row.accidents())
                .multiply(BigDecimal.valueOf(mostSevere.killed()));
        return numerator.divide(denominator, NORMALIZED_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Picks the row with the largest {@code killed / accidents} using exact cross-multiplication,
     * so the choice of denominator never depends on a rounded intermediate value.
     */
    private static Row mostSevere(List<Row> rows) {
        Row mostSevere = rows.get(0);
        for (Row row : rows) {
            // row.killed/row.accidents > mostSevere.killed/mostSevere.accidents, without dividing.
            if (row.killed() * mostSevere.accidents() > mostSevere.killed() * row.accidents()) {
                mostSevere = row;
            }
        }
        return mostSevere;
    }

    private static List<Row> readDataRows(ClassPathResource resource, String resourcePath) throws IOException {
        List<Row> rows = new ArrayList<>();
        try (InputStream input = resource.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                // Skip provenance comments, blank lines, and the column header.
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(HEADER_PREFIX)) {
                    continue;
                }
                rows.add(toRow(trimmed.split(",", -1), resourcePath));
            }
        }
        return rows;
    }

    private static Row toRow(String[] columns, String resourcePath) {
        if (columns.length != 3) {
            throw new IllegalStateException("a row in " + resourcePath + " has " + columns.length
                    + " columns instead of 3");
        }
        String label = columns[0].trim();
        WeatherCategory category = WeatherCategory.fromPublishedLabel(label)
                .orElseThrow(() -> new IllegalStateException("row \"" + label + "\" in " + resourcePath
                        + " is not a published MoRTH Table 3.8 weather condition"));
        long accidents = Long.parseLong(columns[1].trim());
        long killed = Long.parseLong(columns[2].trim());
        if (accidents <= 0 || killed <= 0) {
            throw new IllegalStateException("condition \"" + label + "\" in " + resourcePath
                    + " has a non-positive published count");
        }
        if (killed > accidents) {
            throw new IllegalStateException("condition \"" + label + "\" in " + resourcePath
                    + " reports more persons killed than accidents, so the severity ratio would exceed 1");
        }
        return new Row(category, accidents, killed);
    }

    private static void validateEveryPublishedCategoryIsPresent(List<Row> rows, String resourcePath) {
        for (WeatherCategory category : WeatherCategory.values()) {
            if (rows.stream().noneMatch(row -> row.category() == category)) {
                throw new IllegalStateException(resourcePath + " is missing the published \""
                        + category.publishedLabel() + "\" condition");
            }
        }
    }

    /** Guards the transcription: the rows must add up to Table 3.8's own printed Total row. */
    private static void validateTotalsReconcile(List<Row> rows, String resourcePath) {
        long accidents = rows.stream().mapToLong(Row::accidents).sum();
        long killed = rows.stream().mapToLong(Row::killed).sum();
        if (accidents != PUBLISHED_TOTAL_ACCIDENTS) {
            throw new IllegalStateException("accident counts in " + resourcePath + " sum to " + accidents
                    + " instead of the published Total of " + PUBLISHED_TOTAL_ACCIDENTS);
        }
        if (killed != PUBLISHED_TOTAL_KILLED) {
            throw new IllegalStateException("persons-killed counts in " + resourcePath + " sum to " + killed
                    + " instead of the published Total of " + PUBLISHED_TOTAL_KILLED);
        }
    }

    private record Row(WeatherCategory category, long accidents, long killed) { }
}
