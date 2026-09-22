package io.depguard.dependency;

import io.depguard.shared.GitHubUrlValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.springframework.stereotype.Service;

/**
 * Clones a public GitHub repository over anonymous HTTPS with JGit.
 *
 * <p>Security posture: the URL must pass {@link GitHubUrlValidator} (public GitHub HTTPS, no embedded
 * credentials) before anything is cloned, no credentials or SSH keys are ever supplied, submodules are
 * never fetched, and no build is executed — the clone only provides files to read.
 *
 * <p>The clone is shallow: a scan needs the working tree and the head commit, not the whole history.
 */
@Service
public class GitCloneService {

    private static final String CLONE_DIRECTORY_PREFIX = "depguard-clone-";

    private static final int CLONE_TIMEOUT_SECONDS = 120;

    private static final int SHALLOW_CLONE_DEPTH = 1;

    /**
     * Clones {@code repositoryUrl} into a fresh temporary directory.
     *
     * <p>The caller owns the working directory: use the result in a try-with-resources block so that
     * {@link CloneResult#close()} deletes it.
     *
     * @param repositoryUrl the project repository URL (must be a public GitHub HTTPS URL)
     * @param branch        the branch to clone, or {@code null}/blank for the repository default branch
     * @return the clone with its commit SHA and branch
     * @throws IllegalArgumentException if the URL is not a public GitHub repository URL
     * @throws IllegalStateException    if the repository cannot be cloned
     */
    public CloneResult cloneRepository(String repositoryUrl, String branch) {
        String canonicalUrl = GitHubUrlValidator.validateAndNormalize(repositoryUrl);
        Path workingDirectory = createWorkingDirectory();
        try {
            try (Git git = clone(canonicalUrl, branch, workingDirectory)) {
                Repository repository = git.getRepository();
                return new CloneResult(workingDirectory, headCommit(repository), currentBranch(repository, branch));
            }
        } catch (GitAPIException | IOException ex) {
            CloneResult.deleteQuietly(workingDirectory);
            throw new IllegalStateException("Failed to clone " + canonicalUrl + ": " + ex.getMessage(), ex);
        }
    }

    private static Git clone(String canonicalUrl, String branch, Path workingDirectory) throws GitAPIException {
        CloneCommand command = Git.cloneRepository()
                .setURI(canonicalUrl)
                .setDirectory(workingDirectory.toFile())
                .setCloneAllBranches(false)
                .setCloneSubmodules(false)
                .setDepth(SHALLOW_CLONE_DEPTH)
                .setTimeout(CLONE_TIMEOUT_SECONDS);
        if (branch != null && !branch.isBlank()) {
            command.setBranch(branch);
        }
        return command.call();
    }

    private static String headCommit(Repository repository) throws IOException {
        ObjectId head = repository.resolve(Constants.HEAD);
        if (head == null) {
            throw new IOException("Cloned repository has no HEAD commit");
        }
        return head.getName();
    }

    private static String currentBranch(Repository repository, String requestedBranch) throws IOException {
        String branch = repository.getBranch();
        if (branch != null && !branch.isBlank()) {
            return branch;
        }
        return requestedBranch == null || requestedBranch.isBlank() ? Constants.HEAD : requestedBranch;
    }

    private static Path createWorkingDirectory() {
        try {
            return Files.createTempDirectory(CLONE_DIRECTORY_PREFIX);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create a temporary working directory", ex);
        }
    }
}
