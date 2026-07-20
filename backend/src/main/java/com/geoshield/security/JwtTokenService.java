package com.geoshield.security;

import com.geoshield.config.JwtProperties;
import com.geoshield.identity.entity.Role;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private static final int MINIMUM_HMAC_SECRET_BYTES = 32;
    private final JwtProperties jwtProperties;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        secretBytes();
    }

    public String createAccessToken(UUID userId, Role role) {
        Instant issuedAt = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(userId.toString()).claim("role", role.name())
                .issueTime(Date.from(issuedAt)).expirationTime(Date.from(issuedAt.plus(jwtProperties.accessTokenTtl()))).build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secretBytes()));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new JwtAuthenticationException("Unable to issue access token", exception);
        }
    }

    public AuthenticatedPrincipal validateAccessToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secretBytes()))) {
                throw new JwtAuthenticationException("Invalid access token");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime() == null || !claims.getExpirationTime().after(new Date())) {
                throw new JwtAuthenticationException("Access token has expired");
            }
            return new AuthenticatedPrincipal(UUID.fromString(claims.getSubject()), Role.valueOf(claims.getStringClaim("role")));
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            throw new JwtAuthenticationException("Invalid access token", exception);
        }
    }

    public long accessTokenExpiresInSeconds() {
        return jwtProperties.accessTokenTtl().toSeconds();
    }

    public java.time.Duration refreshTokenTtl() {
        // TODO(architecture-open): confirm the refresh-token lifetime policy before exposing refresh-token rotation endpoints.
        return jwtProperties.refreshTokenTtl();
    }

    private byte[] secretBytes() {
        if (jwtProperties.secret() == null || jwtProperties.secret().getBytes(StandardCharsets.UTF_8).length < MINIMUM_HMAC_SECRET_BYTES) {
            throw new JwtAuthenticationException("JWT secret must contain at least 32 bytes");
        }
        return jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
    }

    public record AuthenticatedPrincipal(UUID userId, Role role) { }
}
