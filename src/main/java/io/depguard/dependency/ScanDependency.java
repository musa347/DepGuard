package io.depguard.dependency;

import io.depguard.shared.AssertUtil;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Links a scan to a dependency it resolved, with the resolved scope and whether the dependency is
 * declared by the project itself or was pulled in transitively.
 *
 * <p>The table carries no audit columns, so this entity does not extend {@code BaseEntity}.
 */
@Entity
@Table(name = "scan_dependencies")
public class ScanDependency {

    @EmbeddedId
    @AttributeOverrides({
        @AttributeOverride(name = "scanId", column = @Column(name = "scan_id", nullable = false, length = 26)),
        @AttributeOverride(
                name = "dependencyId",
                column = @Column(name = "dependency_id", nullable = false, length = 26))
    })
    private ScanDependencyId id;

    @Column(name = "scope", nullable = false, length = 20)
    private String scope;

    @Column(name = "direct", nullable = false)
    private boolean direct;

    /** Required by JPA. */
    protected ScanDependency() {}

    public ScanDependency(ScanDependencyId id, String scope, boolean direct) {
        this.id = AssertUtil.requireNotNull(id, "Scan dependency id must not be null");
        this.scope = AssertUtil.requireNotBlank(scope, "Dependency scope must not be blank");
        this.direct = direct;
    }

    public ScanDependencyId getId() {
        return id;
    }

    public String getScope() {
        return scope;
    }

    public boolean isDirect() {
        return direct;
    }
}
