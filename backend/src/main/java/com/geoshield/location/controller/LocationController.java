package com.geoshield.location.controller;

import com.geoshield.common.api.ApiResponse;
import com.geoshield.location.dto.LocationRequest;
import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.service.LocationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locations")
@PreAuthorize("hasRole('TOURIST')")
public class LocationController {
    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PostMapping
    public ApiResponse<LocationResponse> submitLocation(Authentication authentication, @Valid @RequestBody LocationRequest request) {
        return ApiResponse.success("Location updated", locationService.submitLocation(currentUserId(authentication), request));
    }

    @GetMapping
    public ApiResponse<LocationResponse> getCurrentLocation(Authentication authentication) {
        return ApiResponse.success("Current location retrieved", locationService.getCurrentLocation(currentUserId(authentication)));
    }

    @GetMapping("/history")
    public ApiResponse<List<LocationResponse>> getLocationHistory(Authentication authentication) {
        return ApiResponse.success("Location history retrieved", locationService.getLocationHistory(currentUserId(authentication)));
    }

    private UUID currentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID userId) {
            return userId;
        }
        throw new IllegalStateException("Authenticated principal does not contain a user identifier");
    }
}
