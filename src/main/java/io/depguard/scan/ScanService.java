package io.depguard.scan;

import io.depguard.dependency.DependencyRepository;
import io.depguard.dependency.Ecosystem;
import io.depguard.dependency.GitCloneService;
import io.depguard.dependency.MavenDependencyResolver;
import io.depguard.dependency.ResolvedDependency;
import io.depguard.dependency.ScanDependency;
import io.depguard.dependency.ScanDependencyId;
import io.depguard.dependency.ScanDependencyRepository;
import io.depguard.dependency.ScanDependencyView;
import io.depguard.eol.EolService;
import io.depguard.project.ProjectAPI;
import io.depguard.project.ProjectId;
import io.depguard.project.ProjectSnapshot;
import io.depguard.shared.ResourceNotFoundException;
import io.depguard.shared.ScanId;
import io.depguard.vulnerability.VulnerabilityService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates scans and runs their pipeline asynchronously: clone → resolve → persist → EOL enrichment.
 *
 * <p>Advisory enrichment (Task 6) will run in parallel with EOL enrichment via
 * {@code CompletableFuture.allOf()} once that module is wired in.
 */
@Service
public class ScanService {

    private static final Logger LOG = LoggerFactory.getLogger(ScanService.class);

    private final ScanRepository scanRepository;

    private final ProjectAPI projectAPI;

    private final GitCloneService gitCloneService;

    private final MavenDependencyResolver dependencyResolver;

    private final DependencyRepository dependencyRepository;

    private final ScanDependencyRepository scanDependencyRepository;

    private final TransactionTemplate transactionTemplate;

    private final EolService eolService;
    private final VulnerabilityService vulnerabilityService;

    ScanService(
            ScanRepository scanRepository,
            ProjectAPI projectAPI,
            GitCloneService gitCloneService,
            MavenDependencyResolver dependencyResolver,
            DependencyRepository dependencyRepository,
            ScanDependencyRepository scanDependencyRepository,
            TransactionTemplate transactionTemplate,
            EolService eolService,
            VulnerabilityService vulnerabilityService) {
        this.scanRepository = scanRepository;
        this.projectAPI = projectAPI;
        this.gitCloneService = gitCloneService;
        this.dependencyResolver = dependencyResolver;
        this.dependencyRepository = dependencyRepository;
        this.scanDependencyRepository = scanDependencyRepository;
        this.transactionTemplate = transactionTemplate;
        this.eolService = eolService;
        this.vulnerabilityService = vulnerabilityService;
    }

    /**
     * Creates a {@link ScanStatus#PENDING} scan for an existing project.
     *
     * @throws io.depguard.shared.ResourceNotFoundException if the project does not exist
     */
    @Transactional
    public ScanId createScan(ProjectId projectId) {
        projectAPI.getProject(projectId);
        return scanRepository.save(Scan.start(projectId)).getId();
    }

    /**
     * Runs the scan pipeline. Only scans still in {@link ScanStatus#PENDING} are executed, so a scan can
     * never run twice.
     */
    @Async(AsyncScanConfig.SCAN_EXECUTOR)
    @Transactional
    public void runScan(ScanId scanId) {
        Scan scan = scanRepository.findById(scanId).orElse(null);
        if (scan == null || scan.getStatus() != ScanStatus.PENDING) {
            LOG.warn("Scan {} is not pending — skipping execution", scanId);
            return;
        }
        scan.markRunning();
        scan = scanRepository.save(scan);
        LOG.info("Scan {} started", scanId);
        try {
            ProjectSnapshot project = projectAPI.getProject(scan.getProjectId());
            try (io.depguard.dependency.CloneResult clone =
                    gitCloneService.cloneRepository(project.repositoryUrl(), project.defaultBranch())) {
                scan.cloned(clone.commitSha(), clone.branch());
                scan = scanRepository.save(scan);
                List<ResolvedDependency> dependencies =
                        dependencyResolver.resolveDependencies(clone.workingDirectory());
                transactionTemplate.executeWithoutResult(status -> persist(scanId, dependencies));
                LOG.info("Scan {} resolved {} dependencies", scanId, dependencies.size());
            }
            // EOL + advisory enrichment run in parallel after the dependency tree is committed.
            CompletableFuture<Void> eolFuture = CompletableFuture.runAsync(() -> eolService.enrichScan(scanId));
            CompletableFuture<Void> vulnFuture =
                    CompletableFuture.runAsync(() -> vulnerabilityService.enrichScan(scanId));
            CompletableFuture.allOf(eolFuture, vulnFuture).join();
            scan.markCompleted();
            scan = scanRepository.save(scan);
            LOG.info("Scan {} completed", scanId);
        } catch (Exception ex) {
            LOG.error("Scan {} failed", scanId, ex);
            scan.markFailed(errorMessageOf(ex));
            scan = scanRepository.save(scan);
        }
    }

    /**
     * @throws io.depguard.shared.ResourceNotFoundException if the scan does not exist
     */
    @Transactional(readOnly = true)
    public ScanResponse getScan(ScanId scanId) {
        Scan scan = scanRepository
                .findById(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan not found: " + scanId));
        List<ScanDependencyView> dependencies = scan.getStatus() == ScanStatus.COMPLETED
                ? scanDependencyRepository.findDependenciesOfScan(scanId.id())
                : List.of();
        return ScanResponse.from(scan, dependencies);
    }

    private static String errorMessageOf(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    /** Persists the resolved tree: deduplicated {@code dependencies} rows plus the scan's join rows. */
    private void persist(ScanId scanId, List<ResolvedDependency> resolvedDependencies) {
        List<ScanDependency> scanDependencies = new ArrayList<>(resolvedDependencies.size());
        for (ResolvedDependency resolved : resolvedDependencies) {
            io.depguard.dependency.Dependency dependency = dependencyRepository
                    .findByKey(resolved.groupId(), resolved.artifactId(), resolved.version(), Ecosystem.MAVEN)
                    .orElseGet(() -> dependencyRepository.save(io.depguard.dependency.Dependency.maven(
                            resolved.groupId(), resolved.artifactId(), resolved.version())));
            scanDependencies.add(new ScanDependency(
                    new ScanDependencyId(scanId.id(), dependency.getId().id()), resolved.scope(), resolved.direct()));
        }
        scanDependencyRepository.saveAll(scanDependencies);
    }
}
