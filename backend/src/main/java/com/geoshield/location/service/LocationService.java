package com.geoshield.location.service;
import com.geoshield.common.service.ModuleService;
import com.geoshield.location.dto.LocationRequest;
import com.geoshield.location.dto.LocationResponse;
import java.util.List;
import java.util.UUID;

public interface LocationService extends ModuleService {
    // TODO(architecture-open): add geofencing only after zone ownership and authoring are approved.
    // TODO(architecture-open): apply location retention cleanup only after the retention policy is approved.

    LocationResponse submitLocation(UUID userId, LocationRequest request);
    LocationResponse getCurrentLocation(UUID userId);
    List<LocationResponse> getLocationHistory(UUID userId);
}
