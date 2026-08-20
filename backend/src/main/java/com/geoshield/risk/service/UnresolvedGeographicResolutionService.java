package com.geoshield.risk.service;

import com.geoshield.risk.dto.GeographicResolution;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class UnresolvedGeographicResolutionService implements GeographicResolutionService {
    @Override
    public GeographicResolution resolve(BigDecimal latitude, BigDecimal longitude) {
        return GeographicResolution.unresolved("No verified offline GPS-to-State/UT or city mapping dataset is configured.");
    }
}
