package com.geoshield.sos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geoshield.common.exception.InvalidStateTransitionException;
import com.geoshield.identity.entity.Role;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.entity.UserRole;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.sos.dto.CreateSosRequest;
import com.geoshield.sos.dto.SosResponse;
import com.geoshield.sos.entity.SosRequest;
import com.geoshield.sos.entity.SosStatus;
import com.geoshield.sos.mapper.SosMapper;
import com.geoshield.sos.repository.SosRequestRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SosServiceImplTest {
    @Mock private SosRequestRepository sosRequestRepository;
    @Mock private IdentityService identityService;
    @Mock private SosMapper sosMapper;
    @Mock private SosTransactionHelper sosTransactionHelper;

    private SosServiceImpl sosService;

    @BeforeEach
    void setUp() {
        sosService = new SosServiceImpl(sosRequestRepository, identityService, sosMapper, sosTransactionHelper);
    }

    @Test
    void createSos_delegatesToTransactionHelper() {
        UUID touristId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();
        CreateSosRequest request = new CreateSosRequest(new BigDecimal("12.9716"), new BigDecimal("77.5946"), clientRequestId);

        SosResponse expected = new SosResponse(
                UUID.randomUUID(), touristId, "tourist1", "Tourist One", "+919876543210",
                request.latitude(), request.longitude(), SosStatus.PENDING, null, clientRequestId, Instant.now()
        );
        when(sosTransactionHelper.createSosInTransaction(touristId, request)).thenReturn(expected);

        SosResponse result = sosService.createSos(touristId, request);

        assertThat(result).isEqualTo(expected);
        verify(sosTransactionHelper).createSosInTransaction(touristId, request);
    }

    @Test
    void createSos_concurrencyRaceCondition_recoversViaHelper() {
        UUID touristId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();
        CreateSosRequest request = new CreateSosRequest(new BigDecimal("12.9716"), new BigDecimal("77.5946"), clientRequestId);

        when(sosTransactionHelper.createSosInTransaction(touristId, request))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        SosResponse expected = new SosResponse(
                UUID.randomUUID(), touristId, "tourist1", "Tourist One", "+919876543210",
                request.latitude(), request.longitude(), SosStatus.PENDING, null, clientRequestId, Instant.now()
        );
        when(sosTransactionHelper.resolveExistingSosAfterConflict(clientRequestId, touristId))
                .thenReturn(expected);

        SosResponse result = sosService.createSos(touristId, request);

        assertThat(result).isEqualTo(expected);
        verify(sosTransactionHelper).resolveExistingSosAfterConflict(clientRequestId, touristId);
    }

    @Test
    void createSos_concurrencyRaceCondition_rethrowsWhenHelperThrowsResourceNotFound() {
        UUID touristId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();
        CreateSosRequest request = new CreateSosRequest(new BigDecimal("12.9716"), new BigDecimal("77.5946"), clientRequestId);

        org.springframework.dao.DataIntegrityViolationException originalException =
                new org.springframework.dao.DataIntegrityViolationException("other constraint violation");
        when(sosTransactionHelper.createSosInTransaction(touristId, request))
                .thenThrow(originalException);

        when(sosTransactionHelper.resolveExistingSosAfterConflict(clientRequestId, touristId))
                .thenThrow(new com.geoshield.common.exception.ResourceNotFoundException("SOS not found"));

        assertThatThrownBy(() -> sosService.createSos(touristId, request))
                .isSameAs(originalException);
    }

    @Test
    void updateSosStatus_whenTransitioningToCancelled_setsCancelledAt() {
        UUID sosId = UUID.randomUUID();
        User tourist = new User("tourist1", "tourist1@example.com", "hash", "Tourist One", "+919876543210", new UserRole(Role.TOURIST));
        SosRequest existing = new SosRequest(tourist, new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.PENDING, UUID.randomUUID());

        when(sosRequestRepository.findById(sosId)).thenReturn(Optional.of(existing));
        when(sosRequestRepository.save(existing)).thenReturn(existing);

        SosResponse expected = new SosResponse(
                sosId, tourist.getId(), "tourist1", "Tourist One", "+919876543210",
                new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.CANCELLED, null, existing.getClientRequestId(), Instant.now()
        );
        when(sosMapper.toResponse(existing)).thenReturn(expected);

        SosResponse result = sosService.updateSosStatus(sosId, SosStatus.CANCELLED, null);

        assertThat(result.status()).isEqualTo(SosStatus.CANCELLED);
        assertThat(existing.getCancelledAt()).isNotNull();
    }

    @Test
    void cancelledAt_isImmutable_whenAlreadySet() {
        User tourist = new User("tourist1", "tourist1@example.com", "hash", "Tourist One", "+919876543210", new UserRole(Role.TOURIST));
        SosRequest request = new SosRequest(tourist, new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.PENDING, UUID.randomUUID());

        Instant originalInstant = Instant.parse("2026-09-21T10:00:00Z");
        request.setCancelledAt(originalInstant);
        assertThat(request.getCancelledAt()).isEqualTo(originalInstant);

        // Attempt to overwrite cancelledAt with a different timestamp
        Instant secondInstant = Instant.parse("2026-09-22T12:00:00Z");
        request.setCancelledAt(secondInstant);

        // Value must remain unchanged (immutable / write-once)
        assertThat(request.getCancelledAt()).isEqualTo(originalInstant);
    }

    @Test
    void updateSosStatusTransitionsFromPendingToAcknowledged() {
        UUID sosId = UUID.randomUUID();
        UUID responderId = UUID.randomUUID();

        User tourist = new User("tourist1", "tourist1@example.com", "hash", "Tourist One", "+919876543210", new UserRole(Role.TOURIST));
        User responder = new User("resp1", "resp1@example.com", "hash", "Responder One", "+919876543211", new UserRole(Role.RESPONDER));

        SosRequest existing = new SosRequest(tourist, new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.PENDING, UUID.randomUUID());
        when(sosRequestRepository.findById(sosId)).thenReturn(Optional.of(existing));
        when(identityService.getUserById(responderId)).thenReturn(responder);
        when(sosRequestRepository.save(existing)).thenReturn(existing);

        SosResponse expected = new SosResponse(
                sosId, tourist.getId(), "tourist1", "Tourist One", "+919876543210",
                new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.ACKNOWLEDGED, responderId, existing.getClientRequestId(), Instant.now()
        );
        when(sosMapper.toResponse(existing)).thenReturn(expected);

        SosResponse result = sosService.updateSosStatus(sosId, SosStatus.ACKNOWLEDGED, responderId);

        assertThat(result.status()).isEqualTo(SosStatus.ACKNOWLEDGED);
        assertThat(existing.getAcknowledgedAt()).isNotNull();
    }

    @Test
    void updateSosStatusTransitionsFromAcknowledgedToResponding() {
        UUID sosId = UUID.randomUUID();
        UUID responderId = UUID.randomUUID();

        User tourist = new User("tourist1", "tourist1@example.com", "hash", "Tourist One", "+919876543210", new UserRole(Role.TOURIST));
        User responder = new User("resp1", "resp1@example.com", "hash", "Responder One", "+919876543211", new UserRole(Role.RESPONDER));

        SosRequest existing = new SosRequest(tourist, new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.ACKNOWLEDGED, UUID.randomUUID());
        Instant ackTime = Instant.now().minusSeconds(60);
        existing.setAcknowledgedAt(ackTime);

        when(sosRequestRepository.findById(sosId)).thenReturn(Optional.of(existing));
        when(identityService.getUserById(responderId)).thenReturn(responder);
        when(sosRequestRepository.save(existing)).thenReturn(existing);

        SosResponse expected = new SosResponse(
                sosId, tourist.getId(), "tourist1", "Tourist One", "+919876543210",
                new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.RESPONDING, responderId, existing.getClientRequestId(), Instant.now()
        );
        when(sosMapper.toResponse(existing)).thenReturn(expected);

        SosResponse result = sosService.updateSosStatus(sosId, SosStatus.RESPONDING, responderId);

        assertThat(result.status()).isEqualTo(SosStatus.RESPONDING);
        assertThat(existing.getAcknowledgedAt()).isEqualTo(ackTime); // immutable
        assertThat(existing.getRespondingAt()).isNotNull();
    }

    @Test
    void updateSosStatusTransitionsFromRespondingToResolved() {
        UUID sosId = UUID.randomUUID();
        UUID responderId = UUID.randomUUID();

        User tourist = new User("tourist1", "tourist1@example.com", "hash", "Tourist One", "+919876543210", new UserRole(Role.TOURIST));
        User responder = new User("resp1", "resp1@example.com", "hash", "Responder One", "+919876543211", new UserRole(Role.RESPONDER));

        SosRequest existing = new SosRequest(tourist, new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.RESPONDING, UUID.randomUUID());

        when(sosRequestRepository.findById(sosId)).thenReturn(Optional.of(existing));
        when(identityService.getUserById(responderId)).thenReturn(responder);
        when(sosRequestRepository.save(existing)).thenReturn(existing);

        SosResponse expected = new SosResponse(
                sosId, tourist.getId(), "tourist1", "Tourist One", "+919876543210",
                new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.RESOLVED, responderId, existing.getClientRequestId(), Instant.now()
        );
        when(sosMapper.toResponse(existing)).thenReturn(expected);

        SosResponse result = sosService.updateSosStatus(sosId, SosStatus.RESOLVED, responderId);

        assertThat(result.status()).isEqualTo(SosStatus.RESOLVED);
        assertThat(existing.getResolvedAt()).isNotNull();
    }

    @Test
    void cancelSosSetsCancelledAtAndStatus() {
        UUID sosId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        User tourist = new User("tourist1", "tourist1@example.com", "hash", "Tourist One", "+919876543210", new UserRole(Role.TOURIST));
        org.springframework.test.util.ReflectionTestUtils.setField(tourist, "id", touristId);

        SosRequest existing = new SosRequest(tourist, new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.PENDING, UUID.randomUUID());
        when(sosRequestRepository.findById(sosId)).thenReturn(Optional.of(existing));
        when(sosRequestRepository.save(existing)).thenReturn(existing);

        SosResponse expected = new SosResponse(
                sosId, touristId, "tourist1", "Tourist One", "+919876543210",
                new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.CANCELLED, null, existing.getClientRequestId(), Instant.now()
        );
        when(sosMapper.toResponse(existing)).thenReturn(expected);

        SosResponse result = sosService.cancelSos(touristId, sosId);

        assertThat(result.status()).isEqualTo(SosStatus.CANCELLED);
        assertThat(existing.getCancelledAt()).isNotNull();
    }

    @Test
    void cancelSosThrowsForbiddenWhenDifferentUserAttemptsCancel() {
        UUID sosId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        User tourist = new User("tourist1", "tourist1@example.com", "hash", "Tourist One", "+919876543210", new UserRole(Role.TOURIST));
        org.springframework.test.util.ReflectionTestUtils.setField(tourist, "id", touristId);

        SosRequest existing = new SosRequest(tourist, new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.PENDING, UUID.randomUUID());
        when(sosRequestRepository.findById(sosId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> sosService.cancelSos(otherUserId, sosId))
                .isInstanceOf(com.geoshield.common.exception.ForbiddenException.class)
                .hasMessageContaining("Cannot cancel another user's SOS request");
    }

    @Test
    void cancelSosThrowsInvalidStateWhenRespondingOrResolved() {
        UUID sosId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        User tourist = new User("tourist1", "tourist1@example.com", "hash", "Tourist One", "+919876543210", new UserRole(Role.TOURIST));
        org.springframework.test.util.ReflectionTestUtils.setField(tourist, "id", touristId);

        SosRequest existing = new SosRequest(tourist, new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.RESPONDING, UUID.randomUUID());
        when(sosRequestRepository.findById(sosId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> sosService.cancelSos(touristId, sosId))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Cannot cancel SOS request in status");
    }

    @Test
    void updateSosStatusRejectsIllegalTransition() {
        UUID sosId = UUID.randomUUID();
        User tourist = new User("tourist1", "tourist1@example.com", "hash", "Tourist One", "+919876543210", new UserRole(Role.TOURIST));
        SosRequest existing = new SosRequest(tourist, new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.PENDING, UUID.randomUUID());

        when(sosRequestRepository.findById(sosId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> sosService.updateSosStatus(sosId, SosStatus.RESOLVED, UUID.randomUUID()))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Cannot transition SOS status");
    }
}
