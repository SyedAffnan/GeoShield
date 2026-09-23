package com.geoshield.news.provider.gnews;

import com.geoshield.news.config.NewsProperties;
import com.geoshield.news.dto.EventSeverity;
import com.geoshield.news.dto.NewsQuery;
import com.geoshield.news.dto.RecentSafetyEventDto;
import com.geoshield.news.dto.RelevanceTier;
import com.geoshield.news.dto.SafetyEventCategory;
import com.geoshield.news.provider.NewsProvider;
import com.geoshield.news.service.EventClassifier;
import com.geoshield.news.service.LocationRelevanceFilter;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * GNews API v4 implementation of the NewsProvider interface.
 * Externalizes API key, applies timeouts, and extracts safety-relevant events.
 */
@Component
public class GNewsProvider implements NewsProvider {

    private static final Logger log = LoggerFactory.getLogger(GNewsProvider.class);

    private static final Set<String> VERIFIED_INDIAN_SOURCES = Set.of(
            "the hindu", "the times of india", "times of india", "hindustan times",
            "the indian express", "indian express", "ndtv", "deccan herald",
            "the tribune", "tribune india", "telegraph india", "livemint", "ani", "pti"
    );

    private final NewsProperties properties;
    private final RestClient restClient;
    private final EventClassifier eventClassifier;
    private final LocationRelevanceFilter relevanceFilter;

    @Autowired
    public GNewsProvider(NewsProperties properties,
                         RestClient.Builder builder,
                         EventClassifier eventClassifier,
                         LocationRelevanceFilter relevanceFilter) {
        this.properties = properties;
        this.eventClassifier = eventClassifier;
        this.relevanceFilter = relevanceFilter;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();

        if (!isAvailable()) {
            log.info("GNewsProvider initialized in disabled/unconfigured state (apiKey present: {}).",
                    properties.apiKey() != null && !properties.apiKey().isBlank());
        } else {
            log.info("GNewsProvider active with endpoint: {}", properties.baseUrl());
        }
    }

    /** Constructor for unit testing with stubbed RestClient. */
    GNewsProvider(NewsProperties properties,
                  RestClient restClient,
                  EventClassifier eventClassifier,
                  LocationRelevanceFilter relevanceFilter) {
        this.properties = properties;
        this.restClient = restClient;
        this.eventClassifier = eventClassifier;
        this.relevanceFilter = relevanceFilter;
    }

    @Override
    public List<RecentSafetyEventDto> fetchSafetyNews(NewsQuery query) {
        if (!isAvailable()) {
            log.debug("GNewsProvider is not available or disabled; returning empty events.");
            return List.of();
        }

        String area = query.resolvedArea();
        if (area == null || area.isBlank()) {
            area = "India";
        }

        String queryTerms = buildQueryKeywords(area, query.category());
        Instant now = Instant.now();
        Instant fromTime = now.minus(Duration.ofDays(properties.maxAgeDays()));

        try {
            String uriString = String.format(
                    "%s/search?q=%s&lang=%s&country=%s&max=%d&from=%s&token=%s",
                    properties.baseUrl(),
                    URLEncoder.encode(queryTerms, StandardCharsets.UTF_8),
                    properties.language(),
                    properties.country(),
                    Math.min(query.limit(), properties.maxResults()),
                    URLEncoder.encode(fromTime.toString(), StandardCharsets.UTF_8),
                    properties.apiKey()
            );

            log.debug("Dispatching GNews query for area: '{}' (category: {})", area, query.category());

            GNewsRawResponse response = restClient.get()
                    .uri(URI.create(uriString))
                    .retrieve()
                    .body(GNewsRawResponse.class);

            if (response == null || response.articles() == null || response.articles().isEmpty()) {
                log.debug("GNews returned zero articles for query in area '{}'.", area);
                return List.of();
            }

            return mapArticles(response.articles(), area, query);

        } catch (RestClientException e) {
            log.warn("GNews request failed for area '{}': {}", area, e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("Unexpected error during GNews fetch for area '{}': {}", area, e.getMessage());
            return List.of();
        }
    }

    @Override
    public String getProviderName() {
        return "GNews";
    }

    @Override
    public boolean isAvailable() {
        return properties.enabled() && properties.apiKey() != null && !properties.apiKey().isBlank();
    }

    private String buildQueryKeywords(String area, SafetyEventCategory category) {
        // Sanitize area for query safety
        String cleanArea = area.replaceAll("[^a-zA-Z0-9\\s]", " ").trim();

        String safetyTerms = switch (category != null ? category : SafetyEventCategory.GENERAL_SAFETY) {
            case CRIME_AND_VIOLENCE -> "(robbery OR theft OR assault OR murder OR snatching)";
            case TRAFFIC_AND_TRANSIT -> "(accident OR crash OR collision OR overturn)";
            case FIRE_AND_EXPLOSION -> "(fire OR blaze OR explosion)";
            case NATURAL_DISASTER -> "(flood OR landslide OR cyclone OR cloudburst)";
            case INFRASTRUCTURE_HAZARD -> "(collapse OR \"gas leak\" OR electrocution)";
            case CIVIL_DISTURBANCE -> "(protest OR strike OR curfew OR riot)";
            case GENERAL_SAFETY -> "(accident OR robbery OR theft OR assault OR fire OR flood OR landslide OR protest)";
        };

        return "\"" + cleanArea + "\" AND " + safetyTerms;
    }

    private List<RecentSafetyEventDto> mapArticles(List<GNewsRawResponse.GNewsArticle> articles, String area, NewsQuery query) {
        List<RecentSafetyEventDto> dtos = new ArrayList<>();
        Instant now = Instant.now();
        Instant oldestAllowed = now.minus(Duration.ofDays(properties.maxAgeDays()));
        SafetyEventCategory requestedCategory = query.category();

        for (GNewsRawResponse.GNewsArticle art : articles) {
            if (art.title() == null || art.title().isBlank()) {
                continue;
            }

            // Publication timestamp: unusable timestamp is rejected safely
            Instant publishedAt = parseTimestamp(art.publishedAt());
            if (publishedAt == null) {
                log.debug("Dropped article '{}' due to missing or unusable publication timestamp", art.title());
                continue;
            }

            // Exact duration freshness check
            if (publishedAt.isBefore(oldestAllowed) || publishedAt.isAfter(now.plus(Duration.ofMinutes(15)))) {
                log.debug("Dropped article '{}' published at {} failing freshness window ({} days)",
                        art.title(), publishedAt, properties.maxAgeDays());
                continue;
            }

            String cleanTitle = relevanceFilter.sanitizeText(art.title());
            String cleanDesc = relevanceFilter.sanitizeText(art.description());

            // Location relevance check with hierarchical context and conflict detection
            RelevanceTier relevance;
            if (query.resolutionLevel() != null && !query.resolutionLevel().isBlank()) {
                relevance = relevanceFilter.calculateHierarchicalRelevance(
                        query.locality(), query.district(), query.stateUt(), cleanTitle, cleanDesc
                );
                if (!relevanceFilter.isAcceptableForLevel(query.resolutionLevel(), relevance)) {
                    log.debug("Dropped article '{}' due to insufficient relevance ({}) for level '{}'",
                            cleanTitle, relevance, query.resolutionLevel());
                    continue;
                }
            } else {
                relevance = relevanceFilter.calculateRelevance(area, cleanTitle, cleanDesc);
                if (!relevanceFilter.isAcceptableRelevance(relevance)) {
                    log.debug("Dropped article '{}' due to insufficient relevance ({}) for area '{}'",
                            cleanTitle, relevance, area);
                    continue;
                }
            }

            // Categorization
            SafetyEventCategory category = (requestedCategory != null && requestedCategory != SafetyEventCategory.GENERAL_SAFETY)
                    ? requestedCategory
                    : eventClassifier.classifyCategory(cleanTitle, cleanDesc);

            // Conservative severity
            EventSeverity severity = eventClassifier.classifySeverity(cleanTitle, cleanDesc);

            // Deterministic UUID based on URL and publish time
            String sourceUrl = (art.url() != null && art.url().startsWith("https://")) ? art.url() : "https://news.google.com";
            UUID eventId = UUID.nameUUIDFromBytes((sourceUrl + publishedAt.toString()).getBytes(StandardCharsets.UTF_8));
            String eventGroupId = "grp-" + UUID.nameUUIDFromBytes((cleanTitle + area).getBytes(StandardCharsets.UTF_8));

            String sourceName = (art.source() != null && art.source().name() != null) ? art.source().name() : "News";
            boolean isVerified = VERIFIED_INDIAN_SOURCES.contains(sourceName.toLowerCase(Locale.ROOT));

            dtos.add(new RecentSafetyEventDto(
                    eventId,
                    eventGroupId,
                    cleanTitle,
                    cleanDesc,
                    sourceName,
                    sourceUrl,
                    art.image(),
                    publishedAt,
                    now,
                    category,
                    severity,
                    relevance,
                    area,
                    isVerified,
                    1
            ));
        }

        return dtos;
    }

    private Instant parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
