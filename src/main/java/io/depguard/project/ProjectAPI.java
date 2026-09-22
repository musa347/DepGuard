package io.depguard.project;

import org.springframework.stereotype.Component;

/**
 * Public API of the project module: lets other modules (the scan pipeline) look up registered projects
 * without exposing the service, entities or repositories.
 */
@Component
public class ProjectAPI {

    private final ProjectService projectService;

    ProjectAPI(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * @throws io.depguard.shared.ResourceNotFoundException if no project exists for the given id
     */
    public ProjectSnapshot getProject(ProjectId projectId) {
        return ProjectSnapshot.from(projectService.getProject(projectId));
    }
}
