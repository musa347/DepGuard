package io.depguard.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.depguard.project.ProjectId;
import io.depguard.remediation.RemediationResponse;
import io.depguard.remediation.RemediationService;
import io.depguard.remediation.UpgradeType;
import io.depguard.risk.RiskConfidence;
import io.depguard.risk.RiskLevel;
import io.depguard.shared.ResourceNotFoundException;
import io.depguard.shared.ScanId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ScanController.class)
class ScanControllerTest {

    private static final String PROJECT_ID = "0KX1Q2W3E4R5T";

    private static final String SCAN_ID = "0KX1Q2W3E4R5S";

    private static final ScanResponse COMPLETED_SCAN = new ScanResponse(
            SCAN_ID,
            "COMPLETED",
            "a3f9d1c8b2e4f6a0d5c7e9f1b3a5d7e9c1a3b5d7",
            "main",
            Instant.parse("2026-09-22T10:00:00Z"),
            Instant.parse("2026-09-22T10:00:20Z"),
            null,
            1,
            List.of(new ScanDependencyResponse("org.springframework", "spring-core", "6.1.8", "compile", true)));

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    ScanService scanService;

    @MockitoBean
    RemediationService remediationService;

    @MockitoBean
    ScanReportService scanReportService;

    @Test
    void startsAScanAndReturnsTheIdToPoll() {
        given(scanService.createScan(ProjectId.of(PROJECT_ID))).willReturn(ScanId.of(SCAN_ID));

        assertThat(mockMvc.post().uri("/api/projects/{projectId}/scans", PROJECT_ID))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.scanId")
                .isEqualTo(SCAN_ID);

        verify(scanService).runScan(ScanId.of(SCAN_ID));
    }

    @Test
    void failsToStartAScanForAnUnknownProject() {
        given(scanService.createScan(ProjectId.of(PROJECT_ID)))
                .willThrow(new ResourceNotFoundException("Project not found: " + PROJECT_ID));

        assertThat(mockMvc.post().uri("/api/projects/{projectId}/scans", PROJECT_ID))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.error")
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void returnsACompletedScanWithItsDependencies() {
        given(scanService.getScan(ScanId.of(SCAN_ID))).willReturn(COMPLETED_SCAN);

        assertThat(mockMvc.get().uri("/api/scans/{id}", SCAN_ID))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("COMPLETED");

        assertThat(mockMvc.get().uri("/api/scans/{id}", SCAN_ID))
                .bodyJson()
                .extractingPath("$.dependencyCount")
                .isEqualTo(1);

        assertThat(mockMvc.get().uri("/api/scans/{id}", SCAN_ID))
                .bodyJson()
                .extractingPath("$.dependencies[0].groupId")
                .isEqualTo("org.springframework");
    }

    @Test
    void returnsARunningScanWithoutDependencies() {
        ScanResponse running = new ScanResponse(
                SCAN_ID, "RUNNING", null, null, Instant.parse("2026-09-22T10:00:00Z"), null, null, 0, List.of());
        given(scanService.getScan(ScanId.of(SCAN_ID))).willReturn(running);

        assertThat(mockMvc.get().uri("/api/scans/{id}", SCAN_ID))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.dependencies")
                .asArray()
                .isEmpty();
    }

    @Test
    void failsForAnUnknownScan() {
        given(scanService.getScan(ScanId.of(SCAN_ID)))
                .willThrow(new ResourceNotFoundException("Scan not found: " + SCAN_ID));

        assertThat(mockMvc.get().uri("/api/scans/{id}", SCAN_ID))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.message")
                .isEqualTo("Scan not found: " + SCAN_ID);

        verify(scanService).getScan(ScanId.of(SCAN_ID));
    }

    @Test
    void returnsCompatibilityAwareRecommendations() {
        given(remediationService.getRecommendations(ScanId.of(SCAN_ID)))
                .willReturn(List.of(new RemediationResponse(
                        "0RZKMN9SVD511",
                        "2.7.18",
                        "3.4.1",
                        UpgradeType.MAJOR,
                        "Spring Boot 2.x → 3.x requires javax → jakarta migration and Java 17+.",
                        "No supported same-major version was found; migration is required.",
                        RiskConfidence.MEDIUM)));

        assertThat(mockMvc.get().uri("/api/scans/{id}/recommendations", SCAN_ID))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].upgradeType")
                .isEqualTo("MAJOR");
    }

    @Test
    void returnsACompleteReportForACompletedScan() {
        given(scanReportService.getReport(ScanId.of(SCAN_ID)))
                .willReturn(new ScanReportDto(
                        SCAN_ID,
                        "payment-service",
                        "https://github.com/depguard/payment-service",
                        "a3f9d1c8b2e4f6a0d5c7e9f1b3a5d7e9c1a3b5d7",
                        "main",
                        Instant.parse("2026-09-22T10:00:20Z"),
                        RiskLevel.HIGH,
                        new ScanReportDto.DataSources(
                                Instant.parse("2026-09-22T10:00:10Z"), Instant.parse("2026-09-22T10:00:11Z")),
                        new ScanReportDto.Summary(1, 1, 1, 0, 1, 0, 0, 0),
                        List.of()));

        assertThat(mockMvc.get().uri("/api/scans/{id}/report", SCAN_ID))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.overallHealth")
                .isEqualTo("HIGH");
    }
}
