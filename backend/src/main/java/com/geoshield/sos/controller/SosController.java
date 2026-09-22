package com.geoshield.sos.controller;

import com.geoshield.common.api.ApiResponse;
import com.geoshield.common.exception.ResourceNotFoundException;
import com.geoshield.sos.dto.CreateSosRequest;
import com.geoshield.sos.dto.SosResponse;
import com.geoshield.sos.dto.UpdateSosStatusRequest;
import com.geoshield.sos.service.SosService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sos")
public class SosController {
    private final SosService sosService;

    public SosController(SosService sosService) {
        this.sosService = sosService;
    }

    @PostMapping
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<ApiResponse<SosResponse>> createSos(
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKeyHeader,
            @Valid @RequestBody CreateSosRequest request) {
        String rawHeader = idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()
                ? idempotencyKeyHeader
                : xIdempotencyKeyHeader;
        UUID headerKey = null;
        if (rawHeader != null && !rawHeader.isBlank()) {
            try {
                headerKey = UUID.fromString(rawHeader.trim());
            } catch (IllegalArgumentException e) {
                throw new com.geoshield.common.exception.ValidationException("Invalid UUID format for Idempotency-Key header: " + rawHeader);
            }
        }

        UUID bodyKey = request.clientRequestId();
        if (headerKey != null && bodyKey != null && !headerKey.equals(bodyKey)) {
            throw new com.geoshield.common.exception.ValidationException("Idempotency-Key header (" + headerKey + ") and body clientRequestId (" + bodyKey + ") must match");
        }

        UUID effectiveKey = headerKey != null ? headerKey : bodyKey;
        if (effectiveKey == null) {
            throw new com.geoshield.common.exception.ValidationException("clientRequestId or Idempotency-Key header is required");
        }

        CreateSosRequest effectiveRequest = request.clientRequestId() != null && request.clientRequestId().equals(effectiveKey)
                ? request
                : request.withClientRequestId(effectiveKey);

        SosResponse response = sosService.createSos(currentUserId(authentication), effectiveRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("SOS alert triggered", response));
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('TOURIST')")
    public ApiResponse<SosResponse> getMyActiveSos(Authentication authentication) {
        SosResponse response = sosService.getMyActiveSos(currentUserId(authentication))
                .orElseThrow(() -> new ResourceNotFoundException("No active SOS request found"));
        return ApiResponse.success("Active SOS retrieved", response);
    }

    @PatchMapping("/{sosId}/cancel")
    @PreAuthorize("hasRole('TOURIST')")
    public ApiResponse<SosResponse> cancelSos(
            Authentication authentication,
            @PathVariable UUID sosId) {
        return ApiResponse.success("SOS cancelled", sosService.cancelSos(currentUserId(authentication), sosId));
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('RESPONDER', 'ADMIN')")
    public ApiResponse<List<SosResponse>> getSosQueue() {
        return ApiResponse.success("SOS queue retrieved", sosService.getSosQueue());
    }

    @GetMapping("/{sosId}")
    @PreAuthorize("hasAnyRole('RESPONDER', 'ADMIN')")
    public ApiResponse<SosResponse> getSosById(@PathVariable UUID sosId) {
        return ApiResponse.success("SOS details retrieved", sosService.getSosById(sosId));
    }

    @PatchMapping("/{sosId}/status")
    @PreAuthorize("hasAnyRole('RESPONDER', 'ADMIN')")
    public ApiResponse<SosResponse> updateSosStatus(
            Authentication authentication,
            @PathVariable UUID sosId,
            @Valid @RequestBody UpdateSosStatusRequest request) {
        UUID responderId = currentUserId(authentication);
        return ApiResponse.success("SOS status updated", sosService.updateSosStatus(sosId, request.status(), responderId));
    }

    private UUID currentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID userId) {
            return userId;
        }
        throw new IllegalStateException("Authenticated principal does not contain a user identifier");
    }
}
