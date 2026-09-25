package io.depguard.scan;

import io.depguard.dependency.ScanDependencyView;
import io.depguard.risk.RiskLevel;
import java.time.Instant;
import java.util.List;

/**
 * Response payload of {@code GET /api/scans/{id}}: lifecycle, reproducibility metadata and, once the
 * scan is completed, its resolved dependencies.
 */
public record ScanResponse(
        String id,
        String status,
        String commitSha,
        String branch,
        Instant startedAt,
        Instant completedAt,
        String errorMessage,
        long dependencyCount,
        RiskSummary riskSummary,
        List<ScanDependencyResponse> dependencies) {

    public record RiskSummary(
            RiskLevel overallHealth,
            long criticalCount,
            long highCount,
            long mediumCount,
            long lowCount,
            long eolCount) {}

    static ScanResponse from(Scan scan, List<ScanDependencyView> dependencies, RiskSummary riskSummary) {
        return new ScanResponse(
                scan.getId().id(),
                scan.getStatus().name(),
                scan.getCommitSha(),
                scan.getBranch(),
                scan.getStartedAt(),
                scan.getCompletedAt(),
                scan.getErrorMessage(),
                dependencies.size(),
                riskSummary,
                dependencies.stream().map(ScanDependencyResponse::from).toList());
    }
}
