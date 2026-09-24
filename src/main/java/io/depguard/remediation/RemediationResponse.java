package io.depguard.remediation;

import io.depguard.risk.RiskConfidence;

/** API representation of a compatibility-aware remediation. */
public record RemediationResponse(
        String dependencyId,
        String currentVersion,
        String recommendedVersion,
        UpgradeType upgradeType,
        String breakingChangeSummary,
        String reason,
        RiskConfidence confidence) {

    static RemediationResponse from(RemediationRecommendation recommendation) {
        return new RemediationResponse(
                recommendation.getDependencyId(),
                recommendation.getCurrentVersion(),
                recommendation.getRecommendedVersion(),
                recommendation.getUpgradeType(),
                recommendation.getBreakingChangeSummary(),
                recommendation.getReason(),
                recommendation.getConfidence());
    }
}
