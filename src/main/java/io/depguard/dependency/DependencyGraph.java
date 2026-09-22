package io.depguard.dependency;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The resolved dependency tree of a single project.
 *
 * <p>{@code childrenByParent} holds the parent → child edges keyed by {@link ResolvedDependency#key()}, with
 * {@code rootKey} being the coordinates of the scanned project itself. The edges are what makes a dependency
 * "direct" (a child of the root) or "transitive", and they keep the graph useful for later position-aware
 * risk scoring.
 *
 * <p>Pure model: it carries no persistence or HTTP concern.
 */
record DependencyGraph(
        String rootKey, Map<String, Set<String>> childrenByParent, List<ResolvedDependency> dependencies) {

    DependencyGraph {
        childrenByParent = Map.copyOf(childrenByParent);
        dependencies = List.copyOf(dependencies);
    }

    /** Keys of the dependencies that the scanned project declares itself. */
    Set<String> directKeys() {
        return childrenByParent.getOrDefault(rootKey, Set.of());
    }
}
