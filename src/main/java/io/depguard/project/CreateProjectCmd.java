package io.depguard.project;

/**
 * Input of {@link ProjectService#createProject(CreateProjectCmd)}.
 *
 * <p>A {@code null} or blank {@code defaultBranch} means "use the repository default" ({@code main}).
 */
record CreateProjectCmd(String name, String repositoryUrl, String defaultBranch) {}
