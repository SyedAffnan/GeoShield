package com.geoshield.risk.controller;

import com.geoshield.common.api.ApiResponse;
import com.geoshield.risk.dto.HistoricalTrendAdvisoryResponse;
import com.geoshield.risk.service.HistoricalTrendAdvisoryService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing retrospective 2024 regional transport safety evaluation estimates.
 *
 * <p>Dedicated informational endpoint, decoupled from authoritative risk calculation.
 */
@RestController
@RequestMapping("/api/v1/risk/historical-advisory")
@PreAuthorize("hasRole('TOURIST')")
public class HistoricalTrendAdvisoryController {
    private final HistoricalTrendAdvisoryService advisoryService;

    public HistoricalTrendAdvisoryController(HistoricalTrendAdvisoryService advisoryService) {
        this.advisoryService = advisoryService;
    }

    @GetMapping
    public ApiResponse<HistoricalTrendAdvisoryResponse> getHistoricalAdvisory(Authentication authentication) {
        return ApiResponse.success("Historical trend advisory retrieved", advisoryService.getAdvisoryForUser(currentUserId(authentication)));
    }

    private UUID currentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID userId) return userId;
        throw new IllegalStateException("Authenticated principal does not contain a user identifier");
    }
}
