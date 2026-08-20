package com.geoshield.risk.service;

import com.geoshield.risk.dto.GeographicResolution;
import java.math.BigDecimal;

public interface GeographicResolutionService {
    GeographicResolution resolve(BigDecimal latitude, BigDecimal longitude);

    // TODO(architecture-open): provide a verified offline mapping dataset or approved geocoding provider.
}
