package com.geoshield.news.provider;

import com.geoshield.news.dto.NewsQuery;
import com.geoshield.news.dto.RecentSafetyEventDto;
import java.util.List;

/**
 * Service provider interface for external news providers.
 * Decouples business logic from upstream vendor-specific APIs.
 */
public interface NewsProvider {
    /**
     * Retrieves recent safety-related news articles matching the query specification.
     *
     * @param query query parameters including target area and filters
     * @return list of normalized safety event DTOs
     */
    List<RecentSafetyEventDto> fetchSafetyNews(NewsQuery query);

    /**
     * @return human-readable identifier of the news provider (e.g. "GNews", "Mock")
     */
    String getProviderName();

    /**
     * @return true if provider is configured and reachable
     */
    boolean isAvailable();
}
