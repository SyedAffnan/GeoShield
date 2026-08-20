package com.geoshield.risk.controller;

import com.geoshield.common.api.ApiResponse;
import com.geoshield.risk.dto.RiskResponse;
import com.geoshield.risk.service.RiskApiService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/risk")
@PreAuthorize("hasRole('TOURIST')")
public class RiskController {
    private final RiskApiService riskApiService;

    public RiskController(RiskApiService riskApiService) {
        this.riskApiService = riskApiService;
    }

    @GetMapping
    public ApiResponse<RiskResponse> getCurrentRisk(Authentication authentication) {
        return ApiResponse.success("Current risk retrieved", riskApiService.getCurrentRisk(currentUserId(authentication)));
    }

    private UUID currentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID userId) return userId;
        throw new IllegalStateException("Authenticated principal does not contain a user identifier");
    }
}
