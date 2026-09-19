package com.geoshield.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geoshield.common.exception.ResourceNotFoundException;
import com.geoshield.identity.entity.Role;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.entity.UserRole;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.location.dto.LocationRequest;
import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.entity.RouteHistory;
import com.geoshield.location.entity.TouristLocation;
import com.geoshield.location.mapper.LocationMapper;
import com.geoshield.location.repository.RouteHistoryRepository;
import com.geoshield.location.repository.TouristLocationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LocationServiceImplTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-09T10:00:00Z");

    @Mock private IdentityService identityService;
    @Mock private TouristLocationRepository touristLocationRepository;
    @Mock private RouteHistoryRepository routeHistoryRepository;
    @Mock private LocationMapper locationMapper;

    private LocationValidityPolicy validityPolicy;
    private Clock clock;
    private LocationServiceImpl service;

    @BeforeEach
    void setUp() {
        validityPolicy = new LocationValidityPolicy();
        clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        service = new LocationServiceImpl(identityService, touristLocationRepository, routeHistoryRepository,
                locationMapper, validityPolicy, clock);
    }

    @Test
    void submittingLocationUpdatesCurrentLocationAndPersistsRouteHistory() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        LocationRequest request = request();
        TouristLocation currentLocation = new TouristLocation();
        RouteHistory routeHistory = new RouteHistory();
        LocationResponse response = new LocationResponse(1L, request.latitude(), request.longitude(), request.accuracy(),
                request.speed(), request.timestamp());
        when(identityService.getUserById(userId)).thenReturn(user);
        when(touristLocationRepository.findTopByUserIdOrderByRecordedAtDesc(userId)).thenReturn(Optional.empty());
        when(touristLocationRepository.save(any(TouristLocation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(locationMapper.toRouteHistory(request)).thenReturn(routeHistory);
        when(locationMapper.toResponse(any(TouristLocation.class))).thenReturn(response);

        LocationResponse actual = service.submitLocation(userId, request);

        assertThat(actual).isEqualTo(response);
        verify(touristLocationRepository).save(any(TouristLocation.class));
        verify(routeHistoryRepository).save(routeHistory);
        assertThat(routeHistory.getUser()).isEqualTo(user);
    }

    @Test
    void retrievesCurrentLocationAndHistoryForAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        TouristLocation currentLocation = new TouristLocation();
        LocationRequest request = request();
        currentLocation.update(request.latitude(), request.longitude(), request.accuracy(), request.speed(), request.timestamp());

        RouteHistory firstRoutePoint = new RouteHistory();
        RouteHistory secondRoutePoint = new RouteHistory();
        LocationResponse currentResponse = response(1L);
        LocationResponse firstResponse = response(2L);
        LocationResponse secondResponse = response(3L);
        when(touristLocationRepository.findTopByUserIdOrderByRecordedAtDesc(userId)).thenReturn(Optional.of(currentLocation));
        when(locationMapper.toResponse(currentLocation)).thenReturn(currentResponse);
        when(routeHistoryRepository.findAllByUserIdOrderByStartedAtAsc(userId)).thenReturn(List.of(firstRoutePoint, secondRoutePoint));
        when(locationMapper.toResponse(firstRoutePoint)).thenReturn(firstResponse);
        when(locationMapper.toResponse(secondRoutePoint)).thenReturn(secondResponse);

        assertThat(service.getCurrentLocation(userId)).isEqualTo(currentResponse);
        assertThat(service.getLocationHistory(userId)).containsExactly(firstResponse, secondResponse);
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when no location record exists for user")
    void getCurrentLocation_throwsWhenNoLocationFound() {
        UUID userId = UUID.randomUUID();
        when(touristLocationRepository.findTopByUserIdOrderByRecordedAtDesc(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentLocation(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Current location not found");
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when current location is older than 15 minutes (stale)")
    void getCurrentLocation_throwsWhenLocationIsStale() {
        UUID userId = UUID.randomUUID();
        TouristLocation staleLocation = new TouristLocation();
        // 16 minutes old relative to FIXED_NOW
        Instant staleInstant = FIXED_NOW.minus(Duration.ofMinutes(16));
        staleLocation.update(new BigDecimal("12.9716000"), new BigDecimal("77.5946000"), new BigDecimal("10.0"),
                BigDecimal.ZERO, staleInstant);
        when(touristLocationRepository.findTopByUserIdOrderByRecordedAtDesc(userId)).thenReturn(Optional.of(staleLocation));

        assertThatThrownBy(() -> service.getCurrentLocation(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Current valid location not found")
                .hasMessageContaining("stale");
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when current location horizontal accuracy exceeds 100m")
    void getCurrentLocation_throwsWhenLocationIsInaccurate() {
        UUID userId = UUID.randomUUID();
        TouristLocation inaccurateLocation = new TouristLocation();
        // Fresh fix (1 min old), but accuracy 120m > 100m threshold
        Instant freshInstant = FIXED_NOW.minus(Duration.ofMinutes(1));
        inaccurateLocation.update(new BigDecimal("12.9716000"), new BigDecimal("77.5946000"), new BigDecimal("120.0"),
                BigDecimal.ZERO, freshInstant);
        when(touristLocationRepository.findTopByUserIdOrderByRecordedAtDesc(userId)).thenReturn(Optional.of(inaccurateLocation));

        assertThatThrownBy(() -> service.getCurrentLocation(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Current valid location not found")
                .hasMessageContaining("accuracy 120.0m exceeds maximum allowable threshold of 100.0m");
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when current location has future timestamp")
    void getCurrentLocation_throwsWhenLocationTimestampIsInTheFuture() {
        UUID userId = UUID.randomUUID();
        TouristLocation futureLocation = new TouristLocation();
        // Future timestamp (5 minutes ahead of clock)
        Instant futureInstant = FIXED_NOW.plus(Duration.ofMinutes(5));
        futureLocation.update(new BigDecimal("12.9716000"), new BigDecimal("77.5946000"), new BigDecimal("10.0"),
                BigDecimal.ZERO, futureInstant);
        when(touristLocationRepository.findTopByUserIdOrderByRecordedAtDesc(userId)).thenReturn(Optional.of(futureLocation));

        assertThatThrownBy(() -> service.getCurrentLocation(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Current valid location not found")
                .hasMessageContaining("timestamp is in the future");
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when location is both stale and inaccurate")
    void getCurrentLocation_throwsWhenBothStaleAndInaccurate() {
        UUID userId = UUID.randomUUID();
        TouristLocation badLocation = new TouristLocation();
        Instant staleInstant = FIXED_NOW.minus(Duration.ofMinutes(25));
        badLocation.update(new BigDecimal("12.9716000"), new BigDecimal("77.5946000"), new BigDecimal("250.0"),
                BigDecimal.ZERO, staleInstant);
        when(touristLocationRepository.findTopByUserIdOrderByRecordedAtDesc(userId)).thenReturn(Optional.of(badLocation));

        assertThatThrownBy(() -> service.getCurrentLocation(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Current valid location not found");
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when location coordinates are invalid")
    void getCurrentLocation_throwsWhenCoordinatesAreInvalid() {
        UUID userId = UUID.randomUUID();
        TouristLocation invalidCoordLocation = new TouristLocation();
        invalidCoordLocation.update(new BigDecimal("95.0"), new BigDecimal("77.5946000"), new BigDecimal("10.0"),
                BigDecimal.ZERO, FIXED_NOW);
        when(touristLocationRepository.findTopByUserIdOrderByRecordedAtDesc(userId)).thenReturn(Optional.of(invalidCoordLocation));

        assertThatThrownBy(() -> service.getCurrentLocation(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Current valid location not found")
                .hasMessageContaining("latitude 95.0 is outside valid range");
    }

    private User user(UUID userId) {
        User user = new User("tourist", "tourist@example.com", "hash", "Tourist", "+919876543210", new UserRole(Role.TOURIST));
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private LocationRequest request() {
        return new LocationRequest(new BigDecimal("12.9716000"), new BigDecimal("77.5946000"), new BigDecimal("5.0"),
                new BigDecimal("1.5"), FIXED_NOW);
    }

    private LocationResponse response(Long locationId) {
        LocationRequest request = request();
        return new LocationResponse(locationId, request.latitude(), request.longitude(), request.accuracy(), request.speed(),
                request.timestamp());
    }
}
