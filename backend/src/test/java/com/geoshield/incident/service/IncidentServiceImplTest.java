package com.geoshield.incident.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IncidentServiceImplTest {
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private IdentityService identityService;
    @Mock
    private User reporter;

    private final IncidentMapper mapper = Mappers.getMapper(IncidentMapper.class);
    private final IncidentIntegrityHasher hasher = new IncidentIntegrityHasher();
    private IncidentServiceImpl service;
    private UUID reporterId;

    @BeforeEach
    void setUp() {
        reporterId = UUID.randomUUID();
        service = new IncidentServiceImpl(incidentRepository, identityService, mapper, hasher);
    }

    @Test
    void createsAndPersistsValidUserReportedIncidentWithIntegrityHash() {
        CreateIncidentRequest request = request();
        when(incidentRepository.findByReporterIdAndClientRequestId(reporterId, request.clientRequestId())).thenReturn(Optional.empty());
        when(incidentRepository.findByClientRequestId(request.clientRequestId())).thenReturn(Optional.empty());
        when(identityService.getUserById(reporterId)).thenReturn(reporter);
        when(reporter.getId()).thenReturn(reporterId);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentCreationResult result = service.createIncident(reporterId, request);

        ArgumentCaptor<Incident> saved = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(saved.capture());
        assertTrue(result.created());
        assertEquals(IncidentSourceType.USER_REPORTED, saved.getValue().getSourceType());
        assertEquals("REPORTED", saved.getValue().getStatus());
        assertEquals(64, saved.getValue().getIntegrityHash().length());
        assertTrue(hasher.matches(saved.getValue()));
    }

    @Test
    void returnsOwnedExistingIncidentForIdempotentReplay() {
        CreateIncidentRequest request = request();
        Incident existing = incident(request);
        when(incidentRepository.findByReporterIdAndClientRequestId(reporterId, request.clientRequestId()))
                .thenReturn(Optional.of(existing));

        IncidentCreationResult result = service.createIncident(reporterId, request);

        assertFalse(result.created());
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void retrievesOnlyAuthenticatedUsersIncidents() {
        Incident owned = incident(request());
        when(incidentRepository.findAllByReporterIdOrderByCreatedAtDesc(reporterId)).thenReturn(List.of(owned));

        List<IncidentResponse> results = service.getIncidents(reporterId);

        assertEquals(1, results.size());
        assertEquals("Road hazard", results.getFirst().incidentType());
        verify(incidentRepository).findAllByReporterIdOrderByCreatedAtDesc(reporterId);
    }

    @Test
    void doesNotExposeAnotherUsersIncident() {
        UUID otherUserId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        when(incidentRepository.findByIdAndReporterId(incidentId, otherUserId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getIncident(otherUserId, incidentId));
        verify(incidentRepository).findByIdAndReporterId(incidentId, otherUserId);
    }

    @Test
    void rejectsIntegrityMismatchOnRead() {
        CreateIncidentRequest request = request();
        Incident incident = incident(request);
        ReflectionTestUtils.setField(incident, "description", "Tampered description");
        UUID incidentId = UUID.randomUUID();
        when(incidentRepository.findByIdAndReporterId(incidentId, reporterId)).thenReturn(Optional.of(incident));

        assertThrows(ConflictException.class, () -> service.getIncident(reporterId, incidentId));
    }

    @Test
    void rejectsClientRequestIdAlreadyOwnedByAnotherReporter() {
        CreateIncidentRequest request = request();
        when(incidentRepository.findByReporterIdAndClientRequestId(reporterId, request.clientRequestId())).thenReturn(Optional.empty());
        when(incidentRepository.findByClientRequestId(request.clientRequestId())).thenReturn(Optional.of(new Incident()));

        assertThrows(ConflictException.class, () -> service.createIncident(reporterId, request));
        verify(identityService, never()).getUserById(any());
    }

    private Incident incident(CreateIncidentRequest request) {
        when(reporter.getId()).thenReturn(reporterId);
        Incident incident = mapper.toEntity(request);
        String hash = hasher.hash(reporterId, request.incidentType(), request.description(), request.latitude(),
                request.longitude(), request.clientRequestId());
        incident.initialize(reporter, IncidentSourceType.USER_REPORTED, "REPORTED", hash, request.clientRequestId());
        return incident;
    }

    private CreateIncidentRequest request() {
        return new CreateIncidentRequest("Road hazard", "Debris on road", new BigDecimal("12.9716"),
                new BigDecimal("77.5946"), UUID.randomUUID());
    }
}
