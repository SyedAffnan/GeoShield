package com.geoshield.news.controller;

import com.geoshield.news.dto.NewsQuery;
import com.geoshield.news.dto.NewsResponse;
import com.geoshield.news.dto.SafetyEventCategory;
import com.geoshield.news.service.NewsService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing location-based Recent Safety Events for tourist awareness.
 */
@RestController
@RequestMapping("/api/v1/news")
@Validated
public class NewsController {

    private final NewsService newsService;

    @Autowired
    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    /**
     * Retrieves recent location-relevant safety news articles.
     *
     * @param latitude  optional GPS latitude
     * @param longitude optional GPS longitude
     * @param locality  optional client-geocoded locality/town name
     * @param district  optional client-geocoded district name
     * @param category  optional category filter
     * @param limit     maximum number of events to return (1-25, default 10)
     * @return response envelope containing structured safety events
     */
    @GetMapping("/recent")
    public ResponseEntity<NewsResponse> getRecentNews(
            @RequestParam(required = false)
            @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
            @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
            BigDecimal latitude,

            @RequestParam(required = false)
            @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
            @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
            BigDecimal longitude,

            @RequestParam(required = false)
            @Size(max = 60, message = "Locality name must be <= 60 characters")
            String locality,

            @RequestParam(required = false)
            @Size(max = 60, message = "District name must be <= 60 characters")
            String district,

            @RequestParam(required = false)
            String category,

            @RequestParam(required = false, defaultValue = "10")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 25, message = "Limit must be at most 25")
            int limit) {

        SafetyEventCategory parsedCategory = null;
        if (category != null && !category.isBlank()) {
            try {
                parsedCategory = SafetyEventCategory.valueOf(category.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                // If invalid category string, return 400 Bad Request
                return ResponseEntity.badRequest().build();
            }
        }

        NewsQuery query = new NewsQuery(
                latitude,
                longitude,
                locality,
                district,
                null,
                null,
                parsedCategory,
                limit
        );

        NewsResponse response = newsService.getRecentSafetyNews(query);
        return ResponseEntity.ok(response);
    }
}
