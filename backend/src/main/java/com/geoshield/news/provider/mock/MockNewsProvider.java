package com.geoshield.news.provider.mock;

import com.geoshield.news.dto.EventSeverity;
import com.geoshield.news.dto.NewsQuery;
import com.geoshield.news.dto.RecentSafetyEventDto;
import com.geoshield.news.dto.RelevanceTier;
import com.geoshield.news.dto.SafetyEventCategory;
import com.geoshield.news.provider.NewsProvider;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic mock provider supplying synthetic safety event fixtures for unit tests
 * and development without external network dependency.
 */
@Component
public class MockNewsProvider implements NewsProvider {

    private boolean available = true;

    @Override
    public List<RecentSafetyEventDto> fetchSafetyNews(NewsQuery query) {
        if (!available) {
            return List.of();
        }

        String area = (query.resolvedArea() != null && !query.resolvedArea().isBlank())
                ? query.resolvedArea()
                : "Shimla";

        String areaSlug = area.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");

        Instant now = Instant.now();
        List<RecentSafetyEventDto> events = new ArrayList<>();

        // Fixture 1: High relevance natural disaster / traffic block
        Instant time1 = now.minus(Duration.ofHours(3));
        UUID id1 = UUID.nameUUIDFromBytes(("mock-1-" + area).getBytes(StandardCharsets.UTF_8));
        events.add(new RecentSafetyEventDto(
                id1,
                "grp-mock-1",
                "[Test Fixture] " + area + " National Highway blocked after heavy rockfall",
                "Traffic authorities in " + area + " report a major landslide near the bypass. Clearing operations are underway and traffic is diverted.",
                "Regional Herald",
                "https://example.com/mock-news/landslide-" + areaSlug,
                null,
                time1,
                now,
                SafetyEventCategory.NATURAL_DISASTER,
                EventSeverity.HIGH,
                RelevanceTier.HIGH,
                area,
                true,
                1
        ));

        // Fixture 1b: Duplicate report of the rockfall / highway blockage from a second publisher
        Instant time1b = now.minus(Duration.ofHours(2));
        UUID id1b = UUID.nameUUIDFromBytes(("mock-1b-" + area).getBytes(StandardCharsets.UTF_8));
        events.add(new RecentSafetyEventDto(
                id1b,
                "grp-mock-1b",
                "[Test Fixture] Landslide halts traffic on " + area + " National Highway bypass",
                "Severe rockfall and landslide on " + area + " National Highway bypass leaves vehicles stranded as clearing operations continue.",
                "National Monitor",
                "https://example.com/mock-news/landslide-" + areaSlug + "?utm_source=feed&ref=banner",
                null,
                time1b,
                now,
                SafetyEventCategory.NATURAL_DISASTER,
                EventSeverity.HIGH,
                RelevanceTier.HIGH,
                area,
                true,
                1
        ));

        // Fixture 2: Moderate relevance traffic accident
        Instant time2 = now.minus(Duration.ofHours(8));
        UUID id2 = UUID.nameUUIDFromBytes(("mock-2-" + area).getBytes(StandardCharsets.UTF_8));
        events.add(new RecentSafetyEventDto(
                id2,
                "grp-mock-2",
                "[Test Fixture] Bus and truck collision on outer ring road in " + area,
                "A tourist transport bus and goods carrier collided near " + area + " outer ring road. Two passengers hospitalized with non-life-threatening injuries.",
                "State Chronicle",
                "https://example.com/mock-news/accident-" + areaSlug,
                null,
                time2,
                now,
                SafetyEventCategory.TRAFFIC_AND_TRANSIT,
                EventSeverity.MODERATE,
                RelevanceTier.HIGH,
                area,
                true,
                1
        ));

        // Fixture 3: Informational general safety advisory
        Instant time3 = now.minus(Duration.ofHours(14));
        UUID id3 = UUID.nameUUIDFromBytes(("mock-3-" + area).getBytes(StandardCharsets.UTF_8));
        events.add(new RecentSafetyEventDto(
                id3,
                "grp-mock-3",
                "[Test Fixture] Travel advisory issued for heavy rainfall and waterlogging in " + area,
                "Local disaster management authorities in " + area + " advise tourists to check route advisories and exercise caution on hilly or low-lying sections.",
                "Weather Sentinel",
                "https://example.com/mock-news/travel-advisory-" + areaSlug,
                null,
                time3,
                now,
                SafetyEventCategory.GENERAL_SAFETY,
                EventSeverity.LOW,
                RelevanceTier.HIGH,
                area,
                true,
                1
        ));

        if (query.category() != null) {
            events.removeIf(e -> e.category() != query.category());
        }

        return events;
    }

    @Override
    public String getProviderName() {
        return "MockNewsProvider";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
