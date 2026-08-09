package com.geoshield.incident.mapper;

import com.geoshield.incident.dto.CreateIncidentRequest;
import com.geoshield.incident.dto.IncidentResponse;
import com.geoshield.incident.entity.Incident;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface IncidentMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reporter", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "integrityHash", ignore = true)
    @Mapping(target = "sourceType", ignore = true)
    @Mapping(target = "clientRequestId", ignore = true)
    Incident toEntity(CreateIncidentRequest request);

    @Mapping(target = "incidentId", source = "id")
    @Mapping(target = "reportedAt", source = "createdAt")
    IncidentResponse toResponse(Incident incident);
}
