package com.geoshield.news.provider.gnews;

import com.geoshield.news.config.NewsProperties;
import com.geoshield.news.dto.NewsQuery;
import com.geoshield.news.dto.RecentSafetyEventDto;
import com.geoshield.news.dto.SafetyEventCategory;
import com.geoshield.news.service.EventClassifier;
import com.geoshield.news.service.LocationRelevanceFilter;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

class GNewsProviderTest {

    private MockRestServiceServer mockServer;
    private GNewsProvider gNewsProvider;

    @BeforeEach
    void setUp() {
        NewsProperties properties = new NewsProperties(
                true, "gnews", "test-api-key", "https://gnews.test/api/v4",
                Duration.ofSeconds(2), Duration.ofSeconds(2), 45, 200, 72, 10, "en", "in", 7
        );

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl());
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        EventClassifier eventClassifier = new EventClassifier();
        LocationRelevanceFilter relevanceFilter = new LocationRelevanceFilter();

        gNewsProvider = new GNewsProvider(properties, restClient, eventClassifier, relevanceFilter);
    }

    @Test
    @DisplayName("Parses valid GNews response and maps to domain DTOs")
    void parsesValidGNewsResponse() {
        String jsonPayload = """
                {
                  "totalArticles": 1,
                  "articles": [
                    {
                      "title": "Shimla highway cleared after rockslide near Dhalli",
                      "description": "Authorities in Shimla successfully restored traffic flow on Monday.",
                      "content": "Full article content...",
                      "url": "https://example.com/shimla-road",
                      "image": "https://example.com/img.jpg",
                      "publishedAt": "2026-09-21T09:00:00Z",
                      "source": {
                        "name": "The Hindu",
                        "url": "https://thehindu.com"
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(containsString("/search?q=")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonPayload, MediaType.APPLICATION_JSON));

        NewsQuery query = new NewsQuery(null, null, "Shimla", null, "Shimla", "LOCALITY", SafetyEventCategory.TRAFFIC_AND_TRANSIT, 10);
        List<RecentSafetyEventDto> events = gNewsProvider.fetchSafetyNews(query);

        assertThat(events).hasSize(1);
        RecentSafetyEventDto event = events.get(0);
        assertThat(event.title()).contains("Shimla highway cleared");
        assertThat(event.sourceName()).isEqualTo("The Hindu");
        assertThat(event.isVerifiedSource()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("Handles server error (5xx) gracefully without throwing")
    void handlesServerErrorGracefully() {
        mockServer.expect(requestTo(containsString("/search?q=")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        NewsQuery query = new NewsQuery(null, null, "Delhi", null, "Delhi", "LOCALITY", null, 10);
        List<RecentSafetyEventDto> events = gNewsProvider.fetchSafetyNews(query);

        assertThat(events).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Returns empty when provider has no API key")
    void returnsEmptyWhenNoApiKey() {
        NewsProperties unconfiguredProps = new NewsProperties(
                true, "gnews", "", "https://gnews.test/api/v4",
                Duration.ofSeconds(2), Duration.ofSeconds(2), 45, 200, 72, 10, "en", "in", 7
        );
        GNewsProvider unconfiguredProvider = new GNewsProvider(
                unconfiguredProps, RestClient.builder().build(), new EventClassifier(), new LocationRelevanceFilter()
        );

        assertThat(unconfiguredProvider.isAvailable()).isFalse();
        List<RecentSafetyEventDto> events = unconfiguredProvider.fetchSafetyNews(new NewsQuery(null, null, "Delhi", null, "Delhi", "LOCALITY", null, 10));
        assertThat(events).isEmpty();
    }
}
