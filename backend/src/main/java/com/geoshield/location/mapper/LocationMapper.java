package com.geoshield.location.mapper;

import com.geoshield.location.dto.LocationRequest;
import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.entity.RouteHistory;
import com.geoshield.location.entity.TouristLocation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface LocationMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "startedAt", source = "timestamp")
    RouteHistory toRouteHistory(LocationRequest request);

    @Mapping(target = "locationId", source = "id")
    @Mapping(target = "timestamp", source = "recordedAt")
    LocationResponse toResponse(TouristLocation location);

    @Mapping(target = "locationId", source = "id")
    @Mapping(target = "timestamp", source = "startedAt")
    LocationResponse toResponse(RouteHistory routeHistory);
}
