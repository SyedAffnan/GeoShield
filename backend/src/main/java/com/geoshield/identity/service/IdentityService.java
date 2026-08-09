package com.geoshield.identity.service;
import com.geoshield.common.service.ModuleService;
import com.geoshield.identity.dto.LoginRequest;
import com.geoshield.identity.dto.LoginResponse;
import com.geoshield.identity.dto.RegisterRequest;
import com.geoshield.identity.dto.RegisterResponse;
import com.geoshield.identity.entity.User;
import java.util.UUID;

public interface IdentityService extends ModuleService {
    RegisterResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
    User getUserById(UUID userId);

    // TODO(architecture-open): define the approved logout endpoint and refresh-token revocation contract before implementation.
}
