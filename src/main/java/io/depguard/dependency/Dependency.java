package io.depguard.dependency;

import io.depguard.shared.AssertUtil;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A Maven artifact that was resolved during a scan.
 *
 * <p>Dependencies are deduplicated across scans by {@code (groupId, artifactId, version, ecosystem)} —
 * a unique constraint backs the lookup. Unlike the other entities this one does not extend
 * {@code BaseEntity}: its optimistic-lock column is {@code version_col}, because the {@code version}
 * column of the {@code dependencies} table holds the Maven version string.
 */
@Entity
@Table(name = "dependencies")
@EntityListeners(AuditingEntityListener.class)
public class Dependency {

    @EmbeddedId
    @AttributeOverride(name = "id", column = @Column(name = "id", nullable = false, length = 26))
    private DependencyId id;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(name = "artifact_id", nullable = false)
    private String artifactId;

    @Column(name = "version", nullable = false, length = 100)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(name = "ecosystem", nullable = false, length = 50)
    private Ecosystem ecosystem;

    @Version
    @Column(name = "version_col", nullable = false)
    private int optimisticLockVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected Dependency() {}

    Dependency(DependencyId id, String groupId, String artifactId, String version, Ecosystem ecosystem) {
        this.id = AssertUtil.requireNotNull(id, "Dependency id must not be null");
        this.groupId = AssertUtil.requireNotBlank(groupId, "Dependency group id must not be blank");
        this.artifactId = AssertUtil.requireNotBlank(artifactId, "Dependency artifact id must not be blank");
        this.version = AssertUtil.requireNotBlank(version, "Dependency version must not be blank");
        this.ecosystem = AssertUtil.requireNotNull(ecosystem, "Dependency ecosystem must not be null");
    }

    public static Dependency maven(String groupId, String artifactId, String version) {
        return new Dependency(DependencyId.generate(), groupId, artifactId, version, Ecosystem.MAVEN);
    }

    public DependencyId getId() {
        return id;
    }

    String getGroupId() {
        return groupId;
    }

    String getArtifactId() {
        return artifactId;
    }

    String getVersion() {
        return version;
    }

    Ecosystem getEcosystem() {
        return ecosystem;
    }
}
