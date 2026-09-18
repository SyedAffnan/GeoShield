package com.geoshield.emergencyservices.service;

import com.geoshield.emergencyservices.dto.EmergencyServiceCenterResponse;
import com.geoshield.emergencyservices.dto.NearestFacilityResult;
import com.geoshield.emergencyservices.entity.CenterType;
import com.geoshield.emergencyservices.entity.EmergencyServiceCenter;
import com.geoshield.emergencyservices.repository.EmergencyServiceCenterRepository;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link EmergencyServicesService}.
 * Automatically seeds emergency facilities from the verified OpenStreetMap reference dataset
 * and provides nearest-facility queries using the Haversine metric.
 */
@Service
@Order(15)
public class EmergencyServicesServiceImpl implements EmergencyServicesService, ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmergencyServicesServiceImpl.class);
    private static final String CSV_RESOURCE_PATH = "emergencyservices/india-emergency-service-centers.csv";
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final EmergencyServiceCenterRepository repository;
    private final List<EmergencyServiceCenter> cachedCenters = new CopyOnWriteArrayList<>();

    public EmergencyServicesServiceImpl(EmergencyServiceCenterRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initializeDataIfEmpty();
        reloadCache();
    }

    @Transactional
    public void initializeDataIfEmpty() {
        long count = repository.count();
        if (count > 0) {
            log.info("Emergency service centers already seeded in database ({} centers found).", count);
            return;
        }

        log.info("Seeding emergency service centers from classpath resource {}...", CSV_RESOURCE_PATH);
        List<EmergencyServiceCenter> centers = loadCentersFromCsv();
        if (!centers.isEmpty()) {
            repository.saveAll(centers);
            log.info("Successfully persisted {} emergency service centers to database.", centers.size());
        } else {
            log.warn("No emergency service centers loaded from CSV resource {}.", CSV_RESOURCE_PATH);
        }
    }

    public synchronized void reloadCache() {
        List<EmergencyServiceCenter> centers = repository.findAll();
        cachedCenters.clear();
        cachedCenters.addAll(centers);
        log.info("Cached {} emergency service centers in memory for proximity queries.", cachedCenters.size());
    }

    @Override
    public Optional<NearestFacilityResult> findNearestFacility(double latitude, double longitude) {
        List<EmergencyServiceCenter> centers = getCenters();
        if (centers.isEmpty()) {
            return Optional.empty();
        }

        EmergencyServiceCenter nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (EmergencyServiceCenter center : centers) {
            if (center.getLatitude() == null || center.getLongitude() == null) {
                continue;
            }
            double dist = haversineDistanceKm(latitude, longitude,
                    center.getLatitude().doubleValue(), center.getLongitude().doubleValue());
            if (dist < minDistance) {
                minDistance = dist;
                nearest = center;
            }
        }

        if (nearest == null) {
            return Optional.empty();
        }
        return Optional.of(new NearestFacilityResult(EmergencyServiceCenterResponse.fromEntity(nearest), minDistance));
    }

    @Override
    public Optional<NearestFacilityResult> findNearestFacilityByType(double latitude, double longitude, CenterType centerType) {
        List<EmergencyServiceCenter> centers = getCenters();
        if (centers.isEmpty() || centerType == null) {
            return Optional.empty();
        }

        EmergencyServiceCenter nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (EmergencyServiceCenter center : centers) {
            if (center.getCenterType() != centerType || center.getLatitude() == null || center.getLongitude() == null) {
                continue;
            }
            double dist = haversineDistanceKm(latitude, longitude,
                    center.getLatitude().doubleValue(), center.getLongitude().doubleValue());
            if (dist < minDistance) {
                minDistance = dist;
                nearest = center;
            }
        }

        if (nearest == null) {
            return Optional.empty();
        }
        return Optional.of(new NearestFacilityResult(EmergencyServiceCenterResponse.fromEntity(nearest), minDistance));
    }

    @Override
    public List<EmergencyServiceCenterResponse> getAllCenters() {
        return getCenters().stream()
                .map(EmergencyServiceCenterResponse::fromEntity)
                .toList();
    }

    @Override
    public long getCenterCount() {
        return getCenters().size();
    }

    private List<EmergencyServiceCenter> getCenters() {
        if (!cachedCenters.isEmpty()) {
            return cachedCenters;
        }
        List<EmergencyServiceCenter> dbCenters = repository.findAll();
        if (!dbCenters.isEmpty()) {
            cachedCenters.addAll(dbCenters);
            return cachedCenters;
        }
        return Collections.emptyList();
    }

    public static double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0) * Math.cos(lat1Rad) * Math.cos(lat2Rad);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_KM * c;
    }

    public List<EmergencyServiceCenter> loadCentersFromCsv() {
        List<EmergencyServiceCenter> centers = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(CSV_RESOURCE_PATH);
        if (!resource.exists()) {
            log.warn("CSV resource {} not found on classpath.", CSV_RESOURCE_PATH);
            return centers;
        }

        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }

                List<String> fields = parseCsvLine(trimmed);
                if (fields.size() < 5) {
                    continue;
                }

                String sourceId = fields.get(0).trim();
                String name = fields.get(1).trim();
                String typeStr = fields.get(2).trim();
                String latStr = fields.get(3).trim();
                String lonStr = fields.get(4).trim();
                String city = fields.size() > 5 ? fields.get(5).trim() : null;
                String state = fields.size() > 6 ? fields.get(6).trim() : null;
                String phone = fields.size() > 7 ? fields.get(7).trim() : null;

                try {
                    CenterType centerType = CenterType.valueOf(typeStr);
                    BigDecimal lat = new BigDecimal(latStr);
                    BigDecimal lon = new BigDecimal(lonStr);
                    EmergencyServiceCenter center = new EmergencyServiceCenter(sourceId, name, centerType, lat, lon, city, state, phone);
                    centers.add(center);
                } catch (Exception e) {
                    log.debug("Skipping invalid CSV line {}: {}", trimmed, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to load emergency centers from CSV {}: {}", CSV_RESOURCE_PATH, e.getMessage(), e);
        }
        return centers;
    }

    public static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields;
    }
}
