package com.geoshield.news.service;

import com.geoshield.news.dto.EventSeverity;
import com.geoshield.news.dto.RecentSafetyEventDto;
import com.geoshield.news.dto.RelevanceTier;
import com.geoshield.news.dto.SafetyEventCategory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventDeduplicationServiceTest {

    private EventDeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        deduplicationService = new EventDeduplicationService();
    }

    @Test
    @DisplayName("Folds multiple articles on the same incident into one cluster")
    void foldsDuplicateArticlesIntoSingleCluster() {
        Instant t1 = Instant.parse("2026-09-21T10:00:00Z");
        Instant t2 = Instant.parse("2026-09-21T11:15:00Z");

        RecentSafetyEventDto art1 = new RecentSafetyEventDto(
                UUID.randomUUID(), null,
                "Landslide blocks National Highway 5 near Shimla",
                "Traffic halted on NH-5 after boulder collapse.",
                "Tribune", "https://example.com/1", null,
                t1, t1,
                SafetyEventCategory.NATURAL_DISASTER, EventSeverity.HIGH, RelevanceTier.HIGH,
                "Shimla", true, 1
        );

        RecentSafetyEventDto art2 = new RecentSafetyEventDto(
                UUID.randomUUID(), null,
                "National Highway 5 blocked near Shimla after heavy landslide",
                "Vehicles stranded as landslide hits NH-5.",
                "The Hindu", "https://example.com/2", null,
                t2, t2,
                SafetyEventCategory.NATURAL_DISASTER, EventSeverity.HIGH, RelevanceTier.HIGH,
                "Shimla", true, 1
        );

        List<RecentSafetyEventDto> deduped = deduplicationService.deduplicate(List.of(art1, art2));

        assertThat(deduped).hasSize(1);
        RecentSafetyEventDto canonical = deduped.get(0);
        assertThat(canonical.relatedSourcesCount()).isEqualTo(2);
        assertThat(canonical.eventGroupId()).isNotNull();
    }

    @Test
    @DisplayName("Keeps distinct incidents in different categories separate")
    void keepsDifferentCategoriesSeparate() {
        Instant t1 = Instant.parse("2026-09-21T10:00:00Z");

        RecentSafetyEventDto art1 = new RecentSafetyEventDto(
                UUID.randomUUID(), null,
                "Fire breaks out at commercial complex in Shimla",
                "Fire department dispatched tenders.",
                "Tribune", "https://example.com/1", null,
                t1, t1,
                SafetyEventCategory.FIRE_AND_EXPLOSION, EventSeverity.HIGH, RelevanceTier.HIGH,
                "Shimla", true, 1
        );

        RecentSafetyEventDto art2 = new RecentSafetyEventDto(
                UUID.randomUUID(), null,
                "Protest blocks mall road in Shimla over local grievances",
                "Demonstrators gathered in morning.",
                "The Hindu", "https://example.com/2", null,
                t1, t1,
                SafetyEventCategory.CIVIL_DISTURBANCE, EventSeverity.MODERATE, RelevanceTier.HIGH,
                "Shimla", true, 1
        );

        List<RecentSafetyEventDto> deduped = deduplicationService.deduplicate(List.of(art1, art2));
        assertThat(deduped).hasSize(2);
    }
}
