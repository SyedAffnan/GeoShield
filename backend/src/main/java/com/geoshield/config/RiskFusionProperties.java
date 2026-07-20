package com.geoshield.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geoshield.risk")
public record RiskFusionProperties(BigDecimal historicalIncidentWeight, BigDecimal weatherWeight,
        BigDecimal timeOfDayWeight, BigDecimal serviceProximityWeight, BigDecimal userReportWeight,
        BigDecimal connectivityWeight, BigDecimal otherContextWeight, int lowMax, int mediumMax, int highMax) { }
