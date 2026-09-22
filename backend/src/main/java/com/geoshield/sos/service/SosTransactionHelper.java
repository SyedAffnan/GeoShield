package com.geoshield.sos.service;

import com.geoshield.common.exception.ConflictException;
import com.geoshield.common.exception.ResourceNotFoundException;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.repository.UserRepository;
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
public class SosTransactionHelper {

    private static final List<SosStatus> ACTIVE_STATUSES = List.of(
            SosStatus.PENDING,
            SosStatus.ACKNOWLEDGED,
            SosStatus.RESPONDING
    );

    private final UserRepository userRepository;
    private final SosRequestRepository sosRequestRepository;
    private final SosMapper sosMapper;

    public SosTransactionHelper(
            UserRepository userRepository,
            SosRequestRepository sosRequestRepository,
            SosMapper sosMapper
    ) {
        this.userRepository = userRepository;
        this.sosRequestRepository = sosRequestRepository;
        this.sosMapper = sosMapper;
    }

    @Transactional
    public SosResponse createSosInTransaction(UUID touristId, CreateSosRequest request) {
        // Step 1 — Acquire pessimistic write lock on the User row (same transaction as insert)
        User user = userRepository.findByIdForUpdate(touristId)
                .orElseThrow(() -> new ResourceNotFoundException("Tourist not found: " + touristId));

        // Step 2 — Idempotency check on clientRequestId
        Optional<SosRequest> existing = sosRequestRepository.findByClientRequestId(request.clientRequestId());
        if (existing.isPresent()) {
            if (existing.get().getUser().getId().equals(touristId)) {
                // Same user + same key -> return existing SOS
                return sosMapper.toResponse(existing.get());
            }
            // Different user + same key -> 409
            throw new ConflictException("clientRequestId is already associated with another user");
        }

        // Step 3 — Active SOS check (serialized by row lock above)
        Optional<SosRequest> activeSos = sosRequestRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(touristId, ACTIVE_STATUSES);
        if (activeSos.isPresent()) {
            // Same user + DIFFERENT key + active SOS exists -> 409
            throw new ConflictException("An active SOS alert already exists for this tourist");
        }

        // Step 4 — Build entity, persist, and flush
        SosRequest sosRequest = new SosRequest(
                user,
                request.latitude(),
                request.longitude(),
                SosStatus.PENDING,
                request.clientRequestId()
        );
        SosRequest saved = sosRequestRepository.saveAndFlush(sosRequest);

        // Step 5 — Map to DTO INSIDE the transaction while Hibernate Session is active
        // user.username, user.fullName, user.phoneNumber are fully accessible here!
        return sosMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SosResponse resolveExistingSosAfterConflict(UUID clientRequestId, UUID touristId) {
        SosRequest existing = sosRequestRepository.findByClientRequestIdWithUser(clientRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("SOS not found: " + clientRequestId));
        if (!existing.getUser().getId().equals(touristId)) {
            throw new ConflictException("clientRequestId is already associated with another user");
        }
        return sosMapper.toResponse(existing);
    }
}
