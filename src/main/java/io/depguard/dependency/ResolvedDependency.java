package io.depguard.dependency;

import io.depguard.shared.AssertUtil;

/**
 * One dependency resolved from a scanned project.
 *
 * @param groupId    the dependency group id
 * @param artifactId the dependency artifact id
 * @param version    the resolved version
 * @param scope      the resolved Maven scope ({@code compile}, {@code runtime}, {@code provided}, {@code test})
 * @param direct     {@code true} when the project declares the dependency itself, {@code false} when it is transitive
 */
public record ResolvedDependency(String groupId, String artifactId, String version, String scope, boolean direct) {

    public ResolvedDependency {
        AssertUtil.requireNotBlank(groupId, "Dependency group id must not be blank");
        AssertUtil.requireNotBlank(artifactId, "Dependency artifact id must not be blank");
        AssertUtil.requireNotBlank(version, "Dependency version must not be blank");
        AssertUtil.requireNotBlank(scope, "Dependency scope must not be blank");
    }

    /** Stable key of this dependency, {@code groupId:artifactId:version}. */
    public String key() {
        return "%s:%s:%s".formatted(groupId, artifactId, version);
    }

    @Override
    public String toString() {
        return "%s [%s]%s".formatted(key(), scope, direct ? " [DIRECT]" : " [TRANSITIVE]");
    }
}
