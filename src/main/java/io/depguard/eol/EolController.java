package io.depguard.eol;

import io.depguard.dependency.ScanDependencyRepository;
import io.depguard.shared.ResourceNotFoundException;
import io.depguard.shared.ScanId;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for EOL enrichment: trigger a check and read results per scan. */
@RestController
@RequestMapping("/api/scans")
class EolController {

    private final EolService eolService;
    private final ScanDependencyRepository scanDependencyRepository;
    private final EolRepository eolRepository;

    EolController(
            EolService eolService, ScanDependencyRepository scanDependencyRepository, EolRepository eolRepository) {
        this.eolService = eolService;
        this.scanDependencyRepository = scanDependencyRepository;
        this.eolRepository = eolRepository;
    }

    /**
     * Triggers EOL enrichment for the given scan.
     *
     * <p>If the scan already has EOL records, the call is idempotent — only new/unenriched dependencies
     * are looked up.
     */
    @PostMapping("/{id}/eol-check")
    ResponseEntity<EolCheckResponse> triggerEolCheck(@PathVariable ScanId id) {
        if (!scanDependencyRepository.existsById_ScanId(id.id())) {
            throw new ResourceNotFoundException("Scan not found: " + id);
        }
        eolService.enrichScan(id);
        return ResponseEntity.accepted().body(new EolCheckResponse(id.id()));
    }

    /** Returns the EOL data for every dependency of the scan (empty list if enrichment has not run). */
    @GetMapping("/{id}/eol")
    ResponseEntity<EolResponse> getEolReport(@PathVariable ScanId id) {
        if (!scanDependencyRepository.existsById_ScanId(id.id())) {
            throw new ResourceNotFoundException("Scan not found: " + id);
        }
        List<EolDependencyEntry> entries = eolRepository.findEolReportByScanId(id.id());
        return ResponseEntity.ok(EolResponse.of(id.id(), entries));
    }
}
