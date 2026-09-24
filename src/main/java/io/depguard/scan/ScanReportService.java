package io.depguard.scan;

import io.depguard.dependency.ScanDependencyCoords;
import io.depguard.dependency.ScanDependencyRepository;
import io.depguard.eol.EolRecord;
import io.depguard.eol.EolRepository;
import io.depguard.eol.EolStatus;
import io.depguard.project.ProjectAPI;
import io.depguard.project.ProjectSnapshot;
import io.depguard.remediation.RemediationResponse;
import io.depguard.remediation.RemediationService;
import io.depguard.risk.RiskAssessment;
import io.depguard.risk.RiskLevel;
import io.depguard.risk.RiskRepository;
import io.depguard.shared.ResourceNotFoundException;
import io.depguard.shared.ScanId;
import io.depguard.shared.ScanNotReadyException;
import io.depguard.vulnerability.VulnerabilityRecord;
import io.depguard.vulnerability.VulnerabilityRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles persisted scan, enrichment, risk, and remediation data into a reproducible report. */
@Service
class ScanReportService {

    private final ScanRepository scanRepository;
    private final ProjectAPI projectAPI;
    private final ScanDependencyRepository scanDependencyRepository;
    private final EolRepository eolRepository;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final RiskRepository riskRepository;
    private final RemediationService remediationService;

    ScanReportService(
            ScanRepository scanRepository,
            ProjectAPI projectAPI,
            ScanDependencyRepository scanDependencyRepository,
            EolRepository eolRepository,
            VulnerabilityRepository vulnerabilityRepository,
            RiskRepository riskRepository,
            RemediationService remediationService) {
        this.scanRepository = scanRepository;
        this.projectAPI = projectAPI;
        this.scanDependencyRepository = scanDependencyRepository;
        this.eolRepository = eolRepository;
        this.vulnerabilityRepository = vulnerabilityRepository;
        this.riskRepository = riskRepository;
        this.remediationService = remediationService;
    }

    @Transactional(readOnly = true)
    ScanReportDto getReport(ScanId scanId) {
        Scan scan = scanRepository
                .findById(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan not found: " + scanId));
        if (scan.getStatus() != ScanStatus.COMPLETED) {
            throw new ScanNotReadyException(
                    "Scan " + scanId + " is " + scan.getStatus() + "; report is not available yet");
        }
        ProjectSnapshot project = projectAPI.getProject(scan.getProjectId());
        List<ScanDependencyCoords> dependencies = scanDependencyRepository.findScanDependenciesWithCoords(scanId.id());
        Map<String, EolRecord> eolByDependency = eolRepository.findByScanId(scanId.id()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        record -> record.getId().dependencyId(), Function.identity()));
        Map<String, RiskAssessment> riskByDependency = riskRepository.findByIdScanId(scanId.id()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        record -> record.getId().dependencyId(), Function.identity()));
        Map<String, RemediationResponse> remediationByDependency =
                remediationService.getRecommendations(scanId).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                RemediationResponse::dependencyId, Function.identity()));

        List<ScanReportDto.DependencyReport> reportDependencies = dependencies.stream()
                .map(dependency -> dependencyReport(
                        dependency, scanId, eolByDependency, riskByDependency, remediationByDependency))
                .toList();
        return new ScanReportDto(
                scanId.id(),
                project.name(),
                project.repositoryUrl(),
                scan.getCommitSha(),
                scan.getBranch(),
                scan.getCompletedAt(),
                overallHealth(reportDependencies),
                dataSources(eolByDependency, scanId),
                summary(reportDependencies),
                reportDependencies);
    }

    private ScanReportDto.DependencyReport dependencyReport(
            ScanDependencyCoords dependency,
            ScanId scanId,
            Map<String, EolRecord> eolByDependency,
            Map<String, RiskAssessment> riskByDependency,
            Map<String, RemediationResponse> remediationByDependency) {
        EolRecord eol = eolByDependency.get(dependency.dependencyId());
        RiskAssessment risk = riskByDependency.get(dependency.dependencyId());
        List<ScanReportDto.Advisory> advisories =
                vulnerabilityRepository.findByScanAndDependencyId(scanId.id(), dependency.dependencyId()).stream()
                        .map(advisory -> new ScanReportDto.Advisory(
                                advisory.getOsvId(),
                                advisory.getSummary(),
                                advisory.getSeverity(),
                                advisory.getCvssScore()))
                        .toList();
        return new ScanReportDto.DependencyReport(
                dependency.groupId(),
                dependency.artifactId(),
                dependency.version(),
                dependency.direct(),
                dependency.scope(),
                eol == null ? EolStatus.UNKNOWN : eol.getStatus(),
                eol == null ? null : eol.getEolDate(),
                advisories,
                risk == null ? RiskLevel.UNKNOWN : risk.getRiskLevel(),
                risk == null ? null : risk.getHeuristicScore(),
                risk == null ? null : risk.getConfidence(),
                risk == null ? List.of() : risk.getReasons(),
                remediationByDependency.get(dependency.dependencyId()));
    }

    private ScanReportDto.DataSources dataSources(Map<String, EolRecord> eolByDependency, ScanId scanId) {
        Instant eolFetchedAt = eolByDependency.values().stream()
                .map(EolRecord::getDataSourceFetchedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        Instant advisoryFetchedAt = vulnerabilityRepository.findByScanId(scanId.id()).stream()
                .map(VulnerabilityRecord::getDataSourceFetchedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new ScanReportDto.DataSources(eolFetchedAt, advisoryFetchedAt);
    }

    private static ScanReportDto.Summary summary(List<ScanReportDto.DependencyReport> dependencies) {
        return new ScanReportDto.Summary(
                dependencies.size(),
                dependencies.stream()
                        .filter(dependency -> dependency.eolStatus() == EolStatus.EOL)
                        .count(),
                dependencies.stream()
                        .filter(dependency -> !dependency.advisories().isEmpty())
                        .count(),
                dependencies.stream()
                        .filter(dependency -> dependency.riskLevel() == RiskLevel.CRITICAL)
                        .count(),
                dependencies.stream()
                        .filter(dependency -> dependency.riskLevel() == RiskLevel.HIGH)
                        .count(),
                dependencies.stream()
                        .filter(dependency -> dependency.riskLevel() == RiskLevel.MEDIUM)
                        .count(),
                dependencies.stream()
                        .filter(dependency -> dependency.riskLevel() == RiskLevel.LOW)
                        .count(),
                dependencies.stream()
                        .filter(dependency -> dependency.eolStatus() == EolStatus.UNKNOWN)
                        .count());
    }

    private static RiskLevel overallHealth(List<ScanReportDto.DependencyReport> dependencies) {
        if (dependencies.stream().anyMatch(dependency -> dependency.riskLevel() == RiskLevel.CRITICAL))
            return RiskLevel.CRITICAL;
        if (dependencies.stream().anyMatch(dependency -> dependency.riskLevel() == RiskLevel.HIGH))
            return RiskLevel.HIGH;
        if (dependencies.stream().anyMatch(dependency -> dependency.riskLevel() == RiskLevel.MEDIUM))
            return RiskLevel.MEDIUM;
        if (dependencies.stream().anyMatch(dependency -> dependency.riskLevel() == RiskLevel.UNKNOWN))
            return RiskLevel.UNKNOWN;
        return RiskLevel.LOW;
    }
}
