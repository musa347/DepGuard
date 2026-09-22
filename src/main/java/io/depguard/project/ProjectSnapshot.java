package io.depguard.project;

/**
 * Read-only view of a registered project, as consumed by other modules (the scan pipeline).
 */
public record ProjectSnapshot(ProjectId projectId, String name, String repositoryUrl, String defaultBranch) {

    static ProjectSnapshot from(ProjectResult result) {
        return new ProjectSnapshot(result.id(), result.name(), result.repositoryUrl(), result.defaultBranch());
    }
}
