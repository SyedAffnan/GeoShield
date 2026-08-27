package com.geoshield.identity.service;
import com.geoshield.common.service.ModuleService;
import com.geoshield.identity.dto.LoginRequest;
import com.geoshield.identity.dto.LoginResponse;
import com.geoshield.identity.dto.RegisterRequest;
import com.geoshield.identity.dto.RegisterResponse;
import com.geoshield.identity.entity.User;
import java.util.UUID;

import com.geoshield.identity.dto.ProvisionUserRequest;
import com.geoshield.identity.dto.UserSummaryResponse;
import com.geoshield.identity.entity.Role;
import java.util.List;
import java.util.Map;

public interface IdentityService extends ModuleService {
    RegisterResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
    User getUserById(UUID userId);
    UserSummaryResponse provisionUser(ProvisionUserRequest request);
    List<UserSummaryResponse> listUsers(Role roleFilter);
    UserSummaryResponse updateUserStatus(UUID userId, boolean active);
    Map<Role, Long> getUserCountsByRole();
}
