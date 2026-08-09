package com.geoshield.location.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LocationResponse(Long locationId, BigDecimal latitude, BigDecimal longitude, BigDecimal accuracy,
        BigDecimal speed, Instant timestamp) { }
