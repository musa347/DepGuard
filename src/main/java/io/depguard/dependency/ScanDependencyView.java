package io.depguard.dependency;

/**
 * Read projection of a scan's dependencies: Maven coordinates plus how they were resolved.
 */
public record ScanDependencyView(String groupId, String artifactId, String version, String scope, boolean direct) {}
