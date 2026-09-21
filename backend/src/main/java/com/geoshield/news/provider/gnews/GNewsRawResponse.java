package com.geoshield.news.provider.gnews;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Jackson mapping DTOs for GNews API v4 search response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GNewsRawResponse(
        int totalArticles,
        List<GNewsArticle> articles
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GNewsArticle(
            String title,
            String description,
            String content,
            String url,
            String image,
            String publishedAt,
            GNewsSource source
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GNewsSource(
            String name,
            String url
    ) {}
}
