package com.geoshield.risk.timeofday;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads the verified MoRTH 2024 national 3-hour time-interval accident distribution once at
 * startup and answers band lookups against it.
 *
 * <p>The resource is read from the classpath, so the application has no runtime dependency on
 * any local filesystem or absolute path. Source values are transcribed, never computed: the
 * only derived quantity is {@link TimeOfDayInterval#normalizedRisk()}.
 *
 * <p>Unlike the boundary index, a load or validation failure here <strong>does not throw</strong>.
 * The distribution simply reports itself unloaded so the risk engine can return an explicitly
 * unavailable time-of-day factor instead of failing the request or synthesizing a value.
 */
@Component
public class MorthTimeOfDayDistribution {
    /** Classpath-relative location. Never an absolute or machine-specific path. */
    static final String RESOURCE_PATH = "risk/morth-2024-time-of-day.csv";

    /** Provenance of the loaded distribution, reported with every feature it produces. */
    public static final String SOURCE = "MoRTH Road Accidents in India 2024, Table 7.3 (national, 2024)";

    /** The normalization actually applied, reported with every feature it produces. */
    public static final String NORMALIZATION =
            "interval accident count / maximum interval accident count × 100";

    /** MoRTH records times of occurrence in Indian local time, the single civil zone for India. */
    public static final String SOURCE_ZONE_ID = "Asia/Kolkata";

    private static final int EXPECTED_INTERVALS = 8;
    private static final int INTERVAL_HOURS = 3;
    private static final int NORMALIZED_SCALE = 8;
    private static final String HEADER_PREFIX = "start_hour";

    private static final Logger log = LoggerFactory.getLogger(MorthTimeOfDayDistribution.class);

    private final List<TimeOfDayInterval> intervals;
    private final String unavailabilityReason;

    public MorthTimeOfDayDistribution() {
        this(RESOURCE_PATH);
    }

    /**
     * Loads a specific classpath resource instead of the bundled default.
     *
     * <p>Exposed so callers outside this package can exercise the missing- and invalid-resource
     * paths. The path is always classpath-relative, never an absolute filesystem path.
     */
    public MorthTimeOfDayDistribution(String resourcePath) {
        List<TimeOfDayInterval> loaded = List.of();
        String failure = null;
        try {
            loaded = load(resourcePath);
            log.info("Loaded {} MoRTH 2024 national 3-hour time intervals from classpath resource {}",
                    loaded.size(), resourcePath);
        } catch (IOException | RuntimeException exception) {
            failure = "The MoRTH 2024 national time-of-day distribution could not be loaded: "
                    + exception.getMessage();
            log.error("Time-of-day risk will report as unavailable. {}", failure, exception);
        }
        this.intervals = loaded;
        this.unavailabilityReason = failure;
    }

    /** Whether a valid distribution is loaded. When false, the feature must report unavailable. */
    public boolean isLoaded() {
        return !intervals.isEmpty();
    }

    /** Why the distribution is unusable, or {@code null} when it loaded successfully. */
    public String unavailabilityReason() {
        return unavailabilityReason;
    }

    /**
     * Finds the published interval containing an Indian-local-time hour-of-day.
     *
     * <p>The loaded intervals are validated to cover all 24 hours, so this is empty only when
     * the resource failed to load or the hour is outside 0-23.
     */
    public Optional<TimeOfDayInterval> intervalAtHour(int hourOfDay) {
        return intervals.stream().filter(interval -> interval.containsHour(hourOfDay)).findFirst();
    }

    /** All loaded intervals, ordered by start hour. */
    public List<TimeOfDayInterval> intervals() {
        return intervals;
    }

    /** Total published accidents across the loaded intervals, excluding MoRTH's "Unknown Time" row. */
    public long totalAccidents() {
        return intervals.stream().mapToLong(TimeOfDayInterval::accidents).sum();
    }

    private static List<TimeOfDayInterval> load(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalStateException("resource is missing from the classpath: " + resourcePath);
        }
        List<String[]> rows = readDataRows(resource);
        if (rows.size() != EXPECTED_INTERVALS) {
            throw new IllegalStateException("expected " + EXPECTED_INTERVALS + " interval rows in "
                    + resourcePath + " but found " + rows.size());
        }
        List<Row> parsed = new ArrayList<>(rows.size());
        for (String[] row : rows) {
            parsed.add(toRow(row, resourcePath));
        }
        parsed.sort(Comparator.comparingInt(Row::startHour));
        validateCoverage(parsed, resourcePath);

        long maximum = parsed.stream().mapToLong(Row::accidents).max().orElseThrow();
        List<TimeOfDayInterval> loaded = new ArrayList<>(parsed.size());
        for (Row row : parsed) {
            loaded.add(new TimeOfDayInterval(row.startHour(), row.endHour(), row.accidents(),
                    row.publishedSharePercent(), row.dayNight(), normalize(row.accidents(), maximum)));
        }
        return List.copyOf(loaded);
    }

    /**
     * Relative-maximum normalization, matching the convention already approved and implemented
     * for the historical factor. The busiest published interval maps to exactly 100.
     */
    private static BigDecimal normalize(long accidents, long maximum) {
        return BigDecimal.valueOf(accidents)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(maximum), NORMALIZED_SCALE, RoundingMode.HALF_UP);
    }

    private static List<String[]> readDataRows(ClassPathResource resource) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (InputStream input = resource.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                // Skip provenance comments, blank lines, and the column header.
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(HEADER_PREFIX)) {
                    continue;
                }
                rows.add(trimmed.split(",", -1));
            }
        }
        return rows;
    }

    private static Row toRow(String[] columns, String resourcePath) {
        if (columns.length != 5) {
            throw new IllegalStateException("a row in " + resourcePath + " has " + columns.length
                    + " columns instead of 5");
        }
        int startHour = Integer.parseInt(columns[0].trim());
        int endHour = Integer.parseInt(columns[1].trim());
        long accidents = Long.parseLong(columns[2].trim());
        BigDecimal share = new BigDecimal(columns[3].trim());
        String dayNight = columns[4].trim();
        if (endHour != startHour + INTERVAL_HOURS) {
            throw new IllegalStateException("interval " + startHour + "-" + endHour + " in " + resourcePath
                    + " is not a " + INTERVAL_HOURS + "-hour MoRTH band");
        }
        if (accidents <= 0 || share.signum() <= 0) {
            throw new IllegalStateException("interval " + startHour + "-" + endHour + " in " + resourcePath
                    + " has a non-positive published value");
        }
        if (dayNight.isEmpty()) {
            throw new IllegalStateException("interval " + startHour + "-" + endHour + " in " + resourcePath
                    + " has no Day/Night label");
        }
        return new Row(startHour, endHour, accidents, share, dayNight);
    }

    /** The published bands must tile the full 24-hour clock, leaving no hour unresolvable. */
    private static void validateCoverage(List<Row> sorted, String resourcePath) {
        int expectedStart = 0;
        for (Row row : sorted) {
            if (row.startHour() != expectedStart) {
                throw new IllegalStateException("intervals in " + resourcePath
                        + " do not tile the 24-hour clock: expected a band starting at " + expectedStart
                        + " but found one starting at " + row.startHour());
            }
            expectedStart = row.endHour();
        }
        if (expectedStart != 24) {
            throw new IllegalStateException("intervals in " + resourcePath + " end at hour " + expectedStart
                    + " instead of covering all 24 hours");
        }
    }

    private record Row(int startHour, int endHour, long accidents, BigDecimal publishedSharePercent,
            String dayNight) { }
}
