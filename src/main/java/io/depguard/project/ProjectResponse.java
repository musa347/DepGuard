package io.depguard.project;

import java.time.Instant;

/**
 * Response payload of the {@code /api/projects} endpoints.
 */
public record ProjectResponse(String id, String name, String repositoryUrl, String defaultBranch, Instant createdAt) {

    static ProjectResponse from(ProjectResult result) {
        return new ProjectResponse(
                result.id().id(), result.name(), result.repositoryUrl(), result.defaultBranch(), result.createdAt());
    }
}
