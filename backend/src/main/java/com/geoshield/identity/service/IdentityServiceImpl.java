package com.geoshield.identity.service;

import com.geoshield.common.exception.AccountLockedException;
import com.geoshield.common.exception.ConflictException;
import com.geoshield.common.exception.ResourceNotFoundException;
import com.geoshield.common.exception.UnauthorizedException;
import com.geoshield.identity.dto.LoginRequest;
import com.geoshield.identity.dto.LoginResponse;
import com.geoshield.identity.dto.RegisterRequest;
import com.geoshield.identity.dto.RegisterResponse;
import com.geoshield.identity.entity.Role;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.entity.UserRole;
import com.geoshield.identity.mapper.UserMapper;
import com.geoshield.identity.repository.RoleRepository;
import com.geoshield.identity.repository.UserRepository;
import com.geoshield.security.JwtTokenService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityServiceImpl implements IdentityService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public IdentityServiceImpl(UserRepository userRepository, RoleRepository roleRepository, UserMapper userMapper,
            PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username is already in use");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email is already in use");
        }
        UserRole touristRole = roleRepository.findByName(Role.TOURIST)
                .orElseThrow(() -> new ResourceNotFoundException("Tourist role is not configured"));
        User user = new User(request.username(), request.email(), passwordEncoder.encode(request.password()),
                request.fullName(), request.phoneNumber(), touristRole);
        return userMapper.toRegisterResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        if (!user.isActive()) {
            throw new AccountLockedException("Account is inactive");
        }

        String refreshToken = createRefreshToken();
        user.replaceRefreshToken(passwordEncoder.encode(refreshToken), Instant.now().plus(jwtTokenService.refreshTokenTtl()));
        return new LoginResponse(jwtTokenService.createAccessToken(user.getId(), user.getRole().getName()), refreshToken,
                jwtTokenService.accessTokenExpiresInSeconds(), user.getRole().getName());
    }

    private String createRefreshToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
