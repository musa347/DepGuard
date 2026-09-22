package io.depguard.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.depguard.BaseIT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.ExchangeResult;

/**
 * End-to-end scan pipeline over a real GitHub repository and a real database.
 *
 * <p>The polled project points at {@code spring-projects/spring-petclinic}, whose shallow clone is small
 * and whose dependency tree is realistic. Requires outbound access to {@code github.com} and Maven Central.
 */
class ScanApiTest extends BaseIT {

    private static final String PETCLINIC_URL = "https://github.com/spring-projects/spring-petclinic";

    private static final Duration SCAN_TIMEOUT = Duration.ofSeconds(120);

    @Test
    void triggersPollsAndCompletesAScan() {
        String projectId = registerProject(PETCLINIC_URL);

        ExchangeResult created = restTestClient
                .post()
                .uri("/api/projects/{id}/scans", projectId)
                .exchange()
                .expectStatus()
                .isAccepted()
                .returnResult();
        String scanId = scanIdOf(created);

        ScanResponse scan = awaitTerminalState(scanId);
        assertThat(scan.status()).as("scan %s ended as", scanId).isEqualTo("COMPLETED");
        assertThat(scan.commitSha()).matches("[0-9a-f]{40}");
        assertThat(scan.branch()).isEqualTo("main");
        assertThat(scan.startedAt()).isNotNull();
        assertThat(scan.completedAt()).isNotNull();
        assertThat(scan.dependencyCount()).isGreaterThan(10);
        assertThat(scan.dependencies()).hasSize((int) scan.dependencyCount());
        assertThat(scan.dependencies()).allSatisfy(dependency -> {
            assertThat(dependency.groupId()).isNotBlank();
            assertThat(dependency.artifactId()).isNotBlank();
            assertThat(dependency.version()).isNotBlank();
            assertThat(dependency.scope()).isNotBlank();
        });
        // spring-web is resolved transitively through spring-boot-starter-web
        assertThat(scan.dependencies())
                .filteredOn(dependency ->
                        "org.springframework:spring-web".equals(dependency.groupId() + ":" + dependency.artifactId()))
                .allSatisfy(dependency -> assertThat(dependency.direct()).isFalse());

        // a second scan of the same project reuses the persisted dependencies (unique constraint on
        // group/artifact/version/ecosystem) and completes as well
        String rerunScanId = scanIdOf(restTestClient
                .post()
                .uri("/api/projects/{id}/scans", projectId)
                .exchange()
                .expectStatus()
                .isAccepted()
                .returnResult());
        ScanResponse rerun = awaitTerminalState(rerunScanId);
        assertThat(rerun.status()).as("scan %s ended as", rerunScanId).isEqualTo("COMPLETED");
        assertThat(rerun.dependencyCount()).isEqualTo(scan.dependencyCount());
    }

    @Test
    void failsToStartAScanForAnUnknownProject() {
        ExchangeResult notFound = restTestClient
                .post()
                .uri("/api/projects/{id}/scans", "0KX1Q2W3E4R5T")
                .exchange()
                .expectStatus()
                .isNotFound()
                .returnResult();

        assertThat(bodyOf(notFound)).contains("NOT_FOUND").contains("Project not found");
    }

    @Test
    void failsForAnUnknownScan() {
        ExchangeResult notFound = restTestClient
                .get()
                .uri("/api/scans/{id}", "0KX1Q2W3E4R5S")
                .exchange()
                .expectStatus()
                .isNotFound()
                .returnResult();

        assertThat(bodyOf(notFound)).contains("NOT_FOUND").contains("Scan not found");
    }

    private String registerProject(String repositoryUrl) {
        ExchangeResult created = restTestClient
                .post()
                .uri("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"petclinic-scan","repositoryUrl":"%s"}
                        """.formatted(repositoryUrl))
                .exchange()
                .expectStatus()
                .isCreated()
                .returnResult();
        return bodyOf(created).replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private static String scanIdOf(ExchangeResult created) {
        return bodyOf(created).replaceAll(".*\"scanId\":\"([^\"]+)\".*", "$1");
    }

    /** Polls {@code GET /api/scans/{id}} until the scan reaches a terminal state (or the deadline passes). */
    private ScanResponse awaitTerminalState(String scanId) {
        Instant deadline = Instant.now().plus(SCAN_TIMEOUT);
        ScanResponse last = null;
        while (last == null
                || (!last.status().equals("COMPLETED") && !last.status().equals("FAILED"))) {
            if (Instant.now().isAfter(deadline)) {
                fail("Scan %s did not finish within %s (last status: %s)"
                        .formatted(scanId, SCAN_TIMEOUT, last == null ? "n/a" : last.status()));
            }
            last = restTestClient
                    .get()
                    .uri("/api/scans/{id}", scanId)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .returnResult(ScanResponse.class)
                    .getResponseBody();
            if (last == null
                    || (!last.status().equals("COMPLETED") && !last.status().equals("FAILED"))) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    fail("Polling was interrupted");
                }
            }
        }
        return last;
    }

    private static String bodyOf(ExchangeResult result) {
        return new String(result.getResponseBodyContent(), StandardCharsets.UTF_8);
    }
}
