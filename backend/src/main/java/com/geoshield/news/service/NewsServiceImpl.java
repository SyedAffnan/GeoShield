package com.geoshield.news.service;

import com.geoshield.emergencyservices.service.EmergencyServicesService;
import com.geoshield.news.config.NewsProperties;
import com.geoshield.news.dto.EventSeverity;
import com.geoshield.news.dto.NewsQuery;
import com.geoshield.news.dto.NewsResponse;
import com.geoshield.news.dto.RecentSafetyEventDto;
import com.geoshield.news.dto.RelevanceTier;
import com.geoshield.news.provider.NewsProvider;
import com.geoshield.news.provider.gnews.GNewsProvider;
import com.geoshield.news.provider.mock.MockNewsProvider;
import com.geoshield.risk.dto.GeographicResolution;
import com.geoshield.risk.service.GeographicResolutionService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Implementation of NewsService providing hierarchical area resolution
 * (Locality -> District -> State/UT fallback), thread-safe in-memory caching,
 * provider delegation, deduplication, authoritative freshness filtering, and graceful degradation.
 */
@Service
public class NewsServiceImpl implements NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsServiceImpl.class);

    private record CachedNews(NewsResponse response, Instant expiresAt) {}

    private static final Comparator<RecentSafetyEventDto> EVENT_COMPARATOR = Comparator
            .comparingInt((RecentSafetyEventDto e) -> relevancePriority(e.relevance()))
            .thenComparing(RecentSafetyEventDto::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparingInt(e -> severityPriority(e.severity()));

    private final NewsProperties properties;
    private final GNewsProvider gNewsProvider;
    private final MockNewsProvider mockNewsProvider;
    private final GeographicResolutionService geographicResolutionService;
    private final EventDeduplicationService deduplicationService;
    private final LocationRelevanceFilter relevanceFilter;
    private final EmergencyServicesService emergencyServicesService;
    private final Clock clock;

    private final Map<String, CachedNews> cache = new ConcurrentHashMap<>();

    @Autowired
    public NewsServiceImpl(NewsProperties properties,
                           GNewsProvider gNewsProvider,
                           MockNewsProvider mockNewsProvider,
                           GeographicResolutionService geographicResolutionService,
                           EventDeduplicationService deduplicationService,
                           LocationRelevanceFilter relevanceFilter,
                           @Autowired(required = false) EmergencyServicesService emergencyServicesService,
                           @Autowired(required = false) Clock clock) {
        this.properties = properties;
        this.gNewsProvider = gNewsProvider;
        this.mockNewsProvider = mockNewsProvider;
        this.geographicResolutionService = geographicResolutionService;
        this.deduplicationService = deduplicationService;
        this.relevanceFilter = relevanceFilter != null ? relevanceFilter : new LocationRelevanceFilter();
        this.emergencyServicesService = emergencyServicesService;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public NewsServiceImpl(NewsProperties properties,
                           GNewsProvider gNewsProvider,
                           MockNewsProvider mockNewsProvider,
                           GeographicResolutionService geographicResolutionService,
                           EventDeduplicationService deduplicationService,
                           LocationRelevanceFilter relevanceFilter) {
        this(properties, gNewsProvider, mockNewsProvider, geographicResolutionService,
             deduplicationService, relevanceFilter, null, Clock.systemUTC());
    }

    public NewsServiceImpl(NewsProperties properties,
                           GNewsProvider gNewsProvider,
                           MockNewsProvider mockNewsProvider,
                           GeographicResolutionService geographicResolutionService,
                           EventDeduplicationService deduplicationService,
                           EmergencyServicesService emergencyServicesService) {
        this(properties, gNewsProvider, mockNewsProvider, geographicResolutionService,
             deduplicationService, new LocationRelevanceFilter(), emergencyServicesService, Clock.systemUTC());
    }

    public NewsServiceImpl(NewsProperties properties,
                           GNewsProvider gNewsProvider,
                           MockNewsProvider mockNewsProvider,
                           GeographicResolutionService geographicResolutionService,
                           EventDeduplicationService deduplicationService) {
        this(properties, gNewsProvider, mockNewsProvider, geographicResolutionService,
             deduplicationService, new LocationRelevanceFilter(), null, Clock.systemUTC());
    }

    @Override
    public NewsResponse getRecentSafetyNews(NewsQuery query) {
        Instant now = clock.instant();

        // Step 1: Select active provider
        NewsProvider provider = selectProvider();
        if (provider == null || !provider.isAvailable()) {
            ResolvedArea primary = resolvePrimaryTargetArea(query);
            log.debug("No active news provider available for area '{}'", primary.areaName());
            return NewsResponse.empty(primary.areaName(), primary.level(), false);
        }

        // Step 2: Build candidate search hierarchy [LOCALITY, DISTRICT, STATE_UT, NATIONAL]
        List<ResolvedArea> hierarchy = buildHierarchy(query);

        // Step 3: Hierarchical retrieval (Level 1: Locality -> Level 2: District -> Level 3: State/UT)
        for (ResolvedArea candidate : hierarchy) {
            String categoryKey = (query.category() != null) ? query.category().name() : "ALL";
            String cacheKey = candidate.level().toLowerCase(Locale.ROOT) + ":"
                    + candidate.areaName().toLowerCase(Locale.ROOT) + ":" + categoryKey;

            // Check cache
            CachedNews cachedEntry = cache.get(cacheKey);
            if (cachedEntry != null && now.isBefore(cachedEntry.expiresAt())) {
                log.debug("Serving safety news from cache for candidate '{}' ({})", candidate.areaName(), candidate.level());
                return cachedEntry.response().withCached(true);
            }

            NewsQuery candidateQuery = new NewsQuery(
                    query.latitude(),
                    query.longitude(),
                    query.locality(),
                    query.district(),
                    query.stateUt(),
                    candidate.areaName(),
                    candidate.level(),
                    query.category(),
                    query.limit()
            );

            List<RecentSafetyEventDto> rawEvents = provider.fetchSafetyNews(candidateQuery);
            if (rawEvents != null && !rawEvents.isEmpty()) {
                // 1. Authoritative publication freshness filtering (rejecting stale or unusable timestamps)
                List<RecentSafetyEventDto> freshEvents = new ArrayList<>();
                for (RecentSafetyEventDto event : rawEvents) {
                    if (isFresh(event.publishedAt(), now, properties.maxAgeDays())) {
                        freshEvents.add(event);
                    } else {
                        log.debug("Authoritative freshness filter rejected article '{}' published at {}",
                                event.title(), event.publishedAt());
                    }
                }

                // 2. Geographic consistency and conflict filtering
                String locality = resolveLocality(query);
                String district = resolveDistrict(query);
                String stateUt = resolveStateUt(query);

                List<RecentSafetyEventDto> validEvents = new ArrayList<>();
                for (RecentSafetyEventDto event : freshEvents) {
                    RelevanceTier tier;
                    if ("NATIONAL".equalsIgnoreCase(candidate.level())) {
                        tier = event.relevance();
                    } else {
                        tier = relevanceFilter.calculateHierarchicalRelevance(
                                locality, district, stateUt, event.title(), event.description()
                        );
                    }
                    if (relevanceFilter.isAcceptableForLevel(candidate.level(), tier)) {
                        validEvents.add(event.withRelevance(tier));
                    } else {
                        log.debug("Authoritative geographic filter rejected article '{}' with tier {} for candidate level {}",
                                event.title(), tier, candidate.level());
                    }
                }

                if (!validEvents.isEmpty()) {
                    // 3. Deduplicate and cluster related coverage
                    List<RecentSafetyEventDto> deduplicated = new ArrayList<>(deduplicationService.deduplicate(validEvents));

                    // 4. Sort: Prioritize HIGH relevance, newest publication, and severity
                    deduplicated.sort(EVENT_COMPARATOR);

                    // Apply result limit
                    int limit = Math.min(query.limit(), properties.maxResults());
                    if (deduplicated.size() > limit) {
                        deduplicated = deduplicated.subList(0, limit);
                    }

                    if (!deduplicated.isEmpty()) {
                        NewsResponse response = new NewsResponse(
                                candidate.areaName(),
                                candidate.level(),
                                now,
                                false,
                                true,
                                deduplicated.size(),
                                deduplicated
                        );

                        evictIfExceedsCapacity();
                        Instant expiresAt = now.plus(Duration.ofMinutes(properties.cacheTtlMinutes()));
                        cache.put(cacheKey, new CachedNews(response, expiresAt));

                        return response;
                    }
                }
            }
        }

        // Fallback: If no level yielded articles, return empty response for the most specific resolved area
        ResolvedArea primary = resolvePrimaryTargetArea(query);
        return NewsResponse.empty(primary.areaName(), primary.level(), true);
    }

    /**
     * Authoritative publication freshness check.
     * Uses exact duration comparison against the configured maxAgeDays.
     * Rejects articles older than maxAgeDays, null timestamps, or future timestamps exceeding clock skew.
     */
    public boolean isFresh(Instant publishedAt, Instant now, int maxAgeDays) {
        if (publishedAt == null) {
            return false;
        }
        Instant oldestAllowed = now.minus(Duration.ofDays(maxAgeDays));
        if (publishedAt.isBefore(oldestAllowed)) {
            return false;
        }
        // Reject future timestamps beyond 15-minute clock skew tolerance
        if (publishedAt.isAfter(now.plus(Duration.ofMinutes(15)))) {
            return false;
        }
        return true;
    }

    @Override
    public void clearCache() {
        cache.clear();
        log.debug("News in-memory cache cleared.");
    }

    private List<ResolvedArea> buildHierarchy(NewsQuery query) {
        List<ResolvedArea> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // If query already has an explicit resolvedArea & resolutionLevel
        if (query.resolvedArea() != null && !query.resolvedArea().isBlank()
                && query.resolutionLevel() != null && !query.resolutionLevel().isBlank()) {
            list.add(new ResolvedArea(sanitizeArea(query.resolvedArea()), query.resolutionLevel().toUpperCase(Locale.ROOT)));
            return list;
        }

        // Level 1: Locality candidate (from query or server-side emergency facility proximity)
        String locality = resolveLocality(query);
        if (locality != null && !locality.isBlank() && seen.add(locality.toLowerCase(Locale.ROOT))) {
            list.add(new ResolvedArea(locality, "LOCALITY"));
        }

        // Level 2: District candidate (from query)
        String district = resolveDistrict(query);
        if (district != null && !district.isBlank() && seen.add(district.toLowerCase(Locale.ROOT))) {
            list.add(new ResolvedArea(district, "DISTRICT"));
        }

        // Level 3: State/UT candidate (from server-side point-in-polygon)
        String stateUt = resolveStateUt(query);
        if (stateUt != null && !stateUt.isBlank() && seen.add(stateUt.toLowerCase(Locale.ROOT))) {
            list.add(new ResolvedArea(stateUt, "STATE_UT"));
        }

        // Level 4: Fallback National scope
        if (list.isEmpty()) {
            list.add(new ResolvedArea("India", "NATIONAL"));
        }

        return list;
    }

    private String resolveLocality(NewsQuery query) {
        if (query.locality() != null && !query.locality().isBlank()) {
            return sanitizeArea(query.locality());
        }
        // Server-side reverse geocoding fallback via nearest emergency facility
        if (emergencyServicesService != null && query.latitude() != null && query.longitude() != null) {
            try {
                var nearest = emergencyServicesService.findNearestFacility(
                        query.latitude().doubleValue(),
                        query.longitude().doubleValue()
                );
                if (nearest.isPresent() && nearest.get().distanceKm() <= 25.0) {
                    String city = nearest.get().facility().city();
                    if (city != null && !city.isBlank()) {
                        return sanitizeArea(city);
                    }
                }
            } catch (Exception e) {
                log.debug("Emergency service proximity city lookup failed: {}", e.getMessage());
            }
        }
        return null;
    }

    private String resolveDistrict(NewsQuery query) {
        if (query.district() != null && !query.district().isBlank()) {
            return sanitizeArea(query.district());
        }
        return null;
    }

    private String resolveStateUt(NewsQuery query) {
        if (query.stateUt() != null && !query.stateUt().isBlank()) {
            return sanitizeArea(query.stateUt());
        }
        if (query.latitude() != null && query.longitude() != null && geographicResolutionService != null) {
            GeographicResolution res = geographicResolutionService.resolve(query.latitude(), query.longitude());
            if (res != null && res.resolved() && res.geographicUnit() != null) {
                return sanitizeArea(res.geographicUnit());
            }
        }
        return null;
    }

    private ResolvedArea resolvePrimaryTargetArea(NewsQuery query) {
        if (query.resolvedArea() != null && !query.resolvedArea().isBlank()) {
            String level = query.resolutionLevel() != null ? query.resolutionLevel() : "LOCALITY";
            return new ResolvedArea(sanitizeArea(query.resolvedArea()), level);
        }
        String locality = resolveLocality(query);
        if (locality != null && !locality.isBlank()) {
            return new ResolvedArea(locality, "LOCALITY");
        }
        String district = resolveDistrict(query);
        if (district != null && !district.isBlank()) {
            return new ResolvedArea(district, "DISTRICT");
        }
        String stateUt = resolveStateUt(query);
        if (stateUt != null && !stateUt.isBlank()) {
            return new ResolvedArea(stateUt, "STATE_UT");
        }
        return new ResolvedArea("India", "NATIONAL");
    }

    private String sanitizeArea(String raw) {
        if (raw == null) {
            return "";
        }
        String clean = raw.replaceAll("[^a-zA-Z0-9\\s\\-]", "").trim();
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
                cache.keySet().stream().findFirst().ifPresent(cache::remove);
            }
        }
    }

    private static int relevancePriority(RelevanceTier tier) {
        if (tier == null) return 3;
        return switch (tier) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
            case UNKNOWN -> 3;
        };
    }

    private static int severityPriority(EventSeverity severity) {
        if (severity == null) return 4;
        return switch (severity) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case MODERATE -> 2;
            case LOW -> 3;
            case UNKNOWN -> 4;
        };
    }

    private record ResolvedArea(String areaName, String level) {}
}

