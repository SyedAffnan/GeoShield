package com.geoshield.incident.service;

import com.geoshield.common.service.ModuleService;
import com.geoshield.incident.dto.CreateIncidentRequest;
import com.geoshield.incident.dto.IncidentCreationResult;
import com.geoshield.incident.dto.IncidentResponse;
import java.util.List;
import java.util.UUID;

import com.geoshield.incident.dto.ResponderIncidentResponse;

public interface IncidentService extends ModuleService {
    IncidentCreationResult createIncident(UUID reporterId, CreateIncidentRequest request);
    List<IncidentResponse> getIncidents(UUID reporterId);
    IncidentResponse getIncident(UUID reporterId, UUID incidentId);
    List<ResponderIncidentResponse> getActiveIncidentQueue();
    ResponderIncidentResponse getIncidentForResponder(UUID incidentId);
    ResponderIncidentResponse updateIncidentStatus(UUID incidentId, String newStatus, UUID responderId);
    List<ResponderIncidentResponse> getAllIncidentsForAdmin();
    List<IncidentResponse> getActiveIncidents();
}
