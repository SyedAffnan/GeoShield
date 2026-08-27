package com.geoshield.sos.repository;

import com.geoshield.sos.entity.SosRequest;
import com.geoshield.sos.entity.SosStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SosRequestRepository extends JpaRepository<SosRequest, UUID> {
    Optional<SosRequest> findByUserIdAndClientRequestId(UUID userId, UUID clientRequestId);
    Optional<SosRequest> findByClientRequestId(UUID clientRequestId);
    List<SosRequest> findAllByStatusInOrderByCreatedAtDesc(List<SosStatus> statuses);
    List<SosRequest> findAllByOrderByCreatedAtDesc();
    List<SosRequest> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<SosRequest> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(UUID userId, List<SosStatus> statuses);
    long countByStatusIn(List<SosStatus> statuses);
}
