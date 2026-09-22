package io.depguard.eol;

import java.time.Instant;
import java.time.LocalDate;

/** One dependency's EOL data in an {@link EolResponse}. */
public record EolDependencyEntry(
        String groupId,
        String artifactId,
        String version,
        EolStatus eolStatus,
        LocalDate eolDate,
        String source,
        Instant fetchedAt) {}
