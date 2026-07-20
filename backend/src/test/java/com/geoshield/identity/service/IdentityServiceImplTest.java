package com.geoshield.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geoshield.common.exception.ConflictException;
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
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IdentityServiceImplTest {
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserMapper userMapper;
    @Mock private JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    @Test
    void registerCreatesTouristWithBcryptPassword() {
        IdentityServiceImpl service = new IdentityServiceImpl(userRepository, roleRepository, userMapper, passwordEncoder, jwtTokenService);
        RegisterRequest request = new RegisterRequest("tourist01", "tourist@example.com", "ValidPass1!", "Geo Shield", "+919876543210");
        UserRole touristRole = new UserRole(Role.TOURIST);
        RegisterResponse expected = new RegisterResponse(UUID.randomUUID(), request.username(), request.email(), Role.TOURIST);
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(roleRepository.findByName(Role.TOURIST)).thenReturn(Optional.of(touristRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toRegisterResponse(any(User.class))).thenReturn(expected);

        RegisterResponse response = service.register(request);

        assertThat(response).isEqualTo(expected);
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                user.getRole().getName() == Role.TOURIST && passwordEncoder.matches(request.password(), user.getPasswordHash())));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        IdentityServiceImpl service = new IdentityServiceImpl(userRepository, roleRepository, userMapper, passwordEncoder, jwtTokenService);
        RegisterRequest request = new RegisterRequest("tourist01", "tourist@example.com", "ValidPass1!", "Geo Shield", "+919876543210");
        when(userRepository.existsByUsername(request.username())).thenReturn(true);

        assertThatThrownBy(() -> service.register(request)).isInstanceOf(ConflictException.class)
                .hasMessage("Username is already in use");
    }

    @Test
    void loginIssuesAccessTokenAndStoresOnlyHashedRefreshToken() {
        IdentityServiceImpl service = new IdentityServiceImpl(userRepository, roleRepository, userMapper, passwordEncoder, jwtTokenService);
        UserRole touristRole = new UserRole(Role.TOURIST);
        User user = new User("tourist01", "tourist@example.com", passwordEncoder.encode("ValidPass1!"), "Geo Shield", "+919876543210", touristRole);
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", userId);
        when(userRepository.findByEmail("tourist@example.com")).thenReturn(Optional.of(user));
        when(jwtTokenService.createAccessToken(userId, Role.TOURIST)).thenReturn("access-token");
        when(jwtTokenService.accessTokenExpiresInSeconds()).thenReturn(900L);
        when(jwtTokenService.refreshTokenTtl()).thenReturn(Duration.ofDays(30));

        LoginResponse response = service.login(new LoginRequest("tourist@example.com", "ValidPass1!"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.role()).isEqualTo(Role.TOURIST);
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(ReflectionTestUtils.getField(user, "refreshTokenHash")).isNotEqualTo(response.refreshToken());
        assertThat(passwordEncoder.matches(response.refreshToken(), (String) ReflectionTestUtils.getField(user, "refreshTokenHash"))).isTrue();
    }
}
