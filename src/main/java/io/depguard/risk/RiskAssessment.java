package io.depguard.risk;

import io.depguard.shared.AssertUtil;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Persisted Dependency Risk Heuristic assessment for one dependency in one scan. */
@Entity
@Table(name = "risk_assessments")
public class RiskAssessment {

    @EmbeddedId
    @AttributeOverrides({
        @AttributeOverride(name = "scanId", column = @Column(name = "scan_id", nullable = false, length = 26)),
        @AttributeOverride(
                name = "dependencyId",
                column = @Column(name = "dependency_id", nullable = false, length = 26))
    })
    private RiskAssessmentId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "heuristic_score", nullable = false)
    private int heuristicScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", nullable = false, length = 20)
    private RiskConfidence confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reasons", nullable = false, columnDefinition = "jsonb")
    private List<String> reasons;

    @jakarta.persistence.Version
    @Column(name = "version_col", nullable = false)
    private int versionCol;

    protected RiskAssessment() {}

    private RiskAssessment(RiskAssessmentId id, RiskDecision decision) {
        this.id = AssertUtil.requireNotNull(id, "Risk assessment id must not be null");
        this.riskLevel = AssertUtil.requireNotNull(decision.riskLevel(), "Risk level must not be null");
        this.heuristicScore = decision.heuristicScore();
        this.confidence = AssertUtil.requireNotNull(decision.confidence(), "Risk confidence must not be null");
        this.reasons = List.copyOf(decision.reasons());
    }

    public static RiskAssessment of(RiskAssessmentId id, RiskDecision decision) {
        return new RiskAssessment(id, decision);
    }

    public RiskAssessmentId getId() {
        return id;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public int getHeuristicScore() {
        return heuristicScore;
    }

    public RiskConfidence getConfidence() {
        return confidence;
    }

    public List<String> getReasons() {
        return List.copyOf(reasons);
    }
}
