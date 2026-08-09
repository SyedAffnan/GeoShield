package com.geoshield.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LocationServiceImplTest {
    @Mock private IdentityService identityService;
    @Mock private TouristLocationRepository touristLocationRepository;
    @Mock private RouteHistoryRepository routeHistoryRepository;
    @Mock private LocationMapper locationMapper;

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

        LocationResponse actual = service().submitLocation(userId, request);

        assertThat(actual).isEqualTo(response);
        verify(touristLocationRepository).save(any(TouristLocation.class));
        verify(routeHistoryRepository).save(routeHistory);
        assertThat(routeHistory.getUser()).isEqualTo(user);
    }

    @Test
    void retrievesCurrentLocationAndHistoryForAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        TouristLocation currentLocation = new TouristLocation();
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

        assertThat(service().getCurrentLocation(userId)).isEqualTo(currentResponse);
        assertThat(service().getLocationHistory(userId)).containsExactly(firstResponse, secondResponse);
    }

    private LocationServiceImpl service() {
        return new LocationServiceImpl(identityService, touristLocationRepository, routeHistoryRepository, locationMapper);
    }

    private User user(UUID userId) {
        User user = new User("tourist", "tourist@example.com", "hash", "Tourist", "+919876543210", new UserRole(Role.TOURIST));
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private LocationRequest request() {
        return new LocationRequest(new BigDecimal("12.9716000"), new BigDecimal("77.5946000"), new BigDecimal("5.0"),
                new BigDecimal("1.5"), Instant.parse("2026-08-09T10:00:00Z"));
    }

    private LocationResponse response(Long locationId) {
        LocationRequest request = request();
        return new LocationResponse(locationId, request.latitude(), request.longitude(), request.accuracy(), request.speed(),
                request.timestamp());
    }
}
