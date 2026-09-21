package com.geoshield.news.service;

import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.news.config.NewsProperties;
import com.geoshield.news.dto.NewsQuery;
import com.geoshield.news.dto.NewsResponse;
import com.geoshield.news.provider.gnews.GNewsProvider;
import com.geoshield.news.provider.mock.MockNewsProvider;
import com.geoshield.risk.dto.GeographicResolution;
import com.geoshield.risk.service.GeographicResolutionService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsServiceImplTest {

    @Mock
    private GNewsProvider gNewsProvider;

    @Mock
    private GeographicResolutionService geographicResolutionService;

    private MockNewsProvider mockNewsProvider;
    private EventDeduplicationService deduplicationService;
    private NewsServiceImpl newsService;

    @BeforeEach
    void setUp() {
        NewsProperties properties = new NewsProperties(
                true, "mock", "", "https://gnews.io/api/v4",
                Duration.ofSeconds(2), Duration.ofSeconds(2), 45, 200, 72, 10, "en", "in"
        );
        mockNewsProvider = new MockNewsProvider();
        deduplicationService = new EventDeduplicationService();

        newsService = new NewsServiceImpl(
                properties, gNewsProvider, mockNewsProvider,
                geographicResolutionService, deduplicationService
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
                Duration.ofSeconds(2), Duration.ofSeconds(2), 45, 200, 72, 10, "en", "in"
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
                Duration.ofSeconds(2), Duration.ofSeconds(2), 45, 200, 72, 10, "en", "in"
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
                Duration.ofSeconds(2), Duration.ofSeconds(2), 45, 200, 72, 10, "en", "in"
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
}
