package com.geoshield.sos.service;

import com.geoshield.common.exception.ConflictException;
import com.geoshield.common.exception.ForbiddenException;
import com.geoshield.common.exception.InvalidStateTransitionException;
import com.geoshield.common.exception.ResourceNotFoundException;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.sos.dto.CreateSosRequest;
import com.geoshield.sos.dto.SosResponse;
import com.geoshield.sos.entity.SosRequest;
import com.geoshield.sos.entity.SosStatus;
import com.geoshield.sos.mapper.SosMapper;
import com.geoshield.sos.repository.SosRequestRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SosServiceImpl implements SosService {
    private static final List<SosStatus> ACTIVE_STATUSES = List.of(
            SosStatus.PENDING,
            SosStatus.ACKNOWLEDGED,
            SosStatus.RESPONDING
    );

    private final SosRequestRepository sosRequestRepository;
    private final IdentityService identityService;
    private final SosMapper sosMapper;
    private final SosTransactionHelper sosTransactionHelper;

    public SosServiceImpl(
            SosRequestRepository sosRequestRepository,
            IdentityService identityService,
            SosMapper sosMapper,
            SosTransactionHelper sosTransactionHelper
    ) {
        this.sosRequestRepository = sosRequestRepository;
        this.identityService = identityService;
        this.sosMapper = sosMapper;
        this.sosTransactionHelper = sosTransactionHelper;
    }

    @Override
    public SosResponse createSos(UUID touristId, CreateSosRequest request) {
        try {
            return sosTransactionHelper.createSosInTransaction(touristId, request);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            try {
                return sosTransactionHelper.resolveExistingSosAfterConflict(request.clientRequestId(), touristId);
            } catch (ResourceNotFoundException notFound) {
                throw e;
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SosResponse> getMyActiveSos(UUID touristId) {
        return sosRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(touristId, ACTIVE_STATUSES)
                .map(sosMapper::toResponse);
    }

    @Override
    @Transactional
    public SosResponse cancelSos(UUID touristId, UUID sosId) {
        SosRequest sosRequest = sosRequestRepository.findById(sosId)
                .orElseThrow(() -> new ResourceNotFoundException("SOS request not found"));

        if (!sosRequest.getUser().getId().equals(touristId)) {
            throw new ForbiddenException("Cannot cancel another user's SOS request");
        }

        if (sosRequest.getStatus() != SosStatus.PENDING && sosRequest.getStatus() != SosStatus.ACKNOWLEDGED) {
            throw new InvalidStateTransitionException("Cannot cancel SOS request in status: " + sosRequest.getStatus());
        }

        sosRequest.setStatus(SosStatus.CANCELLED);
        sosRequest.setCancelledAt(java.time.Instant.now());
        return sosMapper.toResponse(sosRequestRepository.save(sosRequest));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SosResponse> getSosQueue() {
        return sosRequestRepository.findAllByStatusInOrderByCreatedAtDesc(ACTIVE_STATUSES)
                .stream()
                .map(sosMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SosResponse getSosById(UUID sosId) {
        SosRequest sosRequest = sosRequestRepository.findById(sosId)
                .orElseThrow(() -> new ResourceNotFoundException("SOS request not found"));
        return sosMapper.toResponse(sosRequest);
    }

    @Override
    @Transactional
    public SosResponse updateSosStatus(UUID sosId, SosStatus newStatus, UUID responderId) {
        SosRequest sosRequest = sosRequestRepository.findById(sosId)
                .orElseThrow(() -> new ResourceNotFoundException("SOS request not found"));

        validateSosStateTransition(sosRequest.getStatus(), newStatus);
        sosRequest.setStatus(newStatus);
        java.time.Instant now = java.time.Instant.now();
        if (newStatus == SosStatus.ACKNOWLEDGED) {
            sosRequest.setAcknowledgedAt(now);
        } else if (newStatus == SosStatus.RESPONDING) {
            sosRequest.setRespondingAt(now);
        } else if (newStatus == SosStatus.RESOLVED) {
            sosRequest.setResolvedAt(now);
        } else if (newStatus == SosStatus.CANCELLED) {
            sosRequest.setCancelledAt(now);
        }

        if (responderId != null) {
            User responder = identityService.getUserById(responderId);
            sosRequest.setAssignedResponder(responder);
        }
        return sosMapper.toResponse(sosRequestRepository.save(sosRequest));
    }

    @Override
    @Transactional(readOnly = true)
    public long getActiveSosCount() {
        return sosRequestRepository.countByStatusIn(ACTIVE_STATUSES);
    }

    private void validateSosStateTransition(SosStatus currentStatus, SosStatus newStatus) {
        if (currentStatus == newStatus) {
            return;
        }
        boolean valid = switch (currentStatus) {
            case PENDING -> newStatus == SosStatus.ACKNOWLEDGED || newStatus == SosStatus.CANCELLED;
            case ACKNOWLEDGED -> newStatus == SosStatus.RESPONDING || newStatus == SosStatus.CANCELLED;
            case RESPONDING -> newStatus == SosStatus.RESOLVED;
            default -> false;
        };
        if (!valid) {
            throw new InvalidStateTransitionException("Cannot transition SOS status from " + currentStatus + " to " + newStatus);
        }
    }
}
