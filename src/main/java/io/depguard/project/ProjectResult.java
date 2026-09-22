package io.depguard.project;

import java.time.Instant;

/**
 * Output of the {@link ProjectService} read and write operations.
 */
record ProjectResult(ProjectId id, String name, String repositoryUrl, String defaultBranch, Instant createdAt) {

    static ProjectResult from(Project project) {
        return new ProjectResult(
                project.getId(),
                project.getName(),
                project.getRepositoryUrl(),
                project.getDefaultBranch(),
                project.getCreatedAt());
    }
}
