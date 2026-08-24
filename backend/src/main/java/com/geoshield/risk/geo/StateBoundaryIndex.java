package com.geoshield.risk.geo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads the bundled State/UT boundary resource once at startup and answers
 * point-in-polygon queries against it.
 *
 * <p>The resource is read from the classpath, so the application has no runtime
 * dependency on any local filesystem or absolute path. The file is a derived
 * artifact: reprojected from the Survey of India / NWIC source (EPSG:7755) to
 * WGS84 (EPSG:4326) offline by {@code tools/geodata/preprocess-boundaries.mjs}.
 */
@Component
public class StateBoundaryIndex {
    /** Classpath-relative location. Never an absolute or machine-specific path. */
    static final String RESOURCE_PATH = "geo/india-state-ut-boundaries.geojson";

    private static final Logger log = LoggerFactory.getLogger(StateBoundaryIndex.class);

    private final List<StateBoundary> boundaries;

    public StateBoundaryIndex() {
        this(RESOURCE_PATH);
    }

    StateBoundaryIndex(String resourcePath) {
        this.boundaries = load(resourcePath);
        log.info("Loaded {} State/UT boundaries ({} vertices) from classpath resource {}",
                boundaries.size(), totalVertexCount(), resourcePath);
    }

    /**
     * Resolves a WGS84 coordinate to its State/UT name, already spelled exactly as
     * the MoRTH {@code geographicUnit}.
     *
     * <p>Returns empty when no polygon contains the point. Boundaries are tested in
     * dataset order, so the result is deterministic for every input.
     *
     * @param longitude WGS84 longitude in degrees (GeoJSON x axis)
     * @param latitude  WGS84 latitude in degrees (GeoJSON y axis)
     */
    public Optional<String> resolveStateName(double longitude, double latitude) {
        for (StateBoundary boundary : boundaries) {
            if (boundary.contains(longitude, latitude)) {
                return Optional.of(boundary.stateName());
            }
        }
        return Optional.empty();
    }

    /** Number of State/UT boundaries loaded. */
    public int stateCount() {
        return boundaries.size();
    }

    /** All loaded State/UT names, in dataset order. */
    public List<String> stateNames() {
        return boundaries.stream().map(StateBoundary::stateName).toList();
    }

    int totalVertexCount() {
        int total = 0;
        for (StateBoundary boundary : boundaries) {
            total += boundary.vertexCount();
        }
        return total;
    }

    private static List<StateBoundary> load(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalStateException("State/UT boundary resource is missing from the classpath: " + resourcePath);
        }
        try (InputStream input = resource.getInputStream()) {
            FeatureCollection collection = new ObjectMapper().readValue(input, FeatureCollection.class);
            if (collection.features() == null || collection.features().isEmpty()) {
                throw new IllegalStateException("State/UT boundary resource contains no features: " + resourcePath);
            }
            List<StateBoundary> loaded = new ArrayList<>(collection.features().size());
            for (Feature feature : collection.features()) {
                loaded.add(toBoundary(feature, resourcePath));
            }
            return List.copyOf(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read State/UT boundary resource: " + resourcePath, exception);
        }
    }

    private static StateBoundary toBoundary(Feature feature, String resourcePath) {
        Properties properties = feature.properties();
        Geometry geometry = feature.geometry();
        if (properties == null || properties.stateName() == null || properties.stateName().isBlank()) {
            throw new IllegalStateException("A feature in " + resourcePath + " has no state_name property.");
        }
        if (geometry == null || !"MultiPolygon".equals(geometry.type()) || geometry.coordinates() == null) {
            throw new IllegalStateException("Feature " + properties.stateName() + " in " + resourcePath
                    + " is not a MultiPolygon.");
        }
        List<BoundaryPolygon> polygons = new ArrayList<>(geometry.coordinates().length);
        for (double[][][] rings : geometry.coordinates()) {
            polygons.add(BoundaryPolygon.of(rings));
        }
        return new StateBoundary(properties.stateName(), properties.stateCode(), polygons);
    }

    // Minimal GeoJSON binding. Unknown members (name, crs, provenance fields) are ignored.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeatureCollection(List<Feature> features) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Feature(Properties properties, Geometry geometry) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Properties(@JsonProperty("state_name") String stateName, @JsonProperty("stcode") String stateCode) { }

    /** MultiPolygon coordinates are indexed [polygon][ring][vertex][longitude, latitude]. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Geometry(String type, double[][][][] coordinates) { }
}
