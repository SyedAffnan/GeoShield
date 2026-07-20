package com.geoshield.identity.service;
import com.geoshield.common.service.ModuleService;
import com.geoshield.identity.dto.LoginRequest;
import com.geoshield.identity.dto.LoginResponse;
import com.geoshield.identity.dto.RegisterRequest;
import com.geoshield.identity.dto.RegisterResponse;

public interface IdentityService extends ModuleService {
    RegisterResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request);

    // TODO(architecture-open): define the approved logout endpoint and refresh-token revocation contract before implementation.
}
