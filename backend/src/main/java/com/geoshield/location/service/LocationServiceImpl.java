package com.geoshield.location.service;

import com.geoshield.common.exception.ResourceNotFoundException;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.location.dto.LocationRequest;
import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.entity.RouteHistory;
import com.geoshield.location.entity.TouristLocation;
import com.geoshield.location.mapper.LocationMapper;
import com.geoshield.location.repository.RouteHistoryRepository;
import com.geoshield.location.repository.TouristLocationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationServiceImpl implements LocationService {
    private final IdentityService identityService;
    private final TouristLocationRepository touristLocationRepository;
    private final RouteHistoryRepository routeHistoryRepository;
    private final LocationMapper locationMapper;

    public LocationServiceImpl(IdentityService identityService, TouristLocationRepository touristLocationRepository,
            RouteHistoryRepository routeHistoryRepository, LocationMapper locationMapper) {
        this.identityService = identityService;
        this.touristLocationRepository = touristLocationRepository;
        this.routeHistoryRepository = routeHistoryRepository;
        this.locationMapper = locationMapper;
    }

    @Override
    @Transactional
    public LocationResponse submitLocation(UUID userId, LocationRequest request) {
        User user = identityService.getUserById(userId);
        TouristLocation currentLocation = touristLocationRepository.findTopByUserIdOrderByRecordedAtDesc(userId)
                .orElseGet(() -> {
                    TouristLocation location = new TouristLocation();
                    location.setUser(user);
                    return location;
                });
        currentLocation.update(request.latitude(), request.longitude(), request.accuracy(), request.speed(), request.timestamp());
        TouristLocation savedLocation = touristLocationRepository.save(currentLocation);

        RouteHistory routeHistory = locationMapper.toRouteHistory(request);
        routeHistory.setUser(user);
        routeHistoryRepository.save(routeHistory);
        return locationMapper.toResponse(savedLocation);
    }

    @Override
    @Transactional(readOnly = true)
    public LocationResponse getCurrentLocation(UUID userId) {
        return locationMapper.toResponse(touristLocationRepository.findTopByUserIdOrderByRecordedAtDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Current location not found")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocationResponse> getLocationHistory(UUID userId) {
        return routeHistoryRepository.findAllByUserIdOrderByStartedAtAsc(userId).stream().map(locationMapper::toResponse).toList();
    }
}
