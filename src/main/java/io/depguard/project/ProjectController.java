package io.depguard.project;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Projects", description = "Register public GitHub repositories for scanning")
@RestController
@RequestMapping("/api/projects")
class ProjectController {

    private final ProjectService projectService;

    ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(
            summary = "Register a project",
            description = "Accepts public GitHub repository URLs (https://github.com/owner/repo) only")
    @PostMapping
    ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        ProjectResult project = projectService.createProject(request.toCmd());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(project.id().id())
                .toUri();
        return ResponseEntity.created(location).body(ProjectResponse.from(project));
    }

    @Operation(summary = "List projects", description = "Returns all registered projects, newest first")
    @GetMapping
    List<ProjectResponse> listProjects() {
        return projectService.listProjects().stream().map(ProjectResponse::from).toList();
    }

    @Operation(summary = "Get a project", description = "Returns 404 when the project does not exist")
    @GetMapping("/{id}")
    ProjectResponse getProject(@PathVariable ProjectId id) {
        return ProjectResponse.from(projectService.getProject(id));
    }
}
