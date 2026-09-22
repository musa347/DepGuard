package io.depguard.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload of {@code POST /api/projects}.
 */
public record CreateProjectRequest(
        @NotBlank(message = "Project name must not be blank")
        @Size(max = 255, message = "Project name must not exceed 255 characters")
        String name,

        @NotBlank(message = "Repository URL must not be blank")
        @Size(max = 512, message = "Repository URL must not exceed 512 characters")
        String repositoryUrl,

        @Size(max = 255, message = "Default branch must not exceed 255 characters")
        String defaultBranch) {

    CreateProjectCmd toCmd() {
        return new CreateProjectCmd(name, repositoryUrl, defaultBranch);
    }
}
