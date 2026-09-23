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

    @Test
    @DisplayName("Folds articles with canonical URL match even if query params differ")
    void foldsArticlesWithCanonicalUrlMatch() {
        Instant t = Instant.parse("2026-09-21T10:00:00Z");

        RecentSafetyEventDto art1 = new RecentSafetyEventDto(
                UUID.randomUUID(), null,
                "Landslide hits NH-5", "Traffic stopped.",
                "Publisher A", "https://example.com/news/landslide?utm_source=twitter&utm_medium=social", null,
                t, t, SafetyEventCategory.NATURAL_DISASTER, EventSeverity.HIGH, RelevanceTier.HIGH,
                "Shimla", true, 1
        );

        RecentSafetyEventDto art2 = new RecentSafetyEventDto(
                UUID.randomUUID(), null,
                "Massive rockfall on highway", "Commuters stranded.",
                "Publisher B", "https://example.com/news/landslide?ref=rss&utm_campaign=breaking", null,
                t, t, SafetyEventCategory.NATURAL_DISASTER, EventSeverity.HIGH, RelevanceTier.HIGH,
                "Shimla", true, 1
        );

        List<RecentSafetyEventDto> deduped = deduplicationService.deduplicate(List.of(art1, art2));
        assertThat(deduped).hasSize(1);
        assertThat(deduped.get(0).relatedSourcesCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Folds cross-publisher articles with publisher suffixes stripped and shared entity")
    void foldsCrossPublisherArticlesWithStrippedSuffixes() {
        Instant t1 = Instant.parse("2026-09-21T12:00:00Z");
        Instant t2 = Instant.parse("2026-09-21T12:30:00Z");

        RecentSafetyEventDto art1 = new RecentSafetyEventDto(
                UUID.randomUUID(), null,
                "Massive landslide blocks NH-44 near Ramban - The Hindu",
                "Traffic halted after boulder collapse on NH-44 highway.",
                "The Hindu", "https://thehindu.com/news/nh44-landslide", null,
                t1, t1, SafetyEventCategory.NATURAL_DISASTER, EventSeverity.HIGH, RelevanceTier.HIGH,
                "Ramban", true, 1
        );

        RecentSafetyEventDto art2 = new RecentSafetyEventDto(
                UUID.randomUUID(), null,
                "NH-44 blocked near Ramban following heavy rockfall | Times of India",
                "Vehicles stranded on national highway NH-44.",
                "Times of India", "https://timesofindia.indiatimes.com/city/nh44-blocked", null,
                t2, t2, SafetyEventCategory.NATURAL_DISASTER, EventSeverity.HIGH, RelevanceTier.HIGH,
                "Ramban", true, 1
        );

        List<RecentSafetyEventDto> deduped = deduplicationService.deduplicate(List.of(art1, art2));
        assertThat(deduped).hasSize(1);
        assertThat(deduped.get(0).relatedSourcesCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Anti-false-merge: keeps distinct incidents in same city separate when entities and actions differ")
    void keepsDistinctIncidentsInSameCitySeparate() {
        Instant t = Instant.parse("2026-09-21T10:00:00Z");

        RecentSafetyEventDto art1 = new RecentSafetyEventDto(
                UUID.randomUUID(), null,
                "Fire breaks out at industrial chemical warehouse in Manali",
                "Fire tenders deployed to industrial area.",
                "Publisher A", "https://example.com/fire-warehouse", null,
                t, t, SafetyEventCategory.FIRE_AND_EXPLOSION, EventSeverity.HIGH, RelevanceTier.HIGH,
                "Manali", true, 1
        );

        RecentSafetyEventDto art2 = new RecentSafetyEventDto(
                UUID.randomUUID(), null,
                "Kitchen cylinder blast reported at mall road restaurant in Manali",
                "Two workers injured in kitchen explosion.",
                "Publisher B", "https://example.com/cylinder-blast", null,
                t, t, SafetyEventCategory.FIRE_AND_EXPLOSION, EventSeverity.MODERATE, RelevanceTier.HIGH,
                "Manali", true, 1
        );

        List<RecentSafetyEventDto> deduped = deduplicationService.deduplicate(List.of(art1, art2));
        assertThat(deduped).hasSize(2);
    }

    @Test
    @DisplayName("Temporal boundary: does not merge articles more than 48 hours apart")
    void doesNotMergeArticlesBeyondTimeThreshold() {
        Instant t1 = Instant.parse("2026-09-10T10:00:00Z");
        Instant t2 = Instant.parse("2026-09-15T10:00:00Z");

        RecentSafetyEventDto art1 = new RecentSafetyEventDto(
                UUID.randomUUID(), null,
                "Protest blocks highway near Shimla", "Commuters face delays.",
                "Publisher A", "https://example.com/protest-1", null,
                t1, t1, SafetyEventCategory.CIVIL_DISTURBANCE, EventSeverity.MODERATE, RelevanceTier.HIGH,
                "Shimla", true, 1
        );

        RecentSafetyEventDto art2 = new RecentSafetyEventDto(
                UUID.randomUUID(), null,
                "Protest blocks highway near Shimla", "Commuters face delays.",
                "Publisher B", "https://example.com/protest-2", null,
                t2, t2, SafetyEventCategory.CIVIL_DISTURBANCE, EventSeverity.MODERATE, RelevanceTier.HIGH,
                "Shimla", true, 1
        );

        List<RecentSafetyEventDto> deduped = deduplicationService.deduplicate(List.of(art1, art2));
        assertThat(deduped).hasSize(2);
    }

    @Test
    @DisplayName("Deduplication 1: Same article, same URL is folded into single canonical event")
    void sameArticleSameUrl_foldsIntoSingleEvent() {
        Instant t = Instant.parse("2026-09-21T10:00:00Z");
        RecentSafetyEventDto art1 = new RecentSafetyEventDto(
                UUID.randomUUID(), null, "Accident on NH-44 near Salem", "Two vehicles collided.",
                "Publisher A", "https://example.com/salem-crash", null,
                t, t, SafetyEventCategory.TRAFFIC_AND_TRANSIT, EventSeverity.HIGH, RelevanceTier.HIGH,
                "Salem", true, 1
        );
        RecentSafetyEventDto art2 = new RecentSafetyEventDto(
                UUID.randomUUID(), null, "Accident on NH-44 near Salem", "Two vehicles collided.",
                "Publisher A", "https://example.com/salem-crash", null,
                t, t, SafetyEventCategory.TRAFFIC_AND_TRANSIT, EventSeverity.HIGH, RelevanceTier.HIGH,
                "Salem", true, 1
        );

        List<RecentSafetyEventDto> deduped = deduplicationService.deduplicate(List.of(art1, art2));
        assertThat(deduped).hasSize(1);
        assertThat(deduped.get(0).relatedSourcesCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Deduplication 3: Similar wording but genuinely different events are NOT merged")
    void similarWording_differentEvents_notMerged() {
        Instant t = Instant.parse("2026-09-21T10:00:00Z");
        RecentSafetyEventDto art1 = new RecentSafetyEventDto(
                UUID.randomUUID(), null, "Three injured in private car collision on northern bypass",
                "Sedan collided with tree on northern bypass early Monday morning.",
                "Publisher A", "https://example.com/car-crash", null,
                t, t, SafetyEventCategory.TRAFFIC_AND_TRANSIT, EventSeverity.MODERATE, RelevanceTier.HIGH,
                "Erode", true, 1
        );
        RecentSafetyEventDto art2 = new RecentSafetyEventDto(
                UUID.randomUUID(), null, "Cargo truck overturned on southern expressway junction",
                "Heavy container carrier overturned on southern expressway blocking freight lane.",
                "Publisher B", "https://example.com/truck-overturn", null,
                t, t, SafetyEventCategory.TRAFFIC_AND_TRANSIT, EventSeverity.HIGH, RelevanceTier.HIGH,
                "Erode", true, 1
        );

        List<RecentSafetyEventDto> deduped = deduplicationService.deduplicate(List.of(art1, art2));
        assertThat(deduped).hasSize(2);
    }

    @Test
    @DisplayName("Deduplication 6: Different incidents involving the same place/person are NOT merged")
    void differentIncidents_samePlaceOrPerson_notMerged() {
        Instant t = Instant.parse("2026-09-21T10:00:00Z");
        RecentSafetyEventDto art1 = new RecentSafetyEventDto(
                UUID.randomUUID(), null, "Council leader inspects civic water drainage facility in Bhavani",
                "Civic leader visited municipal drainage ward following heavy monsoon rains.",
                "Publisher A", "https://example.com/civic-visit", null,
                t, t, SafetyEventCategory.GENERAL_SAFETY, EventSeverity.LOW, RelevanceTier.HIGH,
                "Bhavani", true, 1
        );
        RecentSafetyEventDto art2 = new RecentSafetyEventDto(
                UUID.randomUUID(), null, "Citizens stage protest against civic leader over water contamination in Bhavani",
                "Residents gathered outside municipal office demanding clean drinking water supply.",
                "Publisher B", "https://example.com/protest-civic", null,
                t, t, SafetyEventCategory.CIVIL_DISTURBANCE, EventSeverity.MODERATE, RelevanceTier.HIGH,
                "Bhavani", true, 1
        );

        List<RecentSafetyEventDto> deduped = deduplicationService.deduplicate(List.of(art1, art2));
        assertThat(deduped).hasSize(2);
    }

    @Test
    @DisplayName("Deduplication 7: Same broad region but clearly different incidents are NOT merged")
    void sameBroadRegion_clearlyDifferentIncidents_notMerged() {
        Instant t = Instant.parse("2026-09-21T10:00:00Z");
        RecentSafetyEventDto art1 = new RecentSafetyEventDto(
                UUID.randomUUID(), null, "Landslide blocks hill highway in Nilgiris ghat section",
                "Rockfall disrupts travel along mountain corridor in Nilgiris.",
                "Publisher A", "https://example.com/nilgiris-landslide", null,
                t, t, SafetyEventCategory.NATURAL_DISASTER, EventSeverity.HIGH, RelevanceTier.LOW,
                "Tamil Nadu", true, 1
        );
        RecentSafetyEventDto art2 = new RecentSafetyEventDto(
                UUID.randomUUID(), null, "Chemical warehouse fire breaks out in coastal industrial park",
                "Fire tenders deployed to extinguish warehouse blaze near harbor.",
                "Publisher B", "https://example.com/harbor-fire", null,
                t, t, SafetyEventCategory.FIRE_AND_EXPLOSION, EventSeverity.HIGH, RelevanceTier.LOW,
                "Tamil Nadu", true, 1
        );

        List<RecentSafetyEventDto> deduped = deduplicationService.deduplicate(List.of(art1, art2));
        assertThat(deduped).hasSize(2);
    }
}
