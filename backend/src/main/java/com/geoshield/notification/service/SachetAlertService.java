package com.geoshield.notification.service;

import com.geoshield.common.service.ModuleService;
import com.geoshield.notification.dto.SachetAlertSummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service boundary interface for NDMA SACHET disaster alert lifecycle,
 * CAP XML ingestion, spatial querying, and risk override evaluation.
 */
public interface SachetAlertService extends ModuleService {

    /**
     * Ingests, validates, deduplicates, and persists a CAP 1.2 XML disaster alert broadcast.
     */
    SachetAlertSummary ingestCapXml(String capXml);

    /**
     * Returns all currently unexpired, non-cancelled disaster alerts in the system.
     */
    List<SachetAlertSummary> getActiveAlerts();

    /**
     * Finds the highest-priority active disaster alert intersecting the specified coordinates at the given time.
     */
    Optional<SachetAlertSummary> findApplicableActiveAlert(double latitude, double longitude, Instant now);

    /**
     * Evaluates whether an alert qualifies as an acute civil defense emergency
     * (Severity in EXTREME, SEVERE and Urgency in IMMEDIATE, EXPECTED).
     */
    boolean isQualifyingSevereAlert(SachetAlertSummary alert);
}
