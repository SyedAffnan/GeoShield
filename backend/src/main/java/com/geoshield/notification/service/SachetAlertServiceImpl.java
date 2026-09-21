package com.geoshield.notification.service;

import com.geoshield.common.util.GeoDistanceUtil;
import com.geoshield.notification.dto.SachetAlertSummary;
import com.geoshield.notification.entity.SachetAlert;
import com.geoshield.notification.repository.SachetAlertRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SachetAlertServiceImpl implements SachetAlertService {

    private static final Logger log = LoggerFactory.getLogger(SachetAlertServiceImpl.class);

    private final SachetAlertRepository alertRepository;
    private final SachetCapXmlParser xmlParser;

    public SachetAlertServiceImpl(SachetAlertRepository alertRepository, SachetCapXmlParser xmlParser) {
        this.alertRepository = alertRepository;
        this.xmlParser = xmlParser;
    }

    @Override
    @Transactional
    public SachetAlertSummary ingestCapXml(String capXml) {
        SachetAlert alert = xmlParser.parse(capXml);

        // Handle cancellations
        if ("Cancel".equalsIgnoreCase(alert.getMsgType())) {
            alert.setCancelled(true);
            if (alert.getReferencesIdentifier() != null && !alert.getReferencesIdentifier().isBlank()) {
                cancelReferencedAlerts(alert.getReferencesIdentifier());
            }
        }

        // Handle updates (mark previous referenced alert as cancelled)
        if ("Update".equalsIgnoreCase(alert.getMsgType()) && alert.getReferencesIdentifier() != null
                && !alert.getReferencesIdentifier().isBlank()) {
            cancelReferencedAlerts(alert.getReferencesIdentifier());
        }

        // Check for deduplication (same identifier and sender)
        Optional<SachetAlert> existingOpt = alertRepository.findByIdentifierAndSender(
                alert.getIdentifier(), alert.getSender()
        );

        SachetAlert saved;
        if (existingOpt.isPresent()) {
            SachetAlert existing = existingOpt.get();
            updateExistingEntity(existing, alert);
            saved = alertRepository.save(existing);
            log.info("Updated existing SACHET alert [identifier={}, sender={}]", saved.getIdentifier(), saved.getSender());
        } else {
            saved = alertRepository.save(alert);
            log.info("Ingested new SACHET alert [identifier={}, sender={}, severity={}, event={}]",
                    saved.getIdentifier(), saved.getSender(), saved.getSeverity(), saved.getEvent());
        }

        return SachetAlertSummary.fromEntity(saved);
    }

    private void cancelReferencedAlerts(String references) {
        if (references == null || references.isBlank()) return;
        // Space-delimited list of references: each is "sender,identifier,sent" or "sender,identifier" or "identifier"
        String[] tokens = references.trim().split("\\s+");
        for (String token : tokens) {
            if (token.isBlank()) continue;
            String[] parts = token.split(",");
            String refSender = null;
            String refIdentifier;
            if (parts.length >= 3) {
                // CAP standard: sender,identifier,sent
                refSender = parts[0].trim();
                refIdentifier = parts[1].trim();
            } else if (parts.length == 2) {
                refSender = parts[0].trim();
                refIdentifier = parts[1].trim();
            } else {
                refIdentifier = parts[0].trim();
            }

            boolean resolved = false;
            if (refSender != null && !refSender.isBlank()) {
                Optional<SachetAlert> targetOpt = alertRepository.findByIdentifierAndSender(refIdentifier, refSender);
                if (targetOpt.isPresent()) {
                    SachetAlert target = targetOpt.get();
                    target.setCancelled(true);
                    alertRepository.save(target);
                    log.info("Cancelled referenced SACHET alert [identifier={}, sender={}]", refIdentifier, refSender);
                    resolved = true;
                }
            }
            if (!resolved) {
                List<SachetAlert> targets = alertRepository.findByIdentifier(refIdentifier);
                if (!targets.isEmpty()) {
                    for (SachetAlert target : targets) {
                        target.setCancelled(true);
                        alertRepository.save(target);
                        log.info("Cancelled referenced SACHET alert by identifier [identifier={}, sender={}]",
                                target.getIdentifier(), target.getSender());
                    }
                    resolved = true;
                }
            }
            if (!resolved) {
                log.warn("Referenced SACHET alert not found for cancellation [token={}, identifier={}, sender={}]",
                        token, refIdentifier, refSender);
            }
        }
    }

    private void updateExistingEntity(SachetAlert target, SachetAlert source) {
        target.setSentAt(source.getSentAt());
        target.setStatus(source.getStatus());
        target.setMsgType(source.getMsgType());
        target.setReferencesIdentifier(source.getReferencesIdentifier());
        target.setCategory(source.getCategory());
        target.setEvent(source.getEvent());
        target.setUrgency(source.getUrgency());
        target.setSeverity(source.getSeverity());
        target.setCertainty(source.getCertainty());
        target.setEffectiveAt(source.getEffectiveAt());
        target.setExpiresAt(source.getExpiresAt());
        target.setHeadline(source.getHeadline());
        target.setDescription(source.getDescription());
        target.setInstruction(source.getInstruction());
        target.setAreaDesc(source.getAreaDesc());
        target.setGeometryType(source.getGeometryType());
        target.setGeometryData(source.getGeometryData());
        target.setSynthetic(source.isSynthetic());
        // Monotonic cancellation semantics: Once cancelled, an alert remains cancelled.
        target.setCancelled(target.isCancelled() || source.isCancelled());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SachetAlertSummary> getActiveAlerts() {
        return alertRepository.findActiveAlerts(Instant.now())
                .stream()
                .map(SachetAlertSummary::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SachetAlertSummary> findApplicableActiveAlert(double latitude, double longitude, Instant now) {
        List<SachetAlert> active = alertRepository.findActiveAlerts(now);

        return active.stream()
                .filter(alert -> !alert.isCancelled())
                .filter(alert -> !alert.getEffectiveAt().isAfter(now) && alert.getExpiresAt().isAfter(now))
                .filter(alert -> isCoordinateInside(alert, latitude, longitude))
                .sorted(Comparator
                        .comparingInt((SachetAlert a) -> severityPriority(a.getSeverity()))
                        .thenComparing(SachetAlert::getSentAt, Comparator.reverseOrder()))
                .findFirst()
                .map(SachetAlertSummary::fromEntity);
    }

    @Override
    public boolean isQualifyingSevereAlert(SachetAlertSummary alert) {
        if (alert == null) return false;

        String status = alert.status();
        if (status == null) return false;

        // Disseminable status allow-list:
        // "Actual" is allowed for production alerts.
        // "Test" / "Exercise" are allowed when explicitly marked synthetic fixtures to support dev/test environments.
        // "Draft", "System", and unknown statuses are strictly prohibited from activating disaster overrides.
        boolean isAllowedStatus = "Actual".equalsIgnoreCase(status)
                || (("Test".equalsIgnoreCase(status) || "Exercise".equalsIgnoreCase(status)) && alert.isSynthetic());
        if (!isAllowedStatus) {
            return false;
        }

        boolean severeOrExtreme = "Extreme".equalsIgnoreCase(alert.severity())
                || "Severe".equalsIgnoreCase(alert.severity());
        boolean immediateOrExpected = "Immediate".equalsIgnoreCase(alert.urgency())
                || "Expected".equalsIgnoreCase(alert.urgency());
        return severeOrExtreme && immediateOrExpected;
    }

    private int severityPriority(String severity) {
        if (severity == null) return 4;
        return switch (severity.toUpperCase()) {
            case "EXTREME" -> 1;
            case "SEVERE" -> 2;
            case "MODERATE" -> 3;
            default -> 4;
        };
    }

    private boolean isCoordinateInside(SachetAlert alert, double latitude, double longitude) {
        String type = alert.getGeometryType();
        String data = alert.getGeometryData();
        if (data == null || data.isBlank()) return false;

        try {
            if ("CIRCLE".equalsIgnoreCase(type)) {
                return isInsideCircle(data, latitude, longitude);
            } else if ("POLYGON".equalsIgnoreCase(type)) {
                return isInsidePolygon(data, latitude, longitude);
            } else if ("MULTI".equalsIgnoreCase(type)) {
                // Multi-area: geometries separated by ';'
                String[] items = data.split(";");
                for (String item : items) {
                    String trimmed = item.trim();
                    if (trimmed.startsWith("CIRCLE:")) {
                        if (isInsideCircle(trimmed.substring(7).trim(), latitude, longitude)) {
                            return true;
                        }
                    } else if (trimmed.startsWith("POLYGON:")) {
                        if (isInsidePolygon(trimmed.substring(8).trim(), latitude, longitude)) {
                            return true;
                        }
                    }
                }
                return false;
            }
        } catch (Exception e) {
            log.warn("Failed to evaluate geometry for alert id={}: {}", alert.getIdentifier(), e.getMessage());
        }
        return false;
    }

    private boolean isInsideCircle(String circleData, double latitude, double longitude) {
        // Supports "lat,lon,radiusKm" or "lat,lon radiusKm"
        double centerLat;
        double centerLon;
        double radiusKm;

        String[] spaceTokens = circleData.trim().split("\\s+");
        if (spaceTokens.length == 2) {
            String[] coords = spaceTokens[0].split(",");
            centerLat = Double.parseDouble(coords[0].trim());
            centerLon = Double.parseDouble(coords[1].trim());
            radiusKm = Double.parseDouble(spaceTokens[1].trim());
        } else {
            String[] parts = circleData.split(",");
            if (parts.length < 3) return false;
            centerLat = Double.parseDouble(parts[0].trim());
            centerLon = Double.parseDouble(parts[1].trim());
            radiusKm = Double.parseDouble(parts[2].trim());
        }

        double distanceMeters = GeoDistanceUtil.distanceMeters(latitude, longitude, centerLat, centerLon);
        return distanceMeters <= (radiusKm * 1000.0);
    }

    private boolean isInsidePolygon(String polygonData, double latitude, double longitude) {
        // Format: "lat1,lon1 lat2,lon2 lat3,lon3 ..."
        String[] pairs = polygonData.trim().split("\\s+");
        if (pairs.length < 3) return false;
        List<double[]> points = new ArrayList<>();
        for (String pair : pairs) {
            String[] coords = pair.split(",");
            if (coords.length == 2) {
                points.add(new double[]{
                        Double.parseDouble(coords[0].trim()),
                        Double.parseDouble(coords[1].trim())
                });
            }
        }
        return pointInPolygon(latitude, longitude, points);
    }

    private boolean pointInPolygon(double lat, double lon, List<double[]> points) {
        boolean inside = false;
        int n = points.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double latI = points.get(i)[0];
            double lonI = points.get(i)[1];
            double latJ = points.get(j)[0];
            double lonJ = points.get(j)[1];

            // Point on vertex
            if (Double.compare(lat, latI) == 0 && Double.compare(lon, lonI) == 0) {
                return true;
            }

            boolean intersect = ((latI > lat) != (latJ > lat))
                    && (lon < (lonJ - lonI) * (lat - latI) / (latJ - latI) + lonI);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }
}
