package com.geoshield.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** TODO(architecture-open): approved rate limits were not quantified in the Architecture/SRS. */
@ConfigurationProperties(prefix = "geoshield.rate-limit")
public record RateLimitProperties() { }
