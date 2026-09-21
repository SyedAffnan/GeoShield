package com.geoshield.notification.controller;

import com.geoshield.common.api.ApiResponse;
import com.geoshield.notification.dto.SachetAlertSummary;
import com.geoshield.notification.service.SachetAlertService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for NDMA SACHET CAP 1.2 disaster alert ingestion and active alert query.
 */
@RestController
@RequestMapping("/api/v1/alerts/sachet")
public class SachetAlertController {

    private final SachetAlertService alertService;

    public SachetAlertController(SachetAlertService alertService) {
        this.alertService = alertService;
    }

    private static final int MAX_CAP_XML_BYTES = 512 * 1024; // 512 KB

    /**
     * Ingests an NDMA SACHET CAP 1.2 XML disaster alert broadcast.
     * Strictly restricted to system administrators.
     */
    @PostMapping(consumes = {"application/xml", "text/xml", "text/plain"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SachetAlertSummary>> ingestAlert(@RequestBody String capXml) {
        if (capXml != null && capXml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_CAP_XML_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        SachetAlertSummary summary = alertService.ingestCapXml(capXml);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("SACHET CAP 1.2 alert ingested successfully", summary));
    }

    /**
     * Lists all currently active, unexpired disaster alerts.
     */
    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<SachetAlertSummary>> getActiveAlerts() {
        return ApiResponse.success("Active disaster alerts retrieved", alertService.getActiveAlerts());
    }
}
