package io.depguard.scan;

import io.depguard.eol.EolStatus;
import io.depguard.remediation.RemediationResponse;
import io.depguard.risk.RiskConfidence;
import io.depguard.risk.RiskLevel;
import io.depguard.vulnerability.Severity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Complete, reproducible report for a completed scan. */
public record ScanReportDto(
        String scanId,
        String projectName,
        String repositoryUrl,
        String commitSha,
        String branch,
        Instant scannedAt,
        RiskLevel overallHealth,
        DataSources dataSources,
        Summary summary,
        List<DependencyReport> dependencies) {

    public record DataSources(Instant eolFetchedAt, Instant advisoryFetchedAt) {}

    public record Summary(
            long totalDependencies,
            long eolCount,
            long vulnerableCount,
            long criticalCount,
            long highCount,
            long mediumCount,
            long lowCount,
            long unknownEolCount) {}

    public record DependencyReport(
            String groupId,
            String artifactId,
            String version,
            boolean direct,
            String scope,
            EolStatus eolStatus,
            LocalDate eolDate,
            List<Advisory> advisories,
            RiskLevel riskLevel,
            Integer heuristicScore,
            RiskConfidence confidence,
            List<String> riskReasons,
            RemediationResponse recommendation) {}

    public record Advisory(String osvId, String summary, Severity severity, BigDecimal cvssScore) {}
}
