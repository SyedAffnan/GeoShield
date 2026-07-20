package com.geoshield.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geoshield.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) { }
