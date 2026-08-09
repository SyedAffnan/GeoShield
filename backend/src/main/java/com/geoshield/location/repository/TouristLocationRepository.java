package com.geoshield.location.repository;

import com.geoshield.location.entity.TouristLocation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TouristLocationRepository extends JpaRepository<TouristLocation, Long> {
    Optional<TouristLocation> findTopByUserIdOrderByRecordedAtDesc(UUID userId);
}
