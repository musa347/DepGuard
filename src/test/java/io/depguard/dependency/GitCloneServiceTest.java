package io.depguard.dependency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.depguard.shared.GitHubUrlValidator;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.jupiter.api.Test;

/**
 * Clones a small public GitHub repository with JGit.
 *
 * <p>These tests need outbound access to {@code github.com}; when it is unavailable they are skipped
 * instead of failing, so the suite still runs in an offline environment.
 */
class GitCloneServiceTest {

    private static final String SAMPLE_REPOSITORY = "https://github.com/octocat/Hello-World";

    private static final String SAMPLE_BRANCH = "master";

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;

    private final GitCloneService gitCloneService = new GitCloneService();

    @Test
    void rejectsRepositoriesThatAreNotPublicGitHubUrls() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> gitCloneService.cloneRepository("https://gitlab.com/octocat/Hello-World", "main"))
                .withMessage(GitHubUrlValidator.REJECTION_MESSAGE);
    }

    @Test
    void clonesRequestedBranchAndRemovesTheWorkingDirectoryOnClose() {
        assumeTrue(gitHubIsReachable(), "github.com is not reachable");

        CloneResult clone;
        try (CloneResult result = gitCloneService.cloneRepository(SAMPLE_REPOSITORY, SAMPLE_BRANCH)) {
            clone = result;
            assertThat(result.workingDirectory()).isDirectory();
            assertThat(result.workingDirectory().resolve(".git")).isDirectory();
            assertThat(result.pomXml()).doesNotExist();
            assertThat(result.commitSha()).matches("[0-9a-f]{40}");
            assertThat(result.branch()).isEqualTo(SAMPLE_BRANCH);
        }

        assertThat(clone.workingDirectory()).doesNotExist();
        System.out.printf(
                "repository: %s%ncommitSha: %s%nbranch: %s%n", SAMPLE_REPOSITORY, clone.commitSha(), clone.branch());
    }

    @Test
    void clonesDefaultBranchWhenNoBranchIsRequested() {
        assumeTrue(gitHubIsReachable(), "github.com is not reachable");

        try (CloneResult clone = gitCloneService.cloneRepository(SAMPLE_REPOSITORY, null)) {
            assertThat(clone.branch()).isIn("master", "main");
            assertThat(clone.commitSha()).matches("[0-9a-f]{40}");
        }
    }

    private static boolean gitHubIsReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("github.com", 443), CONNECT_TIMEOUT_MILLIS);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
