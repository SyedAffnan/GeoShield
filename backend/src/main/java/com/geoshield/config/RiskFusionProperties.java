package com.geoshield.config;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geoshield.risk")
public record RiskFusionProperties(BigDecimal historicalIncidentWeight, BigDecimal weatherWeight,
        BigDecimal timeOfDayWeight, BigDecimal serviceProximityWeight, BigDecimal userReportWeight,
        BigDecimal connectivityWeight, BigDecimal otherContextWeight, int lowMax, int mediumMax, int highMax) {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public RiskFusionProperties {
        List<BigDecimal> weights = List.of(historicalIncidentWeight, weatherWeight, timeOfDayWeight,
                serviceProximityWeight, userReportWeight, connectivityWeight, otherContextWeight);
        if (weights.stream().anyMatch(weight -> weight == null || weight.signum() < 0)
                || weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Risk weights must be non-negative and total exactly 1.0");
        }
        if (lowMax < 0 || lowMax >= mediumMax || mediumMax >= highMax || highMax >= ONE_HUNDRED.intValue()) {
            throw new IllegalArgumentException("Risk thresholds must be ordered within the 0 to 100 range");
        }
    }
}
