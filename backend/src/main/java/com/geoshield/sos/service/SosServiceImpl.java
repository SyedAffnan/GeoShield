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

    public SosServiceImpl(SosRequestRepository sosRequestRepository, IdentityService identityService, SosMapper sosMapper) {
        this.sosRequestRepository = sosRequestRepository;
        this.identityService = identityService;
        this.sosMapper = sosMapper;
    }

    @Override
    @Transactional
    public SosResponse createSos(UUID touristId, CreateSosRequest request) {
        var existing = sosRequestRepository.findByUserIdAndClientRequestId(touristId, request.clientRequestId());
        if (existing.isPresent()) {
            return sosMapper.toResponse(existing.get());
        }
        if (sosRequestRepository.findByClientRequestId(request.clientRequestId()).isPresent()) {
            throw new ConflictException("clientRequestId is already associated with another user");
        }

        User tourist = identityService.getUserById(touristId);
        SosRequest sosRequest = new SosRequest(
                tourist,
                request.latitude(),
                request.longitude(),
                SosStatus.PENDING,
                request.clientRequestId()
        );
        return sosMapper.toResponse(sosRequestRepository.save(sosRequest));
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
