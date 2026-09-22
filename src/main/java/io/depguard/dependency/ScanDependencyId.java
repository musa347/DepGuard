package io.depguard.dependency;

/**
 * Composite key of a {@link ScanDependency}: the scan and the dependency it resolved to.
 */
public record ScanDependencyId(String scanId, String dependencyId) {}
