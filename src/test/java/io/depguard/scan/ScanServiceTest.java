package io.depguard.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.depguard.dependency.CloneResult;
import io.depguard.dependency.Dependency;
import io.depguard.dependency.DependencyRepository;
import io.depguard.dependency.GitCloneService;
import io.depguard.dependency.MavenDependencyResolver;
import io.depguard.dependency.ResolvedDependency;
import io.depguard.dependency.ScanDependency;
import io.depguard.dependency.ScanDependencyRepository;
import io.depguard.eol.EolService;
import io.depguard.project.ProjectAPI;
import io.depguard.project.ProjectId;
import io.depguard.project.ProjectSnapshot;
import io.depguard.remediation.RemediationService;
import io.depguard.risk.RiskScoringService;
import io.depguard.shared.ResourceNotFoundException;
import io.depguard.shared.ScanId;
import io.depguard.vulnerability.VulnerabilityService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    private static final ProjectId PROJECT_ID = ProjectId.of("0KX1Q2W3E4R5T");

    private static final String REPOSITORY_URL = "https://github.com/depguard-it/payment-service";

    private static final String COMMIT_SHA = "a3f9d1c8b2e4f6a0d5c7e9f1b3a5d7e9c1a3b5d7";

    private final ProjectSnapshot project = new ProjectSnapshot(PROJECT_ID, "payment-service", REPOSITORY_URL, "main");

    @Mock
    ScanRepository scanRepository;

    @Mock
    ProjectAPI projectAPI;

    @Mock
    GitCloneService gitCloneService;

    @Mock
    MavenDependencyResolver dependencyResolver;

    @Mock
    DependencyRepository dependencyRepository;

    @Mock
    ScanDependencyRepository scanDependencyRepository;

    @Mock
    TransactionTemplate transactionTemplate;

    @Mock
    EolService eolService;

    @Mock
    VulnerabilityService vulnerabilityService;

    @Mock
    RiskScoringService riskScoringService;

    @Mock
    RemediationService remediationService;

    @InjectMocks
    ScanService scanService;

    @Test
    void createsPendingScanForAnExistingProject() {
        given(projectAPI.getProject(PROJECT_ID)).willReturn(project);
        given(scanRepository.save(any(Scan.class))).willAnswer(invocation -> invocation.getArgument(0));

        ScanId scanId = scanService.createScan(PROJECT_ID);

        ArgumentCaptor<Scan> saved = ArgumentCaptor.forClass(Scan.class);
        verify(scanRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ScanStatus.PENDING);
        assertThat(saved.getValue().getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(saved.getValue().getStartedAt()).isNull();
        assertThat(saved.getValue().getCommitSha()).isNull();
        assertThat(scanId).isNotNull();
    }

    @Test
    void failsToCreateScanForAnUnknownProject() {
        given(projectAPI.getProject(PROJECT_ID))
                .willThrow(new ResourceNotFoundException("Project not found: " + PROJECT_ID));

        assertThatThrownBy(() -> scanService.createScan(PROJECT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Project not found: " + PROJECT_ID);

        verifyNoInteractions(scanRepository);
    }

    @Test
    void runsThePipelineAndPersistsTheResolvedTree() throws IOException {
        Scan scan = Scan.start(PROJECT_ID);
        Path workingDirectory = Files.createTempDirectory("scan-test-");
        given(projectAPI.getProject(PROJECT_ID)).willReturn(project);
        given(gitCloneService.cloneRepository(REPOSITORY_URL, "main"))
                .willReturn(new CloneResult(workingDirectory, COMMIT_SHA, "main"));
        given(dependencyResolver.resolveDependencies(workingDirectory))
                .willReturn(List.of(
                        new ResolvedDependency("org.springframework", "spring-core", "6.1.8", "compile", true),
                        new ResolvedDependency("junit", "junit", "4.13.2", "test", false)));
        given(dependencyRepository.findByKey(any(), any(), any(), any())).willReturn(Optional.empty());
        given(dependencyRepository.save(any(Dependency.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(scanRepository.findById(scan.getId())).willReturn(Optional.of(scan));
        given(scanRepository.save(any(Scan.class))).willAnswer(invocation -> invocation.getArgument(0));
        runAllTransactionsInline(scan);

        scanService.runScan(scan.getId());

        // the clone's working directory is removed by the pipeline (CloneResult is closed)
        assertThat(workingDirectory).doesNotExist();

        ArgumentCaptor<Scan> saved = ArgumentCaptor.forClass(Scan.class);
        verify(scanRepository, times(3)).save(saved.capture());
        Scan completed = saved.getAllValues().getLast();
        assertThat(completed.getStatus()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(completed.getCommitSha()).isEqualTo(COMMIT_SHA);
        assertThat(completed.getBranch()).isEqualTo("main");
        assertThat(completed.getStartedAt()).isNotNull();
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(completed.getErrorMessage()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScanDependency>> persisted = ArgumentCaptor.forClass(List.class);
        verify(scanDependencyRepository).saveAll(persisted.capture());
        assertThat(persisted.getValue()).hasSize(2);
        assertThat(persisted.getValue().get(0).getId().dependencyId()).isNotBlank();
        assertThat(persisted.getValue().get(0).getScope()).isEqualTo("compile");
        assertThat(persisted.getValue().get(0).isDirect()).isTrue();
        assertThat(persisted.getValue().get(1).getScope()).isEqualTo("test");
        assertThat(persisted.getValue().get(1).isDirect()).isFalse();

        // EOL enrichment must run after dependencies are persisted
        verify(eolService).enrichScan(scan.getId());
        verify(vulnerabilityService).enrichScan(scan.getId());
        verify(riskScoringService).scoreScan(scan.getId());
        verify(remediationService).generateRecommendations(scan.getId());
    }

    @Test
    void marksTheScanFailedWhenTheCloneFails() {
        Scan scan = Scan.start(PROJECT_ID);
        given(projectAPI.getProject(PROJECT_ID)).willReturn(project);
        given(gitCloneService.cloneRepository(REPOSITORY_URL, "main"))
                .willThrow(new IllegalStateException("Failed to clone " + REPOSITORY_URL + ": boom"));
        given(scanRepository.findById(scan.getId())).willReturn(Optional.of(scan));
        given(scanRepository.save(any(Scan.class))).willAnswer(invocation -> invocation.getArgument(0));
        runAllTransactionsInline(scan);

        scanService.runScan(scan.getId());

        ArgumentCaptor<Scan> saved = ArgumentCaptor.forClass(Scan.class);
        // running (from execute()) + failed (from executeWithoutResult in catch)
        verify(scanRepository, times(2)).save(saved.capture());
        Scan failed = saved.getAllValues().getLast();
        assertThat(failed.getStatus()).isEqualTo(ScanStatus.FAILED);
        assertThat(failed.getErrorMessage()).contains("boom");
        assertThat(failed.getCompletedAt()).isNotNull();
        verifyNoInteractions(dependencyResolver, dependencyRepository, scanDependencyRepository);
    }

    @Test
    void marksTheScanFailedWhenResolutionFails() throws IOException {
        Scan scan = Scan.start(PROJECT_ID);
        Path workingDirectory = Files.createTempDirectory("scan-test-");
        given(projectAPI.getProject(PROJECT_ID)).willReturn(project);
        given(gitCloneService.cloneRepository(REPOSITORY_URL, "main"))
                .willReturn(new CloneResult(workingDirectory, COMMIT_SHA, "main"));
        given(dependencyResolver.resolveDependencies(workingDirectory))
                .willThrow(new IllegalArgumentException("No pom.xml found in " + workingDirectory));
        given(scanRepository.findById(scan.getId())).willReturn(Optional.of(scan));
        given(scanRepository.save(any(Scan.class))).willAnswer(invocation -> invocation.getArgument(0));
        runAllTransactionsInline(scan);

        scanService.runScan(scan.getId());

        ArgumentCaptor<Scan> saved = ArgumentCaptor.forClass(Scan.class);
        // running + failed (resolution threw before the clone+persist transaction committed)
        verify(scanRepository, times(2)).save(saved.capture());
        Scan failed = saved.getAllValues().getLast();
        assertThat(failed.getStatus()).isEqualTo(ScanStatus.FAILED);
        assertThat(failed.getErrorMessage()).contains("pom.xml");
        assertThat(workingDirectory).doesNotExist();
        verifyNoInteractions(dependencyRepository, scanDependencyRepository);
    }

    @Test
    void skipsAScanThatIsNotPending() {
        Scan scan = Scan.start(PROJECT_ID);
        scan.markRunning();
        // transactionTemplate.execute() must be stubbed to return null (not-pending path)
        given(transactionTemplate.execute(any())).willReturn(null);

        scanService.runScan(scan.getId());

        verify(scanRepository, never()).save(any());
        verifyNoInteractions(
                projectAPI, gitCloneService, dependencyResolver, dependencyRepository, scanDependencyRepository);
    }

    @Test
    void ignoresAnUnknownScan() {
        // transactionTemplate.execute() must be stubbed to return null (unknown scan path)
        given(transactionTemplate.execute(any())).willReturn(null);

        scanService.runScan(ScanId.of("0KX1Q2W3E4R5S"));

        verify(scanRepository, never()).save(any());
        verifyNoInteractions(
                projectAPI, gitCloneService, dependencyResolver, dependencyRepository, scanDependencyRepository);
    }

    /**
     * Makes the mocked {@link TransactionTemplate} execute callbacks synchronously and inline,
     * so that unit tests exercise the real persistence logic without a real database.
     *
     * <p>{@code execute()} is used for the initial load (returns the Scan). All subsequent writes
     * use {@code executeWithoutResult()}.
     */
    @SuppressWarnings("unchecked")
    private void runAllTransactionsInline(Scan scan) {
        // execute() — used for the initial PENDING check; must invoke the callback and return the scan
        given(transactionTemplate.execute(any())).willAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        });
        // executeWithoutResult() — used for clone+persist, markCompleted, markFailed
        doAnswer(invocation -> {
                    Consumer<TransactionStatus> callback = invocation.getArgument(0);
                    callback.accept(org.mockito.Mockito.mock(TransactionStatus.class));
                    return null;
                })
                .when(transactionTemplate)
                .executeWithoutResult(any());
    }
}
