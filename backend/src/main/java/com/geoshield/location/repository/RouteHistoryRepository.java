package com.geoshield.location.repository;

import com.geoshield.location.entity.RouteHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteHistoryRepository extends JpaRepository<RouteHistory, Long> {
    List<RouteHistory> findAllByUserIdOrderByStartedAtAsc(UUID userId);
}
