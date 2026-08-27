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

    private SosServiceImpl sosService;

    @BeforeEach
    void setUp() {
        sosService = new SosServiceImpl(sosRequestRepository, identityService, sosMapper);
    }

    @Test
    void createSosPersistsWithPendingStatus() {
        UUID touristId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();
        CreateSosRequest request = new CreateSosRequest(new BigDecimal("12.9716"), new BigDecimal("77.5946"), clientRequestId);

        User tourist = new User("tourist1", "tourist1@example.com", "hash", "Tourist One", "+919876543210", new UserRole(Role.TOURIST));
        when(sosRequestRepository.findByUserIdAndClientRequestId(touristId, clientRequestId)).thenReturn(Optional.empty());
        when(sosRequestRepository.findByClientRequestId(clientRequestId)).thenReturn(Optional.empty());
        when(identityService.getUserById(touristId)).thenReturn(tourist);

        SosRequest saved = new SosRequest(tourist, request.latitude(), request.longitude(), SosStatus.PENDING, clientRequestId);
        when(sosRequestRepository.save(any(SosRequest.class))).thenReturn(saved);

        SosResponse expected = new SosResponse(
                UUID.randomUUID(), touristId, "tourist1", "Tourist One", "+919876543210",
                request.latitude(), request.longitude(), SosStatus.PENDING, null, clientRequestId, Instant.now()
        );
        when(sosMapper.toResponse(saved)).thenReturn(expected);

        SosResponse result = sosService.createSos(touristId, request);

        assertThat(result.status()).isEqualTo(SosStatus.PENDING);
        verify(sosRequestRepository).save(any(SosRequest.class));
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
