package com.geoshield.incident.service;

import com.geoshield.common.service.ModuleService;
import com.geoshield.incident.dto.CreateIncidentRequest;
import com.geoshield.incident.dto.IncidentCreationResult;
import com.geoshield.incident.dto.IncidentResponse;
import java.util.List;
import java.util.UUID;

public interface IncidentService extends ModuleService {
    IncidentCreationResult createIncident(UUID reporterId, CreateIncidentRequest request);
    List<IncidentResponse> getIncidents(UUID reporterId);
    IncidentResponse getIncident(UUID reporterId, UUID incidentId);

    // TODO(architecture-open): define the incident status lifecycle and authorized status transitions.
    // TODO(architecture-open): define approved incident amendment, deletion, and photo-storage contracts.
    // TODO(architecture-open): define the Risk module's approved incident-query contract.
}
