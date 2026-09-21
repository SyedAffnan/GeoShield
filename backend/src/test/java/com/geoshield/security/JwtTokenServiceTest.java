package com.geoshield.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.geoshield.config.JwtProperties;
import com.geoshield.identity.entity.Role;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {
    private static final String SECRET = "test-only-secret-must-be-at-least-32-bytes";

    @Test
    void createsAndValidatesTokenWithApprovedClaims() {
        JwtTokenService tokenService = new JwtTokenService(new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(30)));
        UUID userId = UUID.randomUUID();

        String token = tokenService.createAccessToken(userId, Role.TOURIST);
        JwtTokenService.AuthenticatedPrincipal principal = tokenService.validateAccessToken(token);

        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.role()).isEqualTo(Role.TOURIST);
        assertThat(tokenService.accessTokenExpiresInSeconds()).isEqualTo(900);
    }

    @Test
    void rejectsPlaceholderSecretContainingChangeMe() {
        String placeholderSecret = "CHANGE_ME_IN_PRODUCTION_MIN_32_BYTES_SECRET_KEY!";
        org.junit.jupiter.api.Assertions.assertThrows(
                JwtAuthenticationException.class,
                () -> new JwtTokenService(new JwtProperties(placeholderSecret, Duration.ofMinutes(15), Duration.ofDays(30)))
        );
    }

    @Test
    void rejectsSecretShorterThan32Bytes() {
        String shortSecret = "too-short-secret";
        org.junit.jupiter.api.Assertions.assertThrows(
                JwtAuthenticationException.class,
                () -> new JwtTokenService(new JwtProperties(shortSecret, Duration.ofMinutes(15), Duration.ofDays(30)))
        );
    }
}
