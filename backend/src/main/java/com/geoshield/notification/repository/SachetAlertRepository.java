package com.geoshield.notification.repository;

import com.geoshield.notification.entity.SachetAlert;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SachetAlertRepository extends JpaRepository<SachetAlert, UUID> {

    Optional<SachetAlert> findByIdentifierAndSender(String identifier, String sender);

    List<SachetAlert> findByIdentifier(String identifier);

    List<SachetAlert> findByReferencesIdentifier(String referencesIdentifier);

    @Query("SELECT a FROM SachetAlert a WHERE a.effectiveAt <= :now AND a.expiresAt > :now AND a.cancelled = false ORDER BY a.sentAt DESC")
    List<SachetAlert> findActiveAlerts(@Param("now") Instant now);
}
