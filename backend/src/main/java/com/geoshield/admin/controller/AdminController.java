package com.geoshield.admin.controller;

import com.geoshield.admin.dto.AdminIncidentSummaryResponse;
import com.geoshield.admin.dto.AdminStatsResponse;
import com.geoshield.admin.service.AdminService;
import com.geoshield.common.api.ApiResponse;
import com.geoshield.identity.dto.ProvisionUserRequest;
import com.geoshield.identity.dto.UserStatusUpdateRequest;
import com.geoshield.identity.dto.UserSummaryResponse;
import com.geoshield.identity.entity.Role;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public ApiResponse<AdminStatsResponse> getStats() {
        return ApiResponse.success("System statistics retrieved", adminService.getSystemStats());
    }

    @GetMapping("/users")
    public ApiResponse<List<UserSummaryResponse>> getUsers(@RequestParam(required = false) Role role) {
        return ApiResponse.success("Users retrieved", adminService.getUsers(role));
    }

    @PostMapping("/users/provision")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> provisionUser(@Valid @RequestBody ProvisionUserRequest request) {
        UserSummaryResponse response = adminService.provisionUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("User provisioned successfully", response));
    }

    @PatchMapping("/users/{userId}/status")
    public ApiResponse<UserSummaryResponse> updateUserStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UserStatusUpdateRequest request) {
        return ApiResponse.success("User status updated", adminService.updateUserStatus(userId, request.active()));
    }

    @GetMapping("/incidents")
    public ApiResponse<List<AdminIncidentSummaryResponse>> getIncidents() {
        return ApiResponse.success("Incidents retrieved", adminService.getAllIncidents());
    }
}
