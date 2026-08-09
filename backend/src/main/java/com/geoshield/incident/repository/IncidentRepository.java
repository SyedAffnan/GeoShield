package com.geoshield.incident.repository;

import com.geoshield.incident.entity.Incident;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    List<Incident> findAllByReporterIdOrderByCreatedAtDesc(UUID reporterId);
    Optional<Incident> findByIdAndReporterId(UUID incidentId, UUID reporterId);
    Optional<Incident> findByReporterIdAndClientRequestId(UUID reporterId, UUID clientRequestId);
    Optional<Incident> findByClientRequestId(UUID clientRequestId);
}
