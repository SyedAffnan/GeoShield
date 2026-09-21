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
                "https://example.com/mock-news/landslide-" + area.toLowerCase(),
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

        // Fixture 2: Moderate relevance traffic accident
        Instant time2 = now.minus(Duration.ofHours(8));
        UUID id2 = UUID.nameUUIDFromBytes(("mock-2-" + area).getBytes(StandardCharsets.UTF_8));
        events.add(new RecentSafetyEventDto(
                id2,
                "grp-mock-2",
                "[Test Fixture] Bus and truck collision on outer ring road in " + area,
                "A tourist transport bus and goods carrier collided near " + area + " outer ring road. Two passengers hospitalized with non-life-threatening injuries.",
                "State Chronicle",
                "https://example.com/mock-news/accident-" + area.toLowerCase(),
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
