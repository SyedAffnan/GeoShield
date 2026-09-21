package com.geoshield.risk.entity;

import com.geoshield.common.entity.BaseEntity;
import com.geoshield.historicaldata.entity.GeographicLevel;
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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "risk_scores", indexes = {
        @Index(name = "idx_risk_scores_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_risk_scores_decision_id", columnList = "decision_id")
})
public class RiskScore extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "risk_score_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "decision_id")
    private UUID decisionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int score;

    @Column(name = "decimal_score", precision = 5, scale = 2)
    private BigDecimal decimalScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "effective_risk_level", length = 16)
    private RiskLevel effectiveRiskLevel;

    @Column(name = "override_active", nullable = false)
    private boolean overrideActive;

    @Column(name = "sachet_alert_identifier", length = 128)
    private String sachetAlertIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "geographic_level", length = 32)
    private GeographicLevel geographicLevel;

    @Column(name = "geographic_unit", length = 128)
    private String geographicUnit;

    @Column(name = "stcode", length = 16)
    private String stateCode;

    @Column(name = "data_completeness", columnDefinition = "json")
    private String dataCompleteness;

    @Column(name = "factor_details", columnDefinition = "json")
    private String factorDetails;

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
        this(UUID.randomUUID(), user, score, BigDecimal.valueOf(score), riskLevel, riskLevel,
                false, null, null, null, null, null, null, contributingFactors, scoringMethod, modelVersion);
    }

    public RiskScore(UUID decisionId, User user, int score, BigDecimal decimalScore, RiskLevel riskLevel,
            RiskLevel effectiveRiskLevel, boolean overrideActive, String sachetAlertIdentifier,
            GeographicLevel geographicLevel, String geographicUnit, String stateCode,
            String dataCompleteness, String factorDetails, String contributingFactors,
            RiskScoringMethod scoringMethod, String modelVersion) {
        this.decisionId = decisionId != null ? decisionId : UUID.randomUUID();
        this.user = user;
        this.score = score;
        this.decimalScore = decimalScore != null ? decimalScore : BigDecimal.valueOf(score);
        this.riskLevel = riskLevel;
        this.effectiveRiskLevel = effectiveRiskLevel != null ? effectiveRiskLevel : riskLevel;
        this.overrideActive = overrideActive;
        this.sachetAlertIdentifier = sachetAlertIdentifier;
        this.geographicLevel = geographicLevel;
        this.geographicUnit = geographicUnit;
        this.stateCode = stateCode;
        this.dataCompleteness = dataCompleteness;
        this.factorDetails = factorDetails;
        this.contributingFactors = contributingFactors;
        this.scoringMethod = scoringMethod;
        this.modelVersion = modelVersion;
    }

    public void recordDecisionDetails(
            RiskLevel effectiveRiskLevel,
            boolean overrideActive,
            String sachetAlertIdentifier,
            GeographicLevel geographicLevel,
            String geographicUnit,
            String stateCode) {
        this.effectiveRiskLevel = effectiveRiskLevel;
        this.overrideActive = overrideActive;
        this.sachetAlertIdentifier = sachetAlertIdentifier;
        this.geographicLevel = geographicLevel;
        this.geographicUnit = geographicUnit;
        this.stateCode = stateCode;
    }

    public Long getId() { return id; }
    public UUID getDecisionId() { return decisionId; }
    public User getUser() { return user; }
    public int getScore() { return score; }
    public BigDecimal getDecimalScore() { return decimalScore; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public RiskLevel getEffectiveRiskLevel() { return effectiveRiskLevel; }
    public boolean isOverrideActive() { return overrideActive; }
    public String getSachetAlertIdentifier() { return sachetAlertIdentifier; }
    public GeographicLevel getGeographicLevel() { return geographicLevel; }
    public String getGeographicUnit() { return geographicUnit; }
    public String getStateCode() { return stateCode; }
    public String getDataCompleteness() { return dataCompleteness; }
    public String getFactorDetails() { return factorDetails; }
    public String getContributingFactors() { return contributingFactors; }
    public RiskScoringMethod getScoringMethod() { return scoringMethod; }
    public String getModelVersion() { return modelVersion; }

    public void setDecisionId(UUID decisionId) { this.decisionId = decisionId; }
    public void setEffectiveRiskLevel(RiskLevel effectiveRiskLevel) { this.effectiveRiskLevel = effectiveRiskLevel; }
    public void setOverrideActive(boolean overrideActive) { this.overrideActive = overrideActive; }
    public void setSachetAlertIdentifier(String sachetAlertIdentifier) { this.sachetAlertIdentifier = sachetAlertIdentifier; }
    public void setGeographicLevel(GeographicLevel geographicLevel) { this.geographicLevel = geographicLevel; }
    public void setGeographicUnit(String geographicUnit) { this.geographicUnit = geographicUnit; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public void setDataCompleteness(String dataCompleteness) { this.dataCompleteness = dataCompleteness; }
    public void setFactorDetails(String factorDetails) { this.factorDetails = factorDetails; }
}
