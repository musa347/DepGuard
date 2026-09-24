package io.depguard.project;

import io.depguard.shared.GitHubUrlValidator;
import io.depguard.shared.ResourceNotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Registers and retrieves the projects DepGuard can scan.
 */
@Service
class ProjectService {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectService.class);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ProjectRepository projectRepository;
    private final WebClient webClient;

    ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
        this.webClient = WebClient.builder().baseUrl("https://api.github.com").build();
    }

    @Transactional
    ProjectResult createProject(CreateProjectCmd cmd) {
        String repositoryUrl = GitHubUrlValidator.validateAndNormalize(cmd.repositoryUrl());
        if (projectRepository.existsByRepositoryUrl(repositoryUrl)) {
            throw new IllegalArgumentException("Project already registered for repository URL: " + repositoryUrl);
        }
        String branch = resolveDefaultBranch(repositoryUrl, cmd.defaultBranch());
        Project project = projectRepository.save(Project.register(cmd.name(), repositoryUrl, branch));
        return ProjectResult.from(project);
    }

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

    @Transactional
    void deleteProject(ProjectId projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found: " + projectId);
        }
        projectRepository.deleteById(projectId);
        LOG.info("Deleted project {}", projectId);
    }

    /**
     * Returns the caller-supplied branch if non-blank, otherwise queries the GitHub API for the
     * repository's actual default branch. Falls back to {@code "main"} only if the API call fails.
     */
    private String resolveDefaultBranch(String repositoryUrl, String requestedBranch) {
        if (requestedBranch != null && !requestedBranch.isBlank()) {
            return requestedBranch.trim();
        }
        // Extract owner/repo from https://github.com/owner/repo
        String path = repositoryUrl.replace("https://github.com/", "").replaceAll("\\.git$", "");
        try {
            GithubRepoResponse repo = webClient
                    .get()
                    .uri("/repos/{path}", path)
                    .retrieve()
                    .bodyToMono(GithubRepoResponse.class)
                    .block();
            if (repo != null
                    && repo.defaultBranch() != null
                    && !repo.defaultBranch().isBlank()) {
                LOG.info("Detected default branch '{}' for {}", repo.defaultBranch(), repositoryUrl);
                return repo.defaultBranch();
            }
        } catch (Exception ex) {
            LOG.warn(
                    "Could not detect default branch for {} — falling back to 'main': {}",
                    repositoryUrl,
                    ex.getMessage());
        }
        return Project.DEFAULT_BRANCH;
    }

    private record GithubRepoResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("default_branch")
            String defaultBranch) {}
}
