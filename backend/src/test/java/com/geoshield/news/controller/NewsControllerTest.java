package com.geoshield.news.controller;

import com.geoshield.news.dto.NewsQuery;
import com.geoshield.news.dto.NewsResponse;
import com.geoshield.news.dto.SafetyEventCategory;
import com.geoshield.news.service.NewsService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsControllerTest {

    @Mock
    private NewsService newsService;

    private NewsController newsController;

    @BeforeEach
    void setUp() {
        newsController = new NewsController(newsService);
    }

    @Test
    @DisplayName("Returns 200 OK with news response on valid request")
    void getRecentNewsReturnsOk() {
        NewsResponse mockResponse = new NewsResponse(
                "Shimla", "LOCALITY", Instant.now(), false, true, 0, List.of()
        );
        when(newsService.getRecentSafetyNews(any(NewsQuery.class))).thenReturn(mockResponse);

        ResponseEntity<NewsResponse> response = newsController.getRecentNews(
                new BigDecimal("31.1048"),
                new BigDecimal("77.1734"),
                "Shimla",
                "Shimla",
                "Himachal Pradesh",
                "NATURAL_DISASTER",
                10
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().resolvedArea()).isEqualTo("Shimla");
        assertThat(response.getBody().providerAvailable()).isTrue();
        verify(newsService).getRecentSafetyNews(any(NewsQuery.class));
    }

    @Test
    @DisplayName("Returns 400 Bad Request when category string is invalid")
    void returnsBadRequestOnInvalidCategory() {
        ResponseEntity<NewsResponse> response = newsController.getRecentNews(
                null, null, "Delhi", null, null, "INVALID_CATEGORY_NAME", 10
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Passes sanitized locality and default limit when omitted")
    void passesLocalityAndDefaultLimit() {
        NewsResponse mockResponse = NewsResponse.empty("Bengaluru", "LOCALITY", true);
        when(newsService.getRecentSafetyNews(any(NewsQuery.class))).thenReturn(mockResponse);

        ResponseEntity<NewsResponse> response = newsController.getRecentNews(
                null, null, "Bengaluru", null, null, null, 10
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(newsService).getRecentSafetyNews(any(NewsQuery.class));
    }
}
