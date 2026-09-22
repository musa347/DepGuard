package io.depguard.project;

import io.depguard.shared.GitHubUrlValidator;
import io.depguard.shared.ResourceNotFoundException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers and retrieves the projects DepGuard can scan.
 */
@Service
class ProjectService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ProjectRepository projectRepository;

    ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /**
     * Registers a public GitHub repository as a project.
     *
     * <p>The repository URL is validated and canonicalised before it is stored, and each repository can be
     * registered only once — a database-level unique constraint backstops the check below.
     *
     * @throws IllegalArgumentException if the URL is not a public GitHub URL or is already registered
     */
    @Transactional
    ProjectResult createProject(CreateProjectCmd cmd) {
        String repositoryUrl = GitHubUrlValidator.validateAndNormalize(cmd.repositoryUrl());
        if (projectRepository.existsByRepositoryUrl(repositoryUrl)) {
            throw new IllegalArgumentException("Project already registered for repository URL: " + repositoryUrl);
        }
        Project project = projectRepository.save(Project.register(cmd.name(), repositoryUrl, cmd.defaultBranch()));
        return ProjectResult.from(project);
    }

    /**
     * @throws io.depguard.shared.ResourceNotFoundException if no project exists for the given id
     */
    @Transactional(readOnly = true)
    ProjectResult getProject(ProjectId projectId) {
        Project project = projectRepository
                .findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        return ProjectResult.from(project);
    }

    @Transactional(readOnly = true)
    List<ProjectResult> listProjects() {
        return projectRepository.findAll(NEWEST_FIRST).stream()
                .map(ProjectResult::from)
                .toList();
    }
}
