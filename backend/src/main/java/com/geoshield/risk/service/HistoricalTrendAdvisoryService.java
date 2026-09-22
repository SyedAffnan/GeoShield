package com.geoshield.risk.service;

import com.geoshield.risk.dto.HistoricalTrendAdvisoryResponse;
import java.util.UUID;

/**
 * Service providing retrospective 2024 regional transport safety evaluation estimates.
 *
 * <p>Strictly informational and non-authoritative; zero coupling to risk calculation or persistence.
 */
public interface HistoricalTrendAdvisoryService {
    HistoricalTrendAdvisoryResponse getAdvisoryForUser(UUID userId);
    boolean isAvailable();
}
