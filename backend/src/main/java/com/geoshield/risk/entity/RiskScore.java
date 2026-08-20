package com.geoshield.risk.entity;

import com.geoshield.common.entity.BaseEntity;
import com.geoshield.identity.entity.User;
import com.geoshield.risk.dto.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "risk_scores")
public class RiskScore extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "risk_score_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private RiskLevel riskLevel;

    @Column(name = "contributing_factors", nullable = false, columnDefinition = "json")
    private String contributingFactors;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_method", nullable = false, length = 32)
    private RiskScoringMethod scoringMethod;

    @Column(name = "model_version", length = 128)
    private String modelVersion;

    protected RiskScore() { }

    public RiskScore(User user, int score, RiskLevel riskLevel, String contributingFactors,
            RiskScoringMethod scoringMethod, String modelVersion) {
        this.user = user;
        this.score = score;
        this.riskLevel = riskLevel;
        this.contributingFactors = contributingFactors;
        this.scoringMethod = scoringMethod;
        this.modelVersion = modelVersion;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public int getScore() { return score; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public String getContributingFactors() { return contributingFactors; }
    public RiskScoringMethod getScoringMethod() { return scoringMethod; }
    public String getModelVersion() { return modelVersion; }
}
