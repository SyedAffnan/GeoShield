package com.geoshield.sos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geoshield.common.exception.ConflictException;
import com.geoshield.common.exception.ResourceNotFoundException;
import com.geoshield.identity.entity.Role;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.entity.UserRole;
import com.geoshield.identity.repository.UserRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SosTransactionHelperTest {

    @Mock private UserRepository userRepository;
    @Mock private SosRequestRepository sosRequestRepository;
    @Mock private SosMapper sosMapper;

    private SosTransactionHelper helper;

    private static final List<SosStatus> ACTIVE_STATUSES = List.of(
            SosStatus.PENDING,
            SosStatus.ACKNOWLEDGED,
            SosStatus.RESPONDING
    );

    @BeforeEach
    void setUp() {
        helper = new SosTransactionHelper(userRepository, sosRequestRepository, sosMapper);
    }

    @Test
    void createSosInTransaction_acquiresPessimisticLockAndPersistsSos() {
        UUID touristId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();
        CreateSosRequest request = new CreateSosRequest(new BigDecimal("12.9716"), new BigDecimal("77.5946"), clientRequestId);

        User tourist = new User("tourist1", "tourist1@example.com", "hash", "Tourist One", "+919876543210", new UserRole(Role.TOURIST));
        ReflectionTestUtils.setField(tourist, "id", touristId);

        when(userRepository.findByIdForUpdate(touristId)).thenReturn(Optional.of(tourist));
        when(sosRequestRepository.findByClientRequestId(clientRequestId)).thenReturn(Optional.empty());
        when(sosRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(touristId, ACTIVE_STATUSES))
                .thenReturn(Optional.empty());

        SosRequest saved = new SosRequest(tourist, request.latitude(), request.longitude(), SosStatus.PENDING, clientRequestId);
        when(sosRequestRepository.saveAndFlush(any(SosRequest.class))).thenReturn(saved);

        SosResponse expected = new SosResponse(
                UUID.randomUUID(), touristId, "tourist1", "Tourist One", "+919876543210",
                request.latitude(), request.longitude(), SosStatus.PENDING, null, clientRequestId, Instant.now()
        );
        when(sosMapper.toResponse(saved)).thenReturn(expected);

        SosResponse result = helper.createSosInTransaction(touristId, request);

        assertThat(result).isEqualTo(expected);
        verify(userRepository).findByIdForUpdate(touristId);
        verify(sosRequestRepository).saveAndFlush(any(SosRequest.class));
    }

    @Test
    void createSosInTransaction_throwsResourceNotFoundWhenUserDoesNotExist() {
        UUID touristId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();
        CreateSosRequest request = new CreateSosRequest(new BigDecimal("12.9716"), new BigDecimal("77.5946"), clientRequestId);

        when(userRepository.findByIdForUpdate(touristId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> helper.createSosInTransaction(touristId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tourist not found");
    }

    @Test
    void createSosInTransaction_idempotentReplayReturnsExistingSosForSameTourist() {
        UUID touristId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();
        CreateSosRequest request = new CreateSosRequest(new BigDecimal("12.9716"), new BigDecimal("77.5946"), clientRequestId);

        User tourist = new User("tourist1", "tourist1@example.com", "hash", "Tourist One", "+919876543210", new UserRole(Role.TOURIST));
        ReflectionTestUtils.setField(tourist, "id", touristId);

        when(userRepository.findByIdForUpdate(touristId)).thenReturn(Optional.of(tourist));

        SosRequest existing = new SosRequest(tourist, request.latitude(), request.longitude(), SosStatus.PENDING, clientRequestId);
        when(sosRequestRepository.findByClientRequestId(clientRequestId)).thenReturn(Optional.of(existing));

        SosResponse expected = new SosResponse(
                UUID.randomUUID(), touristId, "tourist1", "Tourist One", "+919876543210",
                request.latitude(), request.longitude(), SosStatus.PENDING, null, clientRequestId, Instant.now()
        );
        when(sosMapper.toResponse(existing)).thenReturn(expected);

        SosResponse result = helper.createSosInTransaction(touristId, request);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void createSosInTransaction_throwsConflictWhenKeyBelongsToDifferentUser() {
        UUID touristId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();
        CreateSosRequest request = new CreateSosRequest(new BigDecimal("12.9716"), new BigDecimal("77.5946"), clientRequestId);

        User tourist = new User("tourist1", "tourist1@example.com", "hash", "Tourist One", "+919876543210", new UserRole(Role.TOURIST));
        ReflectionTestUtils.setField(tourist, "id", touristId);

        User otherUser = new User("tourist2", "tourist2@example.com", "hash", "Tourist Two", "+919876543211", new UserRole(Role.TOURIST));
        ReflectionTestUtils.setField(otherUser, "id", otherUserId);

        when(userRepository.findByIdForUpdate(touristId)).thenReturn(Optional.of(tourist));

        SosRequest existing = new SosRequest(otherUser, request.latitude(), request.longitude(), SosStatus.PENDING, clientRequestId);
        when(sosRequestRepository.findByClientRequestId(clientRequestId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> helper.createSosInTransaction(touristId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("associated with another user");
    }

    @Test
    void createSosInTransaction_throwsConflictWhenActiveSosAlreadyExistsForSameUser() {
        UUID touristId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();
        UUID existingKey = UUID.randomUUID();
        CreateSosRequest request = new CreateSosRequest(new BigDecimal("12.9716"), new BigDecimal("77.5946"), clientRequestId);

        User tourist = new User("tourist1", "tourist1@example.com", "hash", "Tourist One", "+919876543210", new UserRole(Role.TOURIST));
        ReflectionTestUtils.setField(tourist, "id", touristId);

        when(userRepository.findByIdForUpdate(touristId)).thenReturn(Optional.of(tourist));
        when(sosRequestRepository.findByClientRequestId(clientRequestId)).thenReturn(Optional.empty());

        SosRequest activeSos = new SosRequest(tourist, request.latitude(), request.longitude(), SosStatus.PENDING, existingKey);
        when(sosRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(touristId, ACTIVE_STATUSES))
                .thenReturn(Optional.of(activeSos));

        assertThatThrownBy(() -> helper.createSosInTransaction(touristId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("active SOS alert already exists");
    }

    @Test
    void resolveExistingSosAfterConflict_eagerlyLoadsUserAndReturnsResponse() {
        UUID touristId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();

        User tourist = new User("tourist1", "tourist1@example.com", "hash", "Tourist One", "+919876543210", new UserRole(Role.TOURIST));
        ReflectionTestUtils.setField(tourist, "id", touristId);

        SosRequest existing = new SosRequest(tourist, new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.PENDING, clientRequestId);
        when(sosRequestRepository.findByClientRequestIdWithUser(clientRequestId)).thenReturn(Optional.of(existing));

        SosResponse expected = new SosResponse(
                UUID.randomUUID(), touristId, "tourist1", "Tourist One", "+919876543210",
                new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.PENDING, null, clientRequestId, Instant.now()
        );
        when(sosMapper.toResponse(existing)).thenReturn(expected);

        SosResponse result = helper.resolveExistingSosAfterConflict(clientRequestId, touristId);

        assertThat(result).isEqualTo(expected);
        verify(sosRequestRepository).findByClientRequestIdWithUser(clientRequestId);
    }

    @Test
    void resolveExistingSosAfterConflict_throwsConflictWhenSosBelongsToOtherUser() {
        UUID touristId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();

        User otherUser = new User("tourist2", "tourist2@example.com", "hash", "Tourist Two", "+919876543211", new UserRole(Role.TOURIST));
        ReflectionTestUtils.setField(otherUser, "id", otherUserId);

        SosRequest existing = new SosRequest(otherUser, new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.PENDING, clientRequestId);
        when(sosRequestRepository.findByClientRequestIdWithUser(clientRequestId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> helper.resolveExistingSosAfterConflict(clientRequestId, touristId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("associated with another user");
    }

    @Test
    void resolveExistingSosAfterConflict_throwsResourceNotFoundWhenNotFound() {
        UUID touristId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();

        when(sosRequestRepository.findByClientRequestIdWithUser(clientRequestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> helper.resolveExistingSosAfterConflict(clientRequestId, touristId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("SOS not found");
    }
}
