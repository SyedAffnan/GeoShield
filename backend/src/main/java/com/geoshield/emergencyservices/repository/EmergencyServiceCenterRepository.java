package com.geoshield.emergencyservices.repository;

import com.geoshield.emergencyservices.entity.CenterType;
import com.geoshield.emergencyservices.entity.EmergencyServiceCenter;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for physical emergency facilities.
 */
@Repository
public interface EmergencyServiceCenterRepository extends JpaRepository<EmergencyServiceCenter, Long> {

    Optional<EmergencyServiceCenter> findBySourceId(String sourceId);

    boolean existsBySourceId(String sourceId);

    List<EmergencyServiceCenter> findByCenterType(CenterType centerType);
}
