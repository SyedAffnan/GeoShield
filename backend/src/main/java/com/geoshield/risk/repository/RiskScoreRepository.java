package com.geoshield.risk.repository;

import com.geoshield.risk.entity.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskScoreRepository extends JpaRepository<RiskScore, Long> { }
