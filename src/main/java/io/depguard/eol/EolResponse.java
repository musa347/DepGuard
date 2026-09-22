package io.depguard.eol;

import java.time.Instant;
import java.util.List;

/** Response payload of {@code GET /api/scans/{id}/eol}. */
public record EolResponse(String scanId, Instant enrichedAt, List<EolDependencyEntry> dependencies) {

    static EolResponse of(String scanId, List<EolDependencyEntry> entries) {
        Instant enrichedAt = entries.isEmpty() ? null : entries.get(0).fetchedAt();
        return new EolResponse(scanId, enrichedAt, entries);
    }
}
