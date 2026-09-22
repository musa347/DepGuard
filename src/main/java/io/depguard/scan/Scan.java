package io.depguard.scan;

import io.depguard.project.ProjectId;
import io.depguard.shared.AssertUtil;
import io.depguard.shared.BaseEntity;
import io.depguard.shared.ScanId;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One scan of a project: its lifecycle plus the reproducibility metadata captured while running.
 *
 * <p>{@code commitSha} and {@code branch} are taken from the actual clone, so a report can always be
 * traced back to the exact commit that was analysed.
 */
@Entity
@Table(name = "scans")
class Scan extends BaseEntity {

    @EmbeddedId
    @AttributeOverride(name = "id", column = @Column(name = "id", nullable = false, length = 26))
    private ScanId id;

    @Embedded
    @AttributeOverride(name = "id", column = @Column(name = "project_id", nullable = false, length = 26))
    private ProjectId projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScanStatus status;

    @Column(name = "commit_sha", length = 40)
    private String commitSha;

    @Column(name = "branch")
    private String branch;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Required by JPA. */
    protected Scan() {}

    private Scan(
            ScanId id,
            ProjectId projectId,
            ScanStatus status,
            String commitSha,
            String branch,
            String errorMessage,
            Instant startedAt,
            Instant completedAt) {
        this.id = AssertUtil.requireNotNull(id, "Scan id must not be null");
        this.projectId = AssertUtil.requireNotNull(projectId, "Scan project id must not be null");
        this.status = AssertUtil.requireNotNull(status, "Scan status must not be null");
        this.commitSha = commitSha;
        this.branch = branch;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    /** Creates a new scan in {@link ScanStatus#PENDING}. */
    static Scan start(ProjectId projectId) {
        return new Scan(ScanId.generate(), projectId, ScanStatus.PENDING, null, null, null, null, null);
    }

    void markRunning() {
        this.status = ScanStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    /** Records the reproducibility metadata captured from the clone. */
    void cloned(String commitSha, String branch) {
        this.commitSha = AssertUtil.requireNotBlank(commitSha, "Scan commit SHA must not be blank");
        this.branch = AssertUtil.requireNotBlank(branch, "Scan branch must not be blank");
    }

    void markCompleted() {
        this.status = ScanStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    void markFailed(String errorMessage) {
        this.status = ScanStatus.FAILED;
        this.errorMessage = AssertUtil.requireNotBlank(errorMessage, "Scan error message must not be blank");
        this.completedAt = Instant.now();
    }

    ScanId getId() {
        return id;
    }

    ProjectId getProjectId() {
        return projectId;
    }

    ScanStatus getStatus() {
        return status;
    }

    String getCommitSha() {
        return commitSha;
    }

    String getBranch() {
        return branch;
    }

    String getErrorMessage() {
        return errorMessage;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    Instant getCompletedAt() {
        return completedAt;
    }
}
