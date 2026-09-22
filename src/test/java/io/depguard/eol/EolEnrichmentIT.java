package io.depguard.eol;

import static org.assertj.core.api.Assertions.assertThat;

import io.depguard.BaseIT;
import io.depguard.shared.IdGenerator;
import io.depguard.shared.ScanId;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Integration test: verifies that {@link EolService#enrichScan(ScanId)} correctly derives
 * {@link EolStatus#EOL} for a Spring Boot 2.7.x dependency against a simulated endoflife.date API.
 *
 * <p>A {@link MockWebServer} stands in for endoflife.date. Test data is inserted via
 * {@link JdbcTemplate} to avoid coupling to package-private entity factory methods.
 * The full path from API response → {@link EolService} → persisted {@link EolRecord} is exercised
 * against a real PostgreSQL database (Testcontainers).
 */
class EolEnrichmentIT extends BaseIT {

    // ── endoflife.date JSON for spring-boot / 2.7 — EOL since 2023-11-24 ─────────────────────────
    private static final String SPRING_BOOT_27_EOL_RESPONSE = """
            {
              "cycle": "2.7",
              "release": "2019-09-30",
              "eol": "2023-11-24",
              "latest": "2.7.18",
              "latestRelease": "2023-11-16",
              "support": null,
              "docs": "https://endoflife.date/spring-boot",
              "blog": null,
              "discuss": null,
              "stackoverflow": null
            }
            """;

    private MockWebServer mockEndoflifeDate;

    @Autowired
    private EolService eolService;

    @Autowired
    private EolRepository eolRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Declaring this {@code @MockitoBean} is required so Spring Boot's context setup allows
     * overriding the {@code eolWebClient} bean. The actual client is replaced via reflection
     * in {@link #startMockServer()} once MockWebServer has started.
     */
    @MockitoBean(name = "eolWebClient")
    WebClient eolWebClient;

    @BeforeEach
    void startMockServer() throws Exception {
        mockEndoflifeDate = new MockWebServer();
        mockEndoflifeDate.start();
        WebClient mockClient = WebClient.builder()
                .baseUrl(mockEndoflifeDate.url("/").toString())
                .build();
        // Point the real EolClient at the mock server
        ReflectionTestUtils.setField(applicationContext.getBean(EolClient.class), "webClient", mockClient);
    }

    @AfterEach
    void stopMockServer() throws Exception {
        mockEndoflifeDate.shutdown();
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────────

    /** Inserts a minimal project row and returns its generated ID. */
    private String insertProject(String name, String repoUrl) {
        String id = IdGenerator.generate();
        jdbc.update("""
                INSERT INTO projects (id, name, repository_url, default_branch, created_at, updated_at, version)
                VALUES (?, ?, ?, 'main', now(), now(), 0)
                """, id, name, repoUrl);
        return id;
    }

    /** Inserts a minimal scan row in PENDING status and returns its generated ID. */
    private String insertScan(String projectId) {
        String id = IdGenerator.generate();
        jdbc.update("""
                INSERT INTO scans (id, project_id, status, created_at, updated_at, version)
                VALUES (?, ?, 'PENDING', now(), now(), 0)
                """, id, projectId);
        return id;
    }

    /** Inserts a dependency row and returns its generated ID. */
    private String insertDependency(String groupId, String artifactId, String version) {
        String id = IdGenerator.generate();
        jdbc.update("""
                INSERT INTO dependencies (id, group_id, artifact_id, version, ecosystem, version_col, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'MAVEN', 0, now(), now())
                """, id, groupId, artifactId, version);
        return id;
    }

    /** Links a dependency to a scan via the scan_dependencies join table. */
    private void insertScanDependency(String scanId, String dependencyId) {
        jdbc.update("""
                INSERT INTO scan_dependencies (scan_id, dependency_id, scope, direct)
                VALUES (?, ?, 'compile', true)
                """, scanId, dependencyId);
    }

    // ── tests ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    void springBoot27xDependencyIsEnrichedAsEol() throws Exception {
        // endoflife.date returns EOL date 2023-11-24 (in the past)
        mockEndoflifeDate.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json").setBody(SPRING_BOOT_27_EOL_RESPONSE));

        String projectId = insertProject("sb27-project", "https://github.com/test/sb27-project");
        String scanId = insertScan(projectId);
        String depId = insertDependency("org.springframework.boot", "spring-boot-starter", "2.7.18");
        insertScanDependency(scanId, depId);

        // Act
        eolService.enrichScan(ScanId.of(scanId));

        // Assert
        List<EolRecord> records = eolRepository.findByScanId(scanId);
        assertThat(records).hasSize(1);

        EolRecord record = records.get(0);
        assertThat(record.getStatus())
                .as("spring-boot 2.7 EOL date 2023-11-24 is in the past — must be EOL")
                .isEqualTo(EolStatus.EOL);
        assertThat(record.getEolDate()).isEqualTo(java.time.LocalDate.of(2023, 11, 24));
        assertThat(record.getSource()).isEqualTo("API");
        assertThat(record.getDataSourceFetchedAt()).isNotNull();
        assertThat(record.getId().dependencyId()).isEqualTo(depId);
        assertThat(record.getId().scanId()).isEqualTo(scanId);

        // Verify the mock received the correct endoflife.date request path
        okhttp3.mockwebserver.RecordedRequest request = mockEndoflifeDate.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/spring-boot/2.7.json");
    }

    @Test
    void unknownDependencyIsRecordedWithNoMappingStatus() {
        // No mock response needed — mapping strategy returns empty before EolClient is ever called
        String projectId = insertProject("unknown-project", "https://github.com/test/unknown-project");
        String scanId = insertScan(projectId);
        String depId = insertDependency("com.example", "some-internal-lib", "1.0.0");
        insertScanDependency(scanId, depId);

        eolService.enrichScan(ScanId.of(scanId));

        List<EolRecord> records = eolRepository.findByScanId(scanId);
        assertThat(records).hasSize(1);

        EolRecord record = records.get(0);
        assertThat(record.getStatus())
                .as("com.example has no mapping — must be UNKNOWN")
                .isEqualTo(EolStatus.UNKNOWN);
        assertThat(record.getSource()).isEqualTo("NO_MAPPING");
        assertThat(record.getEolDate()).isNull();
    }

    @Test
    void fallsBackToEmbeddedDatasetWhenApiIsUnavailable() throws Exception {
        // endoflife.date returns 500 twice (exhausts the single retry)
        mockEndoflifeDate.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));
        mockEndoflifeDate.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));

        String projectId = insertProject("fallback-project", "https://github.com/test/fallback-project");
        String scanId = insertScan(projectId);
        // spring-boot 2.7.18 has an entry in eol-fallback.yml with eol: 2023-11-24
        String depId = insertDependency("org.springframework.boot", "spring-boot-starter", "2.7.18");
        insertScanDependency(scanId, depId);

        eolService.enrichScan(ScanId.of(scanId));

        List<EolRecord> records = eolRepository.findByScanId(scanId);
        assertThat(records).hasSize(1);

        EolRecord record = records.get(0);
        assertThat(record.getStatus())
                .as("API is down but fallback dataset has 2.7 entry with past EOL date — must be EOL")
                .isEqualTo(EolStatus.EOL);
        assertThat(record.getSource())
                .as("data came from the embedded fallback dataset, not the live API")
                .isEqualTo("FALLBACK");
    }
}
