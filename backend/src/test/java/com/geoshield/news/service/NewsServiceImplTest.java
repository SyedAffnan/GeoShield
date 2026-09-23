package com.geoshield.news.service;

import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.news.config.NewsProperties;
import com.geoshield.news.dto.NewsQuery;
import com.geoshield.news.dto.NewsResponse;
import com.geoshield.news.provider.gnews.GNewsProvider;
import com.geoshield.news.provider.mock.MockNewsProvider;
import com.geoshield.risk.dto.GeographicResolution;
import com.geoshield.risk.service.GeographicResolutionService;
import com.geoshield.emergencyservices.dto.EmergencyServiceCenterResponse;
import com.geoshield.emergencyservices.dto.NearestFacilityResult;
import com.geoshield.emergencyservices.entity.CenterType;
import com.geoshield.emergencyservices.service.EmergencyServicesService;
import com.geoshield.news.dto.EventSeverity;
import com.geoshield.news.dto.RecentSafetyEventDto;
import com.geoshield.news.dto.RelevanceTier;
import com.geoshield.news.dto.SafetyEventCategory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsServiceImplTest {

    @Mock
    private GNewsProvider gNewsProvider;

    @Mock
    private GeographicResolutionService geographicResolutionService;

    @Mock
    private EmergencyServicesService emergencyServicesService;

    private MockNewsProvider mockNewsProvider;
    private EventDeduplicationService deduplicationService;
    private NewsServiceImpl newsService;

    @BeforeEach
    void setUp() {
        NewsProperties properties = new NewsProperties(
                true, "mock", "", "https://gnews.io/api/v4",
                Duration.ofSeconds(2), Duration.ofSeconds(2), 45, 200, 72, 10, "en", "in", 7
        );
        mockNewsProvider = new MockNewsProvider();
        deduplicationService = new EventDeduplicationService();

        newsService = new NewsServiceImpl(
                properties, gNewsProvider, mockNewsProvider,
                geographicResolutionService, deduplicationService,
                emergencyServicesService
        );
    }

    @Test
    @DisplayName("Prefers locality over district and coordinates")
    void prefersLocalityOverOtherResolutions() {
        NewsQuery query = new NewsQuery(
                new BigDecimal("28.6139"), new BigDecimal("77.2090"),
                "Manali", "Kullu", null, null, null, 10
        );

        NewsResponse response = newsService.getRecentSafetyNews(query);

        assertThat(response.resolvedArea()).isEqualTo("Manali");
        assertThat(response.resolutionLevel()).isEqualTo("LOCALITY");
        assertThat(response.providerAvailable()).isTrue();
        assertThat(response.events()).isNotEmpty();
    }

    @Test
    @DisplayName("Uses district when locality is missing")
    void usesDistrictWhenLocalityMissing() {
        NewsQuery query = new NewsQuery(
                new BigDecimal("28.6139"), new BigDecimal("77.2090"),
                null, "Kullu", null, null, null, 10
        );

        NewsResponse response = newsService.getRecentSafetyNews(query);

        assertThat(response.resolvedArea()).isEqualTo("Kullu");
        assertThat(response.resolutionLevel()).isEqualTo("DISTRICT");
    }

    @Test
    @DisplayName("Uses State/UT fallback when locality and district are missing")
    void usesStateUtFallbackWhenLocalityAndDistrictMissing() {
        when(geographicResolutionService.resolve(any(), any()))
                .thenReturn(GeographicResolution.resolved(GeographicLevel.STATE_UT, "Himachal Pradesh"));

        NewsQuery query = new NewsQuery(
                new BigDecimal("31.1048"), new BigDecimal("77.1734"),
                null, null, null, null, null, 10
        );

        NewsResponse response = newsService.getRecentSafetyNews(query);

        assertThat(response.resolvedArea()).isEqualTo("Himachal Pradesh");
        assertThat(response.resolutionLevel()).isEqualTo("STATE_UT");
    }

    @Test
    @DisplayName("Caches result and marks subsequent queries as cached")
    void cachesResultOnConsecutiveCalls() {
        NewsQuery query = new NewsQuery(null, null, "Shimla", null, null, null, null, 10);

        NewsResponse first = newsService.getRecentSafetyNews(query);
        assertThat(first.cached()).isFalse();

        NewsResponse second = newsService.getRecentSafetyNews(query);
        assertThat(second.cached()).isTrue();
        assertThat(second.resolvedArea()).isEqualTo(first.resolvedArea());
    }

    @Test
    @DisplayName("When news is disabled, returns unavailable NewsResponse without mock fixtures (even if provider=mock)")
    void whenDisabled_returnsUnavailableWithoutMockFixtures() {
        NewsProperties disabledProps = new NewsProperties(
                false, "mock", "", "https://gnews.io/api/v4",
                Duration.ofSeconds(2), Duration.ofSeconds(2), 45, 200, 72, 10, "en", "in", 7
        );
        NewsServiceImpl localService = new NewsServiceImpl(
                disabledProps, gNewsProvider, mockNewsProvider,
                geographicResolutionService, deduplicationService
        );

        NewsQuery query = new NewsQuery(null, null, "Manali", null, null, null, null, 10);
        NewsResponse response = localService.getRecentSafetyNews(query);

        assertThat(response.providerAvailable()).isFalse();
        assertThat(response.eventsCount()).isEqualTo(0);
        assertThat(response.events()).isEmpty();
    }

    @Test
    @DisplayName("When news is enabled and provider is mock, uses MockNewsProvider intentionally")
    void whenExplicitMock_andEnabled_returnsMockFixtures() {
        NewsQuery query = new NewsQuery(null, null, "Manali", null, null, null, null, 10);
        NewsResponse response = newsService.getRecentSafetyNews(query);

        assertThat(response.providerAvailable()).isTrue();
        assertThat(response.eventsCount()).isGreaterThan(0);
        assertThat(response.events()).isNotEmpty();
        assertThat(response.events().get(0).title()).contains("Manali");
    }

    @Test
    @DisplayName("When news is enabled and provider is gnews but API key is missing/blank, returns unavailable without network requests")
    void whenEnabledGNews_withoutKey_returnsUnavailable() {
        NewsProperties noKeyProps = new NewsProperties(
                true, "gnews", "", "https://gnews.io/api/v4",
                Duration.ofSeconds(2), Duration.ofSeconds(2), 45, 200, 72, 10, "en", "in", 7
        );
        NewsServiceImpl localService = new NewsServiceImpl(
                noKeyProps, gNewsProvider, mockNewsProvider,
                geographicResolutionService, deduplicationService
        );
        when(gNewsProvider.isAvailable()).thenReturn(false);

        NewsQuery query = new NewsQuery(null, null, "Jaipur", null, null, null, null, 10);
        NewsResponse response = localService.getRecentSafetyNews(query);

        assertThat(response.providerAvailable()).isFalse();
        assertThat(response.eventsCount()).isEqualTo(0);
        assertThat(response.events()).isEmpty();
    }

    @Test
    @DisplayName("When news is enabled and provider is gnews with valid API key, delegates to GNewsProvider")
    void whenEnabledGNews_withValidKey_delegatesToGNewsProvider() {
        NewsProperties gnewsProps = new NewsProperties(
                true, "gnews", "valid-api-key", "https://gnews.io/api/v4",
                Duration.ofSeconds(2), Duration.ofSeconds(2), 45, 200, 72, 10, "en", "in", 7
        );
        NewsServiceImpl localService = new NewsServiceImpl(
                gnewsProps, gNewsProvider, mockNewsProvider,
                geographicResolutionService, deduplicationService
        );
        when(gNewsProvider.isAvailable()).thenReturn(true);
        when(gNewsProvider.fetchSafetyNews(any())).thenReturn(List.of());

        NewsQuery query = new NewsQuery(null, null, "Jaipur", null, null, null, null, 10);
        NewsResponse response = localService.getRecentSafetyNews(query);

        assertThat(response.providerAvailable()).isTrue();
        assertThat(response.eventsCount()).isEqualTo(0);
        verify(gNewsProvider).fetchSafetyNews(any());
    }

    @Test
    @DisplayName("When using mock provider, returns multiple distinct fixtures with valid URL slugs and respects category filter")
    void mockProvider_returnsMultipleFixtures_andFiltersByCategory() {
        NewsQuery generalQuery = new NewsQuery(null, null, "Tamil Nadu", null, null, null, null, 10);
        NewsResponse generalResponse = newsService.getRecentSafetyNews(generalQuery);

        assertThat(generalResponse.providerAvailable()).isTrue();
        assertThat(generalResponse.events()).hasSize(3);
        assertThat(generalResponse.events().get(0).sourceUrl()).doesNotContain(" ");
        assertThat(generalResponse.events().get(0).title()).contains("Tamil Nadu");

        newsService.clearCache();

        NewsQuery trafficQuery = new NewsQuery(null, null, "Tamil Nadu", null, null, null, com.geoshield.news.dto.SafetyEventCategory.TRAFFIC_AND_TRANSIT, 10);
        NewsResponse trafficResponse = newsService.getRecentSafetyNews(trafficQuery);

        assertThat(trafficResponse.providerAvailable()).isTrue();
        assertThat(trafficResponse.events()).hasSize(1);
        assertThat(trafficResponse.events().get(0).category()).isEqualTo(com.geoshield.news.dto.SafetyEventCategory.TRAFFIC_AND_TRANSIT);
    }

    @Test
    @DisplayName("Hierarchical retrieval: falls back from locality to district when locality produces no events")
    void hierarchicalFallback_fromLocalityToDistrict() {
        NewsProperties gnewsProps = new NewsProperties(
                true, "gnews", "valid-key", "https://gnews.io/api/v4",
                Duration.ofSeconds(2), Duration.ofSeconds(2), 45, 200, 72, 10, "en", "in", 7
        );
        NewsServiceImpl localService = new NewsServiceImpl(
                gnewsProps, gNewsProvider, mockNewsProvider,
                geographicResolutionService, deduplicationService,
                emergencyServicesService
        );

        when(gNewsProvider.isAvailable()).thenReturn(true);
        when(gNewsProvider.fetchSafetyNews(argThat(q -> q != null && "Manali".equals(q.resolvedArea())))).thenReturn(List.of());

        RecentSafetyEventDto kulluEvent = new RecentSafetyEventDto(
                UUID.randomUUID(), null,
                "Heavy rainfall blocks road in Kullu",
                "Traffic diverted across Kullu valley.",
                "Tribune", "https://example.com/kullu", null,
                Instant.now(), Instant.now(),
                SafetyEventCategory.NATURAL_DISASTER, EventSeverity.HIGH, RelevanceTier.HIGH,
                "Kullu", true, 1
        );
        when(gNewsProvider.fetchSafetyNews(argThat(q -> q != null && "Kullu".equals(q.resolvedArea())))).thenReturn(List.of(kulluEvent));

        NewsQuery query = new NewsQuery(null, null, "Manali", "Kullu", null, null, null, 10);
        NewsResponse response = localService.getRecentSafetyNews(query);

        assertThat(response.resolvedArea()).isEqualTo("Kullu");
        assertThat(response.resolutionLevel()).isEqualTo("DISTRICT");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).title()).contains("Kullu");
    }

    @Test
    @DisplayName("Server-side reverse resolution: falls back to nearest emergency facility city when coordinates provided without locality")
    void serverSideReverseResolution_viaEmergencyServiceNearestFacility() {
        EmergencyServiceCenterResponse facility = new EmergencyServiceCenterResponse(
                1L, "osm-101", "District Headquarters Hospital",
                CenterType.MEDICAL,
                new BigDecimal("11.3410"), new BigDecimal("77.7172"),
                "Erode", "Tamil Nadu", "0424-222222"
        );
        when(emergencyServicesService.findNearestFacility(11.3410, 77.7172))
                .thenReturn(Optional.of(new NearestFacilityResult(facility, 3.2)));

        NewsQuery query = new NewsQuery(
                new BigDecimal("11.3410"), new BigDecimal("77.7172"),
                null, null, null, null, null, 10
        );
        NewsResponse response = newsService.getRecentSafetyNews(query);

        assertThat(response.resolvedArea()).isEqualTo("Erode");
        assertThat(response.resolutionLevel()).isEqualTo("LOCALITY");
    }

    @Test
    @DisplayName("Freshness: 1 day old article is accepted")
    void freshness_oneDayOld_accepted() {
        Instant now = Instant.parse("2026-09-23T12:00:00Z");
        Instant pub = now.minus(Duration.ofDays(1));
        assertThat(newsService.isFresh(pub, now, 7)).isTrue();
    }

    @Test
    @DisplayName("Freshness: 6 days old article is accepted")
    void freshness_sixDaysOld_accepted() {
        Instant now = Instant.parse("2026-09-23T12:00:00Z");
        Instant pub = now.minus(Duration.ofDays(6));
        assertThat(newsService.isFresh(pub, now, 7)).isTrue();
    }

    @Test
    @DisplayName("Freshness: Exactly at 7-day boundary is accepted")
    void freshness_exactlyAtBoundary_accepted() {
        Instant now = Instant.parse("2026-09-23T12:00:00Z");
        Instant pub = now.minus(Duration.ofDays(7));
        assertThat(newsService.isFresh(pub, now, 7)).isTrue();
    }

    @Test
    @DisplayName("Freshness: Older than 7 days is rejected")
    void freshness_olderThanSevenDays_rejected() {
        Instant now = Instant.parse("2026-09-23T12:00:00Z");
        Instant pub = now.minus(Duration.ofDays(7)).minusSeconds(1);
        assertThat(newsService.isFresh(pub, now, 7)).isFalse();
    }

    @Test
    @DisplayName("Freshness: 15 days old article is rejected")
    void freshness_fifteenDaysOld_rejected() {
        Instant now = Instant.parse("2026-09-23T12:00:00Z");
        Instant pub = now.minus(Duration.ofDays(15));
        assertThat(newsService.isFresh(pub, now, 7)).isFalse();
    }

    @Test
    @DisplayName("Freshness: Future timestamp beyond clock skew tolerance is rejected")
    void freshness_futureTimestamp_rejected() {
        Instant now = Instant.parse("2026-09-23T12:00:00Z");
        Instant pub = now.plus(Duration.ofHours(2));
        assertThat(newsService.isFresh(pub, now, 7)).isFalse();
    }

    @Test
    @DisplayName("Freshness: Null publication timestamp is rejected safely")
    void freshness_nullTimestamp_rejected() {
        Instant now = Instant.parse("2026-09-23T12:00:00Z");
        assertThat(newsService.isFresh(null, now, 7)).isFalse();
    }

    @Test
    @DisplayName("Freshness: Out of order articles are sorted newest publication first")
    void outOfOrderArticles_sortedNewestFirst() {
        Instant fixedNow = Instant.parse("2026-09-23T12:00:00Z");
        Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

        NewsProperties gnewsProps = new NewsProperties(
                true, "gnews", "valid-key", "https://gnews.io/api/v4",
                Duration.ofSeconds(2), Duration.ofSeconds(2), 45, 200, 72, 10, "en", "in", 7
        );
        NewsServiceImpl localService = new NewsServiceImpl(
                gnewsProps, gNewsProvider, mockNewsProvider,
                geographicResolutionService, deduplicationService,
                new LocationRelevanceFilter(), emergencyServicesService, fixedClock
        );

        when(gNewsProvider.isAvailable()).thenReturn(true);

        Instant olderTime = fixedNow.minus(Duration.ofDays(3));
        Instant newerTime = fixedNow.minus(Duration.ofDays(1));

        RecentSafetyEventDto olderEvent = new RecentSafetyEventDto(
                UUID.randomUUID(), null, "Accident in Erode highway", "Collision on road.",
                "Source A", "https://example.com/erode-old", null,
                olderTime, fixedNow, SafetyEventCategory.TRAFFIC_AND_TRANSIT,
                EventSeverity.HIGH, RelevanceTier.HIGH, "Erode", true, 1
        );
        RecentSafetyEventDto newerEvent = new RecentSafetyEventDto(
                UUID.randomUUID(), null, "Waterlogging in Erode town", "Flooding on road.",
                "Source B", "https://example.com/erode-new", null,
                newerTime, fixedNow, SafetyEventCategory.NATURAL_DISASTER,
                EventSeverity.HIGH, RelevanceTier.HIGH, "Erode", true, 1
        );

        // GNews returns older event first, then newer
        when(gNewsProvider.fetchSafetyNews(any())).thenReturn(List.of(olderEvent, newerEvent));

        NewsQuery query = new NewsQuery(null, null, null, "Erode", "Tamil Nadu", null, null, null, 10);
        NewsResponse response = localService.getRecentSafetyNews(query);

        assertThat(response.events()).hasSize(2);
        // Assert newest publication is first
        assertThat(response.events().get(0).publishedAt()).isEqualTo(newerTime);
        assertThat(response.events().get(1).publishedAt()).isEqualTo(olderTime);
    }

    @Test
    @DisplayName("Regression: Kerala Bhavani River article rejected from Bhavani Tamil Nadu local results and falls back to Erode district")
    void regression_keralaArticleRejected_fallsBackToDistrict() {
        Instant fixedNow = Instant.parse("2026-09-23T12:00:00Z");
        Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

        NewsProperties gnewsProps = new NewsProperties(
                true, "gnews", "valid-key", "https://gnews.io/api/v4",
                Duration.ofSeconds(2), Duration.ofSeconds(2), 45, 200, 72, 10, "en", "in", 7
        );
        NewsServiceImpl localService = new NewsServiceImpl(
                gnewsProps, gNewsProvider, mockNewsProvider,
                geographicResolutionService, deduplicationService,
                new LocationRelevanceFilter(), emergencyServicesService, fixedClock
        );

        when(gNewsProvider.isAvailable()).thenReturn(true);

        // Candidate 1 (Bhavani locality query): GNews returns Kerala article with Bhavani River
        RecentSafetyEventDto keralaArticle = new RecentSafetyEventDto(
                UUID.randomUUID(), null,
                "Body parts in Palakkad river: Bhavani River murder investigation",
                "Police probe crime in Attappadi, Palakkad, Kerala with Fire Force assistance.",
                "Onmanorama", "https://www.onmanorama.com/news/kerala/2026/09/07/bhavani-river-murder-investigation.html",
                null,
                fixedNow.minus(Duration.ofDays(2)), // fresh in this test, but geographic conflict with Kerala
                fixedNow,
                SafetyEventCategory.CRIME_AND_VIOLENCE,
                EventSeverity.CRITICAL,
                RelevanceTier.HIGH,
                "Bhavani",
                true,
                1
        );
        when(gNewsProvider.fetchSafetyNews(argThat(q -> q != null && "Bhavani".equals(q.resolvedArea()))))
                .thenReturn(List.of(keralaArticle));

        // Candidate 2 (Erode district query): GNews returns valid Erode Tamil Nadu event
        RecentSafetyEventDto erodeArticle = new RecentSafetyEventDto(
                UUID.randomUUID(), null,
                "Erode district police conduct vehicle inspection on state highway",
                "Checkposts tightened across Erode, Tamil Nadu.",
                "The Hindu", "https://thehindu.com/erode-police",
                null,
                fixedNow.minus(Duration.ofHours(5)),
                fixedNow,
                SafetyEventCategory.GENERAL_SAFETY,
                EventSeverity.LOW,
                RelevanceTier.MEDIUM,
                "Erode",
                true,
                1
        );
        when(gNewsProvider.fetchSafetyNews(argThat(q -> q != null && "Erode".equals(q.resolvedArea()))))
                .thenReturn(List.of(erodeArticle));

        NewsQuery touristQuery = new NewsQuery(null, null, "Bhavani", "Erode", "Tamil Nadu", null, null, null, 10);
        NewsResponse response = localService.getRecentSafetyNews(touristQuery);

        // Locality Bhavani dropped Kerala article due to state conflict, fell back cleanly to Erode District
        assertThat(response.resolvedArea()).isEqualTo("Erode");
        assertThat(response.resolutionLevel()).isEqualTo("DISTRICT");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).title()).contains("Erode district police");
    }
}
