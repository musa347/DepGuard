package io.depguard.project;

import io.depguard.shared.AssertUtil;
import io.depguard.shared.BaseEntity;
import io.depguard.shared.GitHubUrlValidator;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * A public GitHub repository registered with DepGuard so that it can be scanned.
 *
 * <p>The invariant of the {@code repositoryUrl} field is a canonical public GitHub URL; it is enforced
 * here (not only at the API boundary) so that any caller of {@link #register} is protected.
 */
@Entity
@Table(name = "projects")
class Project extends BaseEntity {

    static final String DEFAULT_BRANCH = "main";

    @EmbeddedId
    @AttributeOverride(name = "id", column = @Column(name = "id", nullable = false, length = 26))
    private ProjectId id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "repository_url", nullable = false, length = 512)
    private String repositoryUrl;

    @Column(name = "default_branch", nullable = false)
    private String defaultBranch;

    /** Required by JPA. */
    protected Project() {}

    Project(ProjectId id, String name, String repositoryUrl, String defaultBranch) {
        this.id = AssertUtil.requireNotNull(id, "Project id must not be null");
        this.name = AssertUtil.requireNotBlank(name, "Project name must not be blank")
                .trim();
        this.repositoryUrl = GitHubUrlValidator.validateAndNormalize(repositoryUrl);
        this.defaultBranch = defaultBranch == null || defaultBranch.isBlank() ? DEFAULT_BRANCH : defaultBranch.trim();
    }

    /**
     * Registers a new project, generating its identifier.
     *
     * @throws IllegalArgumentException if the name is blank or the repository URL is not a public GitHub URL
     */
    static Project register(String name, String repositoryUrl, String defaultBranch) {
        return new Project(ProjectId.generate(), name, repositoryUrl, defaultBranch);
    }

    ProjectId getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getRepositoryUrl() {
        return repositoryUrl;
    }

    String getDefaultBranch() {
        return defaultBranch;
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        return candidate instanceof Project other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
