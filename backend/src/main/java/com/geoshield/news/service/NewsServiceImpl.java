package com.geoshield.news.service;

import com.geoshield.news.config.NewsProperties;
import com.geoshield.news.dto.NewsQuery;
import com.geoshield.news.dto.NewsResponse;
import com.geoshield.news.dto.RecentSafetyEventDto;
import com.geoshield.news.provider.NewsProvider;
import com.geoshield.news.provider.gnews.GNewsProvider;
import com.geoshield.news.provider.mock.MockNewsProvider;
import com.geoshield.risk.dto.GeographicResolution;
import com.geoshield.risk.service.GeographicResolutionService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Implementation of NewsService providing area resolution, thread-safe in-memory caching,
 * provider delegation, deduplication, and graceful degradation.
 */
@Service
public class NewsServiceImpl implements NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsServiceImpl.class);

    private record CachedNews(NewsResponse response, Instant expiresAt) {}

    private final NewsProperties properties;
    private final GNewsProvider gNewsProvider;
    private final MockNewsProvider mockNewsProvider;
    private final GeographicResolutionService geographicResolutionService;
    private final EventDeduplicationService deduplicationService;

    private final Map<String, CachedNews> cache = new ConcurrentHashMap<>();

    @Autowired
    public NewsServiceImpl(NewsProperties properties,
                           GNewsProvider gNewsProvider,
                           MockNewsProvider mockNewsProvider,
                           GeographicResolutionService geographicResolutionService,
                           EventDeduplicationService deduplicationService) {
        this.properties = properties;
        this.gNewsProvider = gNewsProvider;
        this.mockNewsProvider = mockNewsProvider;
        this.geographicResolutionService = geographicResolutionService;
        this.deduplicationService = deduplicationService;
    }

    @Override
    public NewsResponse getRecentSafetyNews(NewsQuery query) {
        // Step 1: Resolve area name and resolution level
        ResolvedArea resolved = resolveTargetArea(query);
        String resolvedArea = resolved.areaName();
        String resolutionLevel = resolved.level();

        // Step 2: Formulate normalized cache key
        String categoryKey = (query.category() != null) ? query.category().name() : "ALL";
        String cacheKey = resolvedArea.toLowerCase(Locale.ROOT) + ":" + categoryKey;

        Instant now = Instant.now();

        // Step 3: Check in-memory cache
        CachedNews cachedEntry = cache.get(cacheKey);
        if (cachedEntry != null && now.isBefore(cachedEntry.expiresAt())) {
            log.debug("Serving safety news from in-memory cache for key '{}'", cacheKey);
            return cachedEntry.response().withCached(true);
        }

        // Step 4: Select active provider
        NewsProvider provider = selectProvider();
        if (provider == null || !provider.isAvailable()) {
            log.debug("No active news provider available for area '{}'", resolvedArea);
            return NewsResponse.empty(resolvedArea, resolutionLevel, false);
        }

        // Step 5: Query external or mock provider
        NewsQuery resolvedQuery = new NewsQuery(
                query.latitude(),
                query.longitude(),
                query.locality(),
                query.district(),
                resolvedArea,
                resolutionLevel,
                query.category(),
                query.limit()
        );

        List<RecentSafetyEventDto> rawEvents = provider.fetchSafetyNews(resolvedQuery);

        // Step 6: Deduplicate and cluster related coverage
        List<RecentSafetyEventDto> deduplicated = deduplicationService.deduplicate(rawEvents);

        // Apply result limit
        int limit = Math.min(query.limit(), properties.maxResults());
        if (deduplicated.size() > limit) {
            deduplicated = deduplicated.subList(0, limit);
        }

        NewsResponse response = new NewsResponse(
                resolvedArea,
                resolutionLevel,
                now,
                false,
                true,
                deduplicated.size(),
                deduplicated
        );

        // Step 7: Store in cache with TTL
        evictIfExceedsCapacity();
        Instant expiresAt = now.plus(Duration.ofMinutes(properties.cacheTtlMinutes()));
        cache.put(cacheKey, new CachedNews(response, expiresAt));

        return response;
    }

    @Override
    public void clearCache() {
        cache.clear();
        log.debug("News in-memory cache cleared.");
    }

    private ResolvedArea resolveTargetArea(NewsQuery query) {
        // Priority 1: User-supplied Locality (from mobile client native geocoder)
        if (query.locality() != null && !query.locality().isBlank()) {
            String cleanLocality = sanitizeArea(query.locality());
            if (!cleanLocality.isBlank()) {
                return new ResolvedArea(cleanLocality, "LOCALITY");
            }
        }

        // Priority 2: User-supplied District
        if (query.district() != null && !query.district().isBlank()) {
            String cleanDistrict = sanitizeArea(query.district());
            if (!cleanDistrict.isBlank()) {
                return new ResolvedArea(cleanDistrict, "DISTRICT");
            }
        }

        // Priority 3: Server-side point-in-polygon resolution to State/UT
        if (query.latitude() != null && query.longitude() != null) {
            GeographicResolution res = geographicResolutionService.resolve(query.latitude(), query.longitude());
            if (res.resolved() && res.geographicUnit() != null) {
                return new ResolvedArea(res.geographicUnit(), "STATE_UT");
            }
        }

        // Fallback: National Scope
        return new ResolvedArea("India", "NATIONAL");
    }

    private String sanitizeArea(String raw) {
        if (raw == null) {
            return "";
        }
        // Allow alphabetic characters, hyphens, and spaces up to 50 chars
        String clean = raw.replaceAll("[^a-zA-Z\\s\\-]", "").trim();
        if (clean.length() > 50) {
            clean = clean.substring(0, 50).trim();
        }
        return clean;
    }

    private NewsProvider selectProvider() {
        if (!properties.enabled()) {
            return null;
        }
        if ("mock".equalsIgnoreCase(properties.provider())) {
            return mockNewsProvider.isAvailable() ? mockNewsProvider : null;
        }
        if ("gnews".equalsIgnoreCase(properties.provider())) {
            return gNewsProvider.isAvailable() ? gNewsProvider : null;
        }
        return null;
    }

    private void evictIfExceedsCapacity() {
        if (cache.size() >= properties.cacheMaxSize()) {
            Instant now = Instant.now();
            cache.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
            if (cache.size() >= properties.cacheMaxSize()) {
                // Remove any key to stay within limit
                cache.keySet().stream().findFirst().ifPresent(cache::remove);
            }
        }
    }

    private record ResolvedArea(String areaName, String level) {}
}
