package io.depguard.dependency;

import io.depguard.shared.AssertUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A read-only clone of a public GitHub repository in a temporary working directory, together with the
 * commit and branch it was taken from (scan reproducibility).
 *
 * <p>The working directory is a short-lived resource: {@link #close()} removes it, so use the clone in a
 * try-with-resources block.
 *
 * <pre>{@code
 * try (CloneResult clone = gitCloneService.cloneRepository(url, branch)) {
 *     resolver.resolveDependencies(clone.workingDirectory());
 * }
 * }</pre>
 *
 * @param workingDirectory the temporary directory holding the checked-out project
 * @param commitSha        the full SHA of the cloned commit
 * @param branch           the cloned branch
 */
public record CloneResult(Path workingDirectory, String commitSha, String branch) implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CloneResult.class);

    public CloneResult {
        AssertUtil.requireNotNull(workingDirectory, "Working directory must not be null");
        AssertUtil.requireNotBlank(commitSha, "Commit SHA must not be blank");
        AssertUtil.requireNotBlank(branch, "Branch must not be blank");
    }

    /** The project descriptor inside the clone. */
    public Path pomXml() {
        return workingDirectory.resolve("pom.xml");
    }

    @Override
    public void close() {
        deleteQuietly(workingDirectory);
    }

    /**
     * Recursively deletes a working directory, logging instead of propagating failures so that cleanup
     * never masks the original error.
     */
    static void deleteQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(directory)) {
            entries.sorted(Comparator.reverseOrder()).forEach(CloneResult::deleteIfExists);
        } catch (IOException ex) {
            LOG.warn("Failed to delete working directory {}: {}", directory, ex.getMessage());
        }
    }

    private static void deleteIfExists(Path entry) {
        try {
            Files.deleteIfExists(entry);
        } catch (IOException ex) {
            LOG.warn("Failed to delete {}: {}", entry, ex.getMessage());
        }
    }
}
