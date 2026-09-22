package com.geoshield.sos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.geoshield.common.api.ApiResponse;
import com.geoshield.sos.dto.CreateSosRequest;
import com.geoshield.sos.dto.SosResponse;
import com.geoshield.sos.entity.SosStatus;
import com.geoshield.sos.service.SosService;
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
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class SosControllerTest {

    @Mock
    private SosService sosService;

    private SosController sosController;

    private UUID touristId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        sosController = new SosController(sosService);
        touristId = UUID.randomUUID();
        // Simulate the JWT principal — the controller expects UUID as principal
        authentication = new UsernamePasswordAuthenticationToken(
                touristId, null, List.of());
    }

    @Test
    void createSos_withBodyOnly_returnsCreatedWithPendingStatus() {
        UUID clientRequestId = UUID.randomUUID();
        CreateSosRequest request = new CreateSosRequest(
                new BigDecimal("12.9716"),
                new BigDecimal("77.5946"),
                clientRequestId
        );
        SosResponse expectedResponse = new SosResponse(
                UUID.randomUUID(), touristId, "tourist1", "Tourist One",
                "+919876543210", new BigDecimal("12.9716"), new BigDecimal("77.5946"),
                SosStatus.PENDING, null, clientRequestId, Instant.now()
        );
        when(sosService.createSos(eq(touristId), any(CreateSosRequest.class)))
                .thenReturn(expectedResponse);

        var result = sosController.createSos(authentication, null, null, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data().status()).isEqualTo(SosStatus.PENDING);
        verify(sosService).createSos(eq(touristId), any(CreateSosRequest.class));
    }

    @Test
    void createSos_withHeaderOnly_returnsCreated() {
        UUID clientRequestId = UUID.randomUUID();
        CreateSosRequest requestWithoutId = new CreateSosRequest(
                new BigDecimal("12.9716"),
                new BigDecimal("77.5946"),
                null
        );
        SosResponse expectedResponse = new SosResponse(
                UUID.randomUUID(), touristId, "tourist1", "Tourist One",
                "+919876543210", new BigDecimal("12.9716"), new BigDecimal("77.5946"),
                SosStatus.PENDING, null, clientRequestId, Instant.now()
        );
        when(sosService.createSos(eq(touristId), any(CreateSosRequest.class)))
                .thenReturn(expectedResponse);

        var result = sosController.createSos(authentication, clientRequestId.toString(), null, requestWithoutId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().data().clientRequestId()).isEqualTo(clientRequestId);
    }

    @Test
    void createSos_withXHeaderOnly_returnsCreated() {
        UUID clientRequestId = UUID.randomUUID();
        CreateSosRequest requestWithoutId = new CreateSosRequest(
                new BigDecimal("12.9716"),
                new BigDecimal("77.5946"),
                null
        );
        SosResponse expectedResponse = new SosResponse(
                UUID.randomUUID(), touristId, "tourist1", "Tourist One",
                "+919876543210", new BigDecimal("12.9716"), new BigDecimal("77.5946"),
                SosStatus.PENDING, null, clientRequestId, Instant.now()
        );
        when(sosService.createSos(eq(touristId), any(CreateSosRequest.class)))
                .thenReturn(expectedResponse);

        var result = sosController.createSos(authentication, null, clientRequestId.toString(), requestWithoutId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().data().clientRequestId()).isEqualTo(clientRequestId);
    }

    @Test
    void createSos_withMatchingHeaderAndBody_returnsCreated() {
        UUID clientRequestId = UUID.randomUUID();
        CreateSosRequest request = new CreateSosRequest(
                new BigDecimal("12.9716"),
                new BigDecimal("77.5946"),
                clientRequestId
        );
        SosResponse expectedResponse = new SosResponse(
                UUID.randomUUID(), touristId, "tourist1", "Tourist One",
                "+919876543210", new BigDecimal("12.9716"), new BigDecimal("77.5946"),
                SosStatus.PENDING, null, clientRequestId, Instant.now()
        );
        when(sosService.createSos(eq(touristId), any(CreateSosRequest.class)))
                .thenReturn(expectedResponse);

        var result = sosController.createSos(authentication, clientRequestId.toString(), null, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().data().clientRequestId()).isEqualTo(clientRequestId);
    }

    @Test
    void createSos_withMismatchingHeaderAndBody_throwsValidationException() {
        UUID headerId = UUID.randomUUID();
        UUID bodyId = UUID.randomUUID();
        CreateSosRequest request = new CreateSosRequest(
                new BigDecimal("12.9716"),
                new BigDecimal("77.5946"),
                bodyId
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                sosController.createSos(authentication, headerId.toString(), null, request))
                .isInstanceOf(com.geoshield.common.exception.ValidationException.class)
                .hasMessageContaining("must match");
    }

    @Test
    void createSos_withInvalidHeaderUUID_throwsValidationException() {
        CreateSosRequest request = new CreateSosRequest(
                new BigDecimal("12.9716"),
                new BigDecimal("77.5946"),
                null
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                sosController.createSos(authentication, "not-a-valid-uuid", null, request))
                .isInstanceOf(com.geoshield.common.exception.ValidationException.class)
                .hasMessageContaining("Invalid UUID format");
    }

    @Test
    void createSos_withNoKeyAtAll_throwsValidationException() {
        CreateSosRequest request = new CreateSosRequest(
                new BigDecimal("12.9716"),
                new BigDecimal("77.5946"),
                null
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                sosController.createSos(authentication, null, null, request))
                .isInstanceOf(com.geoshield.common.exception.ValidationException.class)
                .hasMessageContaining("clientRequestId or Idempotency-Key header is required");
    }

    @Test
    void getMyActiveSos_returnsActiveSos_whenPresent() {
        UUID sosId = UUID.randomUUID();
        SosResponse expectedResponse = new SosResponse(
                sosId, touristId, "tourist1", "Tourist One",
                "+919876543210", new BigDecimal("12.9716"), new BigDecimal("77.5946"),
                SosStatus.ACKNOWLEDGED, null, UUID.randomUUID(), Instant.now()
        );
        when(sosService.getMyActiveSos(touristId))
                .thenReturn(Optional.of(expectedResponse));

        ApiResponse<SosResponse> response = sosController.getMyActiveSos(authentication);

        assertThat(response.data().sosId()).isEqualTo(sosId);
        assertThat(response.data().status()).isEqualTo(SosStatus.ACKNOWLEDGED);
    }

    @Test
    void getSosQueue_returnsAllActiveSos_forResponder() {
        SosResponse sos1 = new SosResponse(
                UUID.randomUUID(), UUID.randomUUID(), "tourist1", "Tourist One",
                "+919876543210", new BigDecimal("12.9716"), new BigDecimal("77.5946"),
                SosStatus.PENDING, null, UUID.randomUUID(), Instant.now()
        );
        SosResponse sos2 = new SosResponse(
                UUID.randomUUID(), UUID.randomUUID(), "tourist2", "Tourist Two",
                "+919876543211", new BigDecimal("-33.8688"), new BigDecimal("151.2093"),
                SosStatus.RESPONDING, UUID.randomUUID(), UUID.randomUUID(), Instant.now()
        );
        when(sosService.getSosQueue()).thenReturn(List.of(sos1, sos2));

        ApiResponse<List<SosResponse>> response = sosController.getSosQueue();

        assertThat(response.data()).hasSize(2);
        assertThat(response.data().get(0).status()).isEqualTo(SosStatus.PENDING);
        assertThat(response.data().get(1).status()).isEqualTo(SosStatus.RESPONDING);
    }

    @Test
    void cancelSos_delegatesCancel_toService() {
        UUID sosId = UUID.randomUUID();
        SosResponse cancelled = new SosResponse(
                sosId, touristId, "tourist1", "Tourist One",
                "+919876543210", new BigDecimal("12.9716"), new BigDecimal("77.5946"),
                SosStatus.CANCELLED, null, UUID.randomUUID(), Instant.now()
        );
        when(sosService.cancelSos(touristId, sosId)).thenReturn(cancelled);

        ApiResponse<SosResponse> response = sosController.cancelSos(authentication, sosId);

        assertThat(response.data().status()).isEqualTo(SosStatus.CANCELLED);
        verify(sosService).cancelSos(touristId, sosId);
    }
}
