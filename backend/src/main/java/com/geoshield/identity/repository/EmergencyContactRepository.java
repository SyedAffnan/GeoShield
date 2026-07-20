package com.geoshield.identity.repository;

import com.geoshield.identity.entity.EmergencyContact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {
    List<EmergencyContact> findAllByUserId(UUID userId);
    Optional<EmergencyContact> findByIdAndUserId(Long id, UUID userId);

    @Modifying
    @Query("update EmergencyContact contact set contact.primary = false where contact.user.id = :userId and contact.primary = true")
    void clearPrimaryForUser(@Param("userId") UUID userId);
}
