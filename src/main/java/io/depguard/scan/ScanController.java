package io.depguard.scan;

import io.depguard.project.ProjectId;
import io.depguard.remediation.RemediationResponse;
import io.depguard.remediation.RemediationService;
import io.depguard.shared.ScanId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Scans", description = "Trigger and inspect dependency scans")
@RestController
@RequestMapping("/api")
class ScanController {

    private final ScanService scanService;
    private final RemediationService remediationService;
    private final ScanReportService scanReportService;

    ScanController(
            ScanService scanService, RemediationService remediationService, ScanReportService scanReportService) {
        this.scanService = scanService;
        this.remediationService = remediationService;
        this.scanReportService = scanReportService;
    }

    @Operation(
            summary = "Trigger a scan",
            description = "Clones the project's repository and resolves its dependency tree asynchronously; "
                    + "returns 202 with the scan id to poll immediately")
    @PostMapping("/projects/{projectId}/scans")
    ResponseEntity<ScanCreatedResponse> startScan(@PathVariable ProjectId projectId) {
        ScanId scanId = scanService.createScan(projectId);
        scanService.runScan(scanId);
        return ResponseEntity.accepted().body(new ScanCreatedResponse(scanId.id()));
    }

    @Operation(
            summary = "Get a scan",
            description = "Returns the scan status and, once completed, its resolved dependencies; "
                    + "returns 404 when the scan does not exist")
    @GetMapping("/scans/{id}")
    ScanResponse getScan(@PathVariable ScanId id) {
        return scanService.getScan(id);
    }

    @Operation(summary = "Get remediation recommendations")
    @GetMapping("/scans/{id}/recommendations")
    java.util.List<RemediationResponse> getRecommendations(@PathVariable ScanId id) {
        return remediationService.getRecommendations(id);
    }

    @Operation(summary = "Get complete reproducible scan report")
    @GetMapping("/scans/{id}/report")
    ScanReportDto getReport(@PathVariable ScanId id) {
        return scanReportService.getReport(id);
    }
}
