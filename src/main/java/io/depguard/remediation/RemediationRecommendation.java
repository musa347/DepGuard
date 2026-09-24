package io.depguard.remediation;

import io.depguard.risk.RiskConfidence;
import io.depguard.shared.AssertUtil;
import io.depguard.shared.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** A compatibility-aware upgrade recommendation for one risky dependency in one scan. */
@Entity
@Table(name = "remediation_recommendations")
public class RemediationRecommendation {

    @jakarta.persistence.Id
    @Column(name = "id", nullable = false, length = 26)
    private String id;

    @Column(name = "scan_id", nullable = false, length = 26)
    private String scanId;

    @Column(name = "dependency_id", nullable = false, length = 26)
    private String dependencyId;

    @Column(name = "current_version", nullable = false, length = 100)
    private String currentVersion;

    @Column(name = "recommended_version", nullable = false, length = 100)
    private String recommendedVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "upgrade_type", nullable = false, length = 20)
    private UpgradeType upgradeType;

    @Column(name = "breaking_change_summary", columnDefinition = "TEXT")
    private String breakingChangeSummary;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", nullable = false, length = 20)
    private RiskConfidence confidence;

    protected RemediationRecommendation() {}

    private RemediationRecommendation(
            String scanId,
            String dependencyId,
            String currentVersion,
            String recommendedVersion,
            UpgradeType upgradeType,
            String breakingChangeSummary,
            String reason,
            RiskConfidence confidence) {
        this.id = IdGenerator.generate();
        this.scanId = AssertUtil.requireNotBlank(scanId, "scanId must not be blank");
        this.dependencyId = AssertUtil.requireNotBlank(dependencyId, "dependencyId must not be blank");
        this.currentVersion = AssertUtil.requireNotBlank(currentVersion, "currentVersion must not be blank");
        this.recommendedVersion =
                AssertUtil.requireNotBlank(recommendedVersion, "recommendedVersion must not be blank");
        this.upgradeType = AssertUtil.requireNotNull(upgradeType, "upgradeType must not be null");
        this.breakingChangeSummary = breakingChangeSummary;
        this.reason = AssertUtil.requireNotBlank(reason, "reason must not be blank");
        this.confidence = AssertUtil.requireNotNull(confidence, "confidence must not be null");
    }

    public static RemediationRecommendation of(
            String scanId,
            String dependencyId,
            String currentVersion,
            String recommendedVersion,
            UpgradeType upgradeType,
            String breakingChangeSummary,
            String reason,
            RiskConfidence confidence) {
        return new RemediationRecommendation(
                scanId,
                dependencyId,
                currentVersion,
                recommendedVersion,
                upgradeType,
                breakingChangeSummary,
                reason,
                confidence);
    }

    public String getScanId() {
        return scanId;
    }

    public String getDependencyId() {
        return dependencyId;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public String getRecommendedVersion() {
        return recommendedVersion;
    }

    public UpgradeType getUpgradeType() {
        return upgradeType;
    }

    public String getBreakingChangeSummary() {
        return breakingChangeSummary;
    }

    public String getReason() {
        return reason;
    }

    public RiskConfidence getConfidence() {
        return confidence;
    }
}
