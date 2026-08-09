package com.geoshield.incident.service;

import com.geoshield.common.exception.ConflictException;
import com.geoshield.common.exception.ResourceNotFoundException;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.incident.dto.CreateIncidentRequest;
import com.geoshield.incident.dto.IncidentCreationResult;
import com.geoshield.incident.dto.IncidentResponse;
import com.geoshield.incident.entity.Incident;
import com.geoshield.incident.entity.IncidentSourceType;
import com.geoshield.incident.mapper.IncidentMapper;
import com.geoshield.incident.repository.IncidentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentServiceImpl implements IncidentService {
    private static final String INITIAL_STATUS = "REPORTED";

    private final IncidentRepository incidentRepository;
    private final IdentityService identityService;
    private final IncidentMapper incidentMapper;
    private final IncidentIntegrityHasher integrityHasher;

    public IncidentServiceImpl(IncidentRepository incidentRepository, IdentityService identityService,
            IncidentMapper incidentMapper, IncidentIntegrityHasher integrityHasher) {
        this.incidentRepository = incidentRepository;
        this.identityService = identityService;
        this.incidentMapper = incidentMapper;
        this.integrityHasher = integrityHasher;
    }

    @Override
    @Transactional
    public IncidentCreationResult createIncident(UUID reporterId, CreateIncidentRequest request) {
        var existing = incidentRepository.findByReporterIdAndClientRequestId(reporterId, request.clientRequestId());
        if (existing.isPresent()) {
            return new IncidentCreationResult(toVerifiedResponse(existing.get()), false);
        }
        if (incidentRepository.findByClientRequestId(request.clientRequestId()).isPresent()) {
            throw new ConflictException("clientRequestId is already associated with another reporter");
        }

        User reporter = identityService.getUserById(reporterId);
        Incident incident = incidentMapper.toEntity(request);
        String integrityHash = integrityHasher.hash(reporterId, request.incidentType(), request.description(),
                request.latitude(), request.longitude(), request.clientRequestId());
        incident.initialize(reporter, IncidentSourceType.USER_REPORTED, INITIAL_STATUS, integrityHash,
                request.clientRequestId());
        return new IncidentCreationResult(toVerifiedResponse(incidentRepository.save(incident)), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponse> getIncidents(UUID reporterId) {
        return incidentRepository.findAllByReporterIdOrderByCreatedAtDesc(reporterId).stream()
                .map(this::toVerifiedResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentResponse getIncident(UUID reporterId, UUID incidentId) {
        Incident incident = incidentRepository.findByIdAndReporterId(incidentId, reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found"));
        return toVerifiedResponse(incident);
    }

    private IncidentResponse toVerifiedResponse(Incident incident) {
        if (!integrityHasher.matches(incident)) {
            throw new ConflictException("Incident integrity verification failed");
        }
        return incidentMapper.toResponse(incident);
    }
}
