package com.geoshield.sos.repository;

import com.geoshield.sos.entity.SosRequest;
import com.geoshield.sos.entity.SosStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SosRequestRepository extends JpaRepository<SosRequest, UUID> {
    Optional<SosRequest> findByUserIdAndClientRequestId(UUID userId, UUID clientRequestId);
    Optional<SosRequest> findByClientRequestId(UUID clientRequestId);
    List<SosRequest> findAllByStatusInOrderByCreatedAtDesc(List<SosStatus> statuses);
    List<SosRequest> findAllByOrderByCreatedAtDesc();
    List<SosRequest> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<SosRequest> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(UUID userId, List<SosStatus> statuses);
    long countByStatusIn(List<SosStatus> statuses);

    /**
     * Fetches a SosRequest by clientRequestId with its user eagerly loaded via
     * JOIN FETCH. Required by SosTransactionHelper.resolveExistingSosAfterConflict()
     * so that SosMapper can safely access user fields (id, username, fullName,
     * phoneNumber) outside an active Hibernate session — avoiding LazyInitializationException.
     */
    @Query("SELECT s FROM SosRequest s JOIN FETCH s.user WHERE s.clientRequestId = :clientRequestId")
    Optional<SosRequest> findByClientRequestIdWithUser(@Param("clientRequestId") UUID clientRequestId);
}
