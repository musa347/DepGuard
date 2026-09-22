package io.depguard.eol;

import io.depguard.dependency.ScanDependencyCoords;
import io.depguard.dependency.ScanDependencyRepository;
import io.depguard.shared.AssertUtil;
import io.depguard.shared.ScanId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enriches a completed scan's dependencies with EOL data from endoflife.date.
 *
 * <p>EOL enrichment is a follow-up to the scan pipeline — it runs asynchronously and does not block the
 * scan's {@code COMPLETED} transition. A dependency with no mapping or a 404 from endoflife.date is
 * recorded as {@link EolStatus#UNKNOWN} with {@code source = NO_MAPPING} or {@code source = API} (with
 * empty eol date), never silently scored as {@code LOW}.
 *
 * <p>When the live API is unavailable (empty response after retry), the service falls back to the
 * embedded {@link EolFallbackStore} dataset before recording {@code UNKNOWN}. The {@code source}
 * field distinguishes {@code "API"}, {@code "FALLBACK"}, and {@code "NO_MAPPING"} for reproducibility.
 */
@Service
public class EolService {

    private static final Logger LOG = LoggerFactory.getLogger(EolService.class);

    private final EolRepository eolRepository;
    private final ScanDependencyRepository scanDependencyRepository;
    private final EolMappingStrategy mappingStrategy;
    private final EolClient eolClient;
    private final EolFallbackStore fallbackStore;

    public EolService(
            EolRepository eolRepository,
            ScanDependencyRepository scanDependencyRepository,
            EolMappingStrategy mappingStrategy,
            EolClient eolClient,
            EolFallbackStore fallbackStore) {
        this.eolRepository = eolRepository;
        this.scanDependencyRepository = scanDependencyRepository;
        this.mappingStrategy = mappingStrategy;
        this.eolClient = eolClient;
        this.fallbackStore = fallbackStore;
    }

    /**
     * Enriches all dependencies of a scan with EOL data.
     *
     * <p>Safe to call multiple times for the same scan — existing records are skipped.
     */
    @Transactional
    public void enrichScan(ScanId scanId) {
        AssertUtil.requireNotNull(scanId, "scanId must not be null");
        List<ScanDependencyCoords> scanDeps = scanDependencyRepository.findScanDependenciesWithCoords(scanId.id());
        if (scanDeps.isEmpty()) {
            LOG.info("Scan {} has no dependencies — nothing to enrich", scanId);
            return;
        }
        List<EolRecord> records = new java.util.ArrayList<>(scanDeps.size());
        Instant fetchedAt = Instant.now();
        for (ScanDependencyCoords dep : scanDeps) {
            EolRecord existing = eolRepository.findByScanAndDependencyId(scanId.id(), dep.dependencyId());
            if (existing != null) {
                continue; // already enriched
            }
            records.add(resolveAndRecord(dep, fetchedAt));
        }
        if (!records.isEmpty()) {
            eolRepository.saveAll(records);
            LOG.info("Enriched {} EOL records for scan {}", records.size(), scanId);
        }
    }

    private EolRecord resolveAndRecord(ScanDependencyCoords dep, Instant fetchedAt) {
        Optional<ProductCycle> productCycle = mappingStrategy.resolve(dep.groupId(), dep.artifactId(), dep.version());
        if (productCycle.isEmpty()) {
            LOG.info(
                    "No EOL mapping for {}:{}:{} — recording UNKNOWN/NO_MAPPING",
                    dep.groupId(),
                    dep.artifactId(),
                    dep.version());
            return EolRecord.of(
                    new EolRecordId(dep.dependencyId(), dep.scanId()), EolStatus.UNKNOWN, "NO_MAPPING", fetchedAt);
        }

        // 1. Try the live API first
        Optional<EolInfo> info = eolClient.fetch(productCycle.get());
        if (info.isPresent()) {
            LOG.info(
                    "Fetched EOL for {}/{} from API → status {}",
                    productCycle.get().product(),
                    productCycle.get().cycle(),
                    info.get().status());
            return EolRecord.of(new EolRecordId(dep.dependencyId(), dep.scanId()), info.get(), "API");
        }

        // 2. API returned empty (404 or failed after retry) — try the embedded fallback dataset
        Optional<EolInfo> fallback = fallbackStore.lookup(productCycle.get());
        if (fallback.isPresent()) {
            LOG.info(
                    "endoflife.date API unavailable for {}/{} — using embedded fallback → status {}",
                    productCycle.get().product(),
                    productCycle.get().cycle(),
                    fallback.get().status());
            return EolRecord.of(new EolRecordId(dep.dependencyId(), dep.scanId()), fallback.get(), "FALLBACK");
        }

        // 3. Neither API nor fallback has data — record UNKNOWN
        LOG.info(
                "No EOL data available for {}/{} from API or fallback — recording UNKNOWN",
                productCycle.get().product(),
                productCycle.get().cycle());
        return EolRecord.of(new EolRecordId(dep.dependencyId(), dep.scanId()), EolStatus.UNKNOWN, "API", fetchedAt);
    }
}
