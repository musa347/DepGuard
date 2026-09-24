package io.depguard.risk;

import io.depguard.dependency.ScanDependencyCoords;
import io.depguard.dependency.ScanDependencyRepository;
import io.depguard.eol.EolRecord;
import io.depguard.eol.EolRepository;
import io.depguard.eol.EolStatus;
import io.depguard.shared.AssertUtil;
import io.depguard.shared.ScanId;
import io.depguard.vulnerability.VulnerabilityRecord;
import io.depguard.vulnerability.VulnerabilityRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calculates and stores a Dependency Risk Heuristic for every dependency in a completed enrichment. */
@Service
public class RiskScoringService {

    private final ScanDependencyRepository scanDependencyRepository;
    private final EolRepository eolRepository;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final RiskRepository riskRepository;

    public RiskScoringService(
            ScanDependencyRepository scanDependencyRepository,
            EolRepository eolRepository,
            VulnerabilityRepository vulnerabilityRepository,
            RiskRepository riskRepository) {
        this.scanDependencyRepository = scanDependencyRepository;
        this.eolRepository = eolRepository;
        this.vulnerabilityRepository = vulnerabilityRepository;
        this.riskRepository = riskRepository;
    }

    @Transactional
    public void scoreScan(ScanId scanId) {
        AssertUtil.requireNotNull(scanId, "scanId must not be null");
        List<RiskAssessment> assessments = scanDependencyRepository.findScanDependenciesWithCoords(scanId.id()).stream()
                .map(dependency -> assess(scanId, dependency))
                .toList();
        riskRepository.saveAll(assessments);
    }

    private RiskAssessment assess(ScanId scanId, ScanDependencyCoords dependency) {
        EolRecord eolRecord = eolRepository.findByScanAndDependencyId(scanId.id(), dependency.dependencyId());
        EolStatus eolStatus = eolRecord == null ? EolStatus.UNKNOWN : eolRecord.getStatus();
        List<io.depguard.vulnerability.Severity> severities =
                vulnerabilityRepository.findByScanAndDependencyId(scanId.id(), dependency.dependencyId()).stream()
                        .map(VulnerabilityRecord::getSeverity)
                        .toList();
        RiskDecision decision = RiskRule.evaluate(eolStatus, severities, dependency.direct());
        return RiskAssessment.of(new RiskAssessmentId(scanId.id(), dependency.dependencyId()), decision);
    }
}
