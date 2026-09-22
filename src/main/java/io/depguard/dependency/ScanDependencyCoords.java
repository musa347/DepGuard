package io.depguard.dependency;

/** Projection of a scan dependency with its Maven coordinates — used by the EOL enrichment query. */
public record ScanDependencyCoords(
        String dependencyId,
        String scanId,
        String groupId,
        String artifactId,
        String version,
        String scope,
        boolean direct) {}
