package io.depguard.remediation;

import io.depguard.dependency.ScanDependencyCoords;
import io.depguard.dependency.ScanDependencyRepository;
import io.depguard.eol.EolFallbackStore;
import io.depguard.eol.EolMappingStrategy;
import io.depguard.eol.EolStatus;
import io.depguard.risk.RiskAssessment;
import io.depguard.risk.RiskConfidence;
import io.depguard.risk.RiskLevel;
import io.depguard.risk.RiskRepository;
import io.depguard.shared.AssertUtil;
import io.depguard.shared.ScanId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Produces safe upgrade paths for the HIGH and CRITICAL findings in a scan. */
@Service
public class RemediationService {

    private final RiskRepository riskRepository;
    private final ScanDependencyRepository scanDependencyRepository;
    private final MavenCentralClient mavenCentralClient;
    private final EolMappingStrategy eolMappingStrategy;
    private final EolFallbackStore eolFallbackStore;
    private final RemediationRepository remediationRepository;

    public RemediationService(
            RiskRepository riskRepository,
            ScanDependencyRepository scanDependencyRepository,
            MavenCentralClient mavenCentralClient,
            EolMappingStrategy eolMappingStrategy,
            EolFallbackStore eolFallbackStore,
            RemediationRepository remediationRepository) {
        this.riskRepository = riskRepository;
        this.scanDependencyRepository = scanDependencyRepository;
        this.mavenCentralClient = mavenCentralClient;
        this.eolMappingStrategy = eolMappingStrategy;
        this.eolFallbackStore = eolFallbackStore;
        this.remediationRepository = remediationRepository;
    }

    @Transactional
    public void generateRecommendations(ScanId scanId) {
        AssertUtil.requireNotNull(scanId, "scanId must not be null");
        Map<String, ScanDependencyCoords> dependencies =
                scanDependencyRepository.findScanDependenciesWithCoords(scanId.id()).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                ScanDependencyCoords::dependencyId, Function.identity()));

        List<RemediationRecommendation> recommendations = riskRepository.findByIdScanId(scanId.id()).stream()
                .filter(assessment -> isActionable(assessment.getRiskLevel()))
                .filter(assessment -> !remediationRepository.existsByScanIdAndDependencyId(
                        scanId.id(), assessment.getId().dependencyId()))
                .map(assessment -> recommendationFor(
                        scanId, dependencies.get(assessment.getId().dependencyId()), assessment))
                .flatMap(java.util.Optional::stream)
                .toList();
        remediationRepository.saveAll(recommendations);
    }

    @Transactional(readOnly = true)
    public List<RemediationResponse> getRecommendations(ScanId scanId) {
        AssertUtil.requireNotNull(scanId, "scanId must not be null");
        return remediationRepository.findByScanId(scanId.id()).stream()
                .map(RemediationResponse::from)
                .toList();
    }

    private java.util.Optional<RemediationRecommendation> recommendationFor(
            ScanId scanId, ScanDependencyCoords dependency, RiskAssessment assessment) {
        if (dependency == null) return java.util.Optional.empty();
        List<String> candidates =
                mavenCentralClient.findCandidateVersions(dependency.groupId(), dependency.artifactId());
        String current = dependency.version();
        java.util.Optional<String> sameMajor = candidates.stream()
                .filter(candidate -> !candidate.equals(current))
                .filter(candidate -> CompatibilityAdvisor.isCompatibleUpgrade(current, candidate))
                .filter(candidate -> isNotEol(dependency, candidate))
                .findFirst();
        if (sameMajor.isPresent()) {
            return java.util.Optional.of(create(scanId, dependency, current, sameMajor.get(), true));
        }
        return candidates.stream()
                .filter(candidate -> !candidate.equals(current))
                .filter(candidate -> !CompatibilityAdvisor.isCompatibleUpgrade(current, candidate))
                .filter(candidate -> isNotEol(dependency, candidate))
                .findFirst()
                .map(candidate -> create(scanId, dependency, current, candidate, false));
    }

    private boolean isNotEol(ScanDependencyCoords dependency, String candidate) {
        return eolMappingStrategy
                .resolve(dependency.groupId(), dependency.artifactId(), candidate)
                .flatMap(eolFallbackStore::lookup)
                .map(info -> info.status() != EolStatus.EOL)
                .orElse(true);
    }

    private static boolean isActionable(RiskLevel level) {
        return level == RiskLevel.HIGH || level == RiskLevel.CRITICAL;
    }

    private static RemediationRecommendation create(
            ScanId scanId, ScanDependencyCoords dependency, String current, String candidate, boolean sameMajor) {
        UpgradeType upgradeType = CompatibilityAdvisor.upgradeType(current, candidate);
        return RemediationRecommendation.of(
                scanId.id(),
                dependency.dependencyId(),
                current,
                candidate,
                upgradeType,
                CompatibilityAdvisor.breakingChangeSummary(dependency.groupId(), current, candidate),
                sameMajor
                        ? "Latest supported version within the current major version."
                        : "No supported same-major version was found; migration is required.",
                sameMajor ? RiskConfidence.HIGH : RiskConfidence.MEDIUM);
    }
}
