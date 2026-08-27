package com.geoshield.admin.service;

import com.geoshield.admin.dto.AdminIncidentSummaryResponse;
import com.geoshield.admin.dto.AdminStatsResponse;
import com.geoshield.common.service.ModuleService;
import com.geoshield.identity.dto.ProvisionUserRequest;
import com.geoshield.identity.dto.UserSummaryResponse;
import com.geoshield.identity.entity.Role;
import java.util.List;
import java.util.UUID;

public interface AdminService extends ModuleService {
    AdminStatsResponse getSystemStats();
    List<UserSummaryResponse> getUsers(Role roleFilter);
    UserSummaryResponse provisionUser(ProvisionUserRequest request);
    UserSummaryResponse updateUserStatus(UUID userId, boolean active);
    List<AdminIncidentSummaryResponse> getAllIncidents();
}
