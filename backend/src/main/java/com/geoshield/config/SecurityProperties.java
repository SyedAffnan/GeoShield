package com.geoshield.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geoshield.security")
public record SecurityProperties(int bcryptStrength) {
    public SecurityProperties {
        if (bcryptStrength < 10) {
            throw new IllegalArgumentException("BCrypt strength must be at least 10");
        }
    }
}
