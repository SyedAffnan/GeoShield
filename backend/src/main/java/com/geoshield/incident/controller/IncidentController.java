package com.geoshield.incident.controller;

import com.geoshield.common.api.ApiResponse;
import com.geoshield.incident.dto.CreateIncidentRequest;
import com.geoshield.incident.dto.IncidentCreationResult;
import com.geoshield.incident.dto.IncidentResponse;
import com.geoshield.incident.service.IncidentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents")
@PreAuthorize("hasRole('TOURIST')")
public class IncidentController {
    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<IncidentResponse>> createIncident(Authentication authentication,
            @Valid @RequestBody CreateIncidentRequest request) {
        IncidentCreationResult result = incidentService.createIncident(currentUserId(authentication), request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        String message = result.created() ? "Incident reported" : "Existing incident returned";
        return ResponseEntity.status(status).body(ApiResponse.success(message, result.incident()));
    }

    @GetMapping
    public ApiResponse<List<IncidentResponse>> getIncidents(Authentication authentication) {
        return ApiResponse.success("Incidents retrieved", incidentService.getIncidents(currentUserId(authentication)));
    }

    @GetMapping("/{incidentId}")
    public ApiResponse<IncidentResponse> getIncident(Authentication authentication, @PathVariable UUID incidentId) {
        return ApiResponse.success("Incident retrieved", incidentService.getIncident(currentUserId(authentication), incidentId));
    }

    private UUID currentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID userId) {
            return userId;
        }
        throw new IllegalStateException("Authenticated principal does not contain a user identifier");
    }
}
