package io.depguard.scan;

import io.depguard.dependency.ScanDependencyView;

/**
 * One entry of the dependency list returned by {@code GET /api/scans/{id}}.
 */
public record ScanDependencyResponse(String groupId, String artifactId, String version, String scope, boolean direct) {

    static ScanDependencyResponse from(ScanDependencyView view) {
        return new ScanDependencyResponse(
                view.groupId(), view.artifactId(), view.version(), view.scope(), view.direct());
    }
}
