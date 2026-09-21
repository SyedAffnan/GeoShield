package com.geoshield.risk.repository;

import com.geoshield.risk.entity.RiskScore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskScoreRepository extends JpaRepository<RiskScore, Long> {
    Page<RiskScore> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    List<RiskScore> findTop10ByUserIdOrderByCreatedAtDesc(UUID userId);
    List<RiskScore> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<RiskScore> findByDecisionId(UUID decisionId);
}
