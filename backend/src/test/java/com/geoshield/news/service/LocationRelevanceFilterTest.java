package com.geoshield.news.service;

import com.geoshield.news.dto.RelevanceTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocationRelevanceFilterTest {

    private LocationRelevanceFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LocationRelevanceFilter();
    }

    @Test
    @DisplayName("Area in headline returns HIGH relevance")
    void areaInHeadlineReturnsHigh() {
        String area = "Shimla";
        String title = "Massive rockfall blocks highway in Shimla district";
        String desc = "Traffic has been diverted following heavy rain.";

        RelevanceTier tier = filter.calculateRelevance(area, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.HIGH);
        assertThat(filter.isAcceptableRelevance(tier)).isTrue();
    }

    @Test
    @DisplayName("Area in first 140 chars of description returns MEDIUM relevance")
    void areaInDescriptionSnippetReturnsMedium() {
        String area = "Bengaluru";
        String title = "Heavy rainfall causes severe waterlogging and power outages";
        String desc = "Several localities in Bengaluru woke up to flooded streets on Monday after torrential downpour.";

        RelevanceTier tier = filter.calculateRelevance(area, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.MEDIUM);
        assertThat(filter.isAcceptableRelevance(tier)).isTrue();
    }

    @Test
    @DisplayName("Area only deep in description returns LOW relevance and is dropped")
    void areaDeepInDescriptionReturnsLow() {
        String area = "Pune";
        String title = "Maharashtra cabinet clears new infrastructure projects across the state";
        String desc = "The state government held an extensive five-hour meeting in Mumbai on Monday afternoon discussing statewide road development plans across Nashik and Nagpur corridors, and toward the very end of the lengthy press briefing officials briefly mentioned Pune in connection with future transport studies.";

        RelevanceTier tier = filter.calculateRelevance(area, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.LOW);
        assertThat(filter.isAcceptableRelevance(tier)).isFalse();
    }

    @Test
    @DisplayName("Area not mentioned at all returns UNKNOWN")
    void areaNotMentionedReturnsUnknown() {
        String area = "Jaipur";
        String title = "Protest breaks out in Lucknow over transport fares";
        String desc = "Commuters faced difficulty in eastern Uttar Pradesh.";

        RelevanceTier tier = filter.calculateRelevance(area, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.UNKNOWN);
        assertThat(filter.isAcceptableRelevance(tier)).isFalse();
    }

    @Test
    @DisplayName("Sanitizes HTML tags and entities properly")
    void sanitizesHtmlTagsProperly() {
        String raw = "<b>Breaking News:</b> Accident on NH-44 &amp; traffic halted.<script>alert('xss')</script>";
        String clean = filter.sanitizeText(raw);

        assertThat(clean).doesNotContain("<b>", "</b>", "<script>", "&amp;");
        assertThat(clean).contains("Breaking News:", "Accident on NH-44", "traffic halted.");
    }
}
