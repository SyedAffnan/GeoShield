package com.geoshield.news.service;

import com.geoshield.news.dto.NewsQuery;
import com.geoshield.news.dto.NewsResponse;

/**
 * Service interface for location-based safety news intelligence.
 */
public interface NewsService {

    /**
     * Resolves location, queries external/mock news provider, deduplicates and caches results.
     *
     * @param query query parameters (coordinates, locality, district, category, limit)
     * @return structured news response with safety events
     */
    NewsResponse getRecentSafetyNews(NewsQuery query);

    /**
     * Clears in-memory news cache.
     */
    void clearCache();
}
