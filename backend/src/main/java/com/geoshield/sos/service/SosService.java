package com.geoshield.sos.service;

import com.geoshield.common.service.ModuleService;
import com.geoshield.sos.dto.CreateSosRequest;
import com.geoshield.sos.dto.SosResponse;
import com.geoshield.sos.entity.SosStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SosService extends ModuleService {
    SosResponse createSos(UUID touristId, CreateSosRequest request);
    Optional<SosResponse> getMyActiveSos(UUID touristId);
    SosResponse cancelSos(UUID touristId, UUID sosId);
    List<SosResponse> getSosQueue();
    SosResponse getSosById(UUID sosId);
    SosResponse updateSosStatus(UUID sosId, SosStatus newStatus, UUID responderId);
    long getActiveSosCount();
}
