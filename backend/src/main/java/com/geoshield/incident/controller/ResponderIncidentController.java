package com.geoshield.incident.controller;

import com.geoshield.common.api.ApiResponse;
import com.geoshield.incident.dto.IncidentStatusUpdateRequest;
import com.geoshield.incident.dto.ResponderIncidentResponse;
import com.geoshield.incident.service.IncidentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/responder/incidents")
@PreAuthorize("hasAnyRole('RESPONDER', 'ADMIN')")
public class ResponderIncidentController {
    private final IncidentService incidentService;

    public ResponderIncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping("/queue")
    public ApiResponse<List<ResponderIncidentResponse>> getActiveIncidentQueue() {
        return ApiResponse.success("Incident queue retrieved", incidentService.getActiveIncidentQueue());
    }

    @GetMapping("/{incidentId}")
    public ApiResponse<ResponderIncidentResponse> getIncident(@PathVariable UUID incidentId) {
        return ApiResponse.success("Incident retrieved", incidentService.getIncidentForResponder(incidentId));
    }

    @PatchMapping("/{incidentId}/status")
    public ApiResponse<ResponderIncidentResponse> updateStatus(
            Authentication authentication,
            @PathVariable UUID incidentId,
            @Valid @RequestBody IncidentStatusUpdateRequest request) {
        UUID responderId = currentUserId(authentication);
        return ApiResponse.success("Incident status updated", incidentService.updateIncidentStatus(incidentId, request.status(), responderId));
    }

    private UUID currentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID userId) {
            return userId;
        }
        throw new IllegalStateException("Authenticated principal does not contain a user identifier");
    }
}
