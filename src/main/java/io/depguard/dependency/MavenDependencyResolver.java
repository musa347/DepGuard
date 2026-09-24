package io.depguard.dependency;

import io.depguard.shared.AssertUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.internal.impl.DefaultLocalPathComposer;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the complete Maven dependency tree of a project from its {@code pom.xml}.
 *
 * <p>Resolution is metadata-only: the Maven Resolver reads POM descriptors, parent POMs and imported BOMs.
 * No Maven build is executed, no plugin is run and no project code is loaded — artifacts are read from
 * Maven Central, using the local repository as a cache.
 */
@Component
public class MavenDependencyResolver {

    private static final String POM_FILE_NAME = "pom.xml";

    private static final String MAVEN_CENTRAL_ID = "central";

    private static final String MAVEN_CENTRAL_URL = "https://repo.maven.apache.org/maven2/";

    private static final String DEFAULT_ARTIFACT_TYPE = "jar";

    private static final String DEFAULT_SCOPE = "compile";

    private final RepositorySystem repositorySystem;

    private final RepositorySystemSession session;

    private final List<RemoteRepository> repositories;

    private final ModelBuilder modelBuilder;

    MavenDependencyResolver(@Value("${depguard.maven.local-repository:}") String localRepositoryPath) {
        this.repositorySystem = newRepositorySystem();
        this.repositories =
                List.of(new RemoteRepository.Builder(MAVEN_CENTRAL_ID, "default", MAVEN_CENTRAL_URL).build());
        this.session = newSession(localRepository(localRepositoryPath));
        this.modelBuilder = new DefaultModelBuilderFactory().newInstance();
    }

    /**
     * Resolves every dependency of the project in {@code projectDirectory} — direct and transitive.
     *
     * <p>For multi-module projects (root POM with {@code <packaging>pom</packaging>} and
     * {@code <modules>}), dependencies are collected from every declared submodule and merged.
     * A dependency that is direct in any module is marked as direct in the merged result.
     *
     * @param projectDirectory directory containing the project {@code pom.xml}
     * @return the resolved dependencies, the project's own declarations first
     * @throws IllegalArgumentException if the directory holds no {@code pom.xml}
     * @throws IllegalStateException    if the POM cannot be read or the tree cannot be resolved
     */
    public List<ResolvedDependency> resolveDependencies(Path projectDirectory) {
        return resolveGraph(projectDirectory).dependencies();
    }

    /**
     * Resolves the dependency tree including its parent → child edges.
     *
     * <p>For multi-module projects the graphs of all declared submodules are merged. If a module
     * directory does not contain a {@code pom.xml} it is silently skipped (the module may not be a
     * Java project).
     *
     * @throws IllegalArgumentException if the directory holds no {@code pom.xml}
     * @throws IllegalStateException    if the POM cannot be read or the tree cannot be resolved
     */
    DependencyGraph resolveGraph(Path projectDirectory) {
        Model project = buildEffectiveModel(locatePom(projectDirectory));
        List<String> modules = project.getModules();
        if (modules != null && !modules.isEmpty()) {
            return resolveMultiModule(projectDirectory, project, modules);
        }
        DependencyNode root = collectDependencies(project).getRoot();
        return toGraph(project, root);
    }

    /**
     * Merges dependency graphs from all submodules of an aggregator POM into a single graph.
     * Submodule directories that have no {@code pom.xml} are skipped quietly.
     */
    private DependencyGraph resolveMultiModule(Path rootDirectory, Model rootModel, List<String> modules) {
        String rootKey = projectKey(rootModel);
        Map<String, Set<String>> mergedChildren = new LinkedHashMap<>();
        Map<String, ResolvedDependency> mergedResolved = new LinkedHashMap<>();
        mergedChildren.put(rootKey, new LinkedHashSet<>());

        // Include any dependencies declared on the root POM itself (rare but valid).
        if (!rootModel.getDependencies().isEmpty()) {
            DependencyNode rootNode = collectDependencies(rootModel).getRoot();
            DependencyGraph rootGraph = toGraph(rootModel, rootNode);
            mergeGraph(rootGraph, rootKey, mergedChildren, mergedResolved);
        }

        for (String module : modules) {
            Path modulePom = rootDirectory.resolve(module).resolve(POM_FILE_NAME);
            if (!Files.isRegularFile(modulePom)) {
                continue; // not a Maven module — skip
            }
            try {
                Model moduleModel = buildEffectiveModel(modulePom);
                if (moduleModel.getDependencies().isEmpty()) {
                    continue;
                }
                DependencyNode moduleRoot = collectDependencies(moduleModel).getRoot();
                DependencyGraph moduleGraph = toGraph(moduleModel, moduleRoot);
                mergeGraph(moduleGraph, rootKey, mergedChildren, mergedResolved);
            } catch (Exception ex) {
                // Log and continue — a broken submodule should not abort the whole scan.
                org.slf4j.LoggerFactory.getLogger(MavenDependencyResolver.class)
                        .warn("Skipping submodule '{}': {}", module, ex.getMessage());
            }
        }

        List<ResolvedDependency> dependencies = new ArrayList<>();
        mergedChildren.get(rootKey).stream().map(mergedResolved::get).forEach(dependencies::add);
        mergedResolved.values().stream()
                .filter(dep -> !dep.direct())
                .filter(dep -> !dependencies.contains(dep))
                .forEach(dependencies::add);
        return new DependencyGraph(rootKey, mergedChildren, dependencies);
    }

    /**
     * Merges a submodule graph into the accumulator maps.
     * Direct dependencies of a submodule become direct children of the aggregator root.
     * If a dependency is already present as transitive, it is promoted to direct.
     */
    private static void mergeGraph(
            DependencyGraph source,
            String aggregatorKey,
            Map<String, Set<String>> targetChildren,
            Map<String, ResolvedDependency> targetResolved) {
        for (ResolvedDependency dep : source.dependencies()) {
            String key = "%s:%s:%s".formatted(dep.groupId(), dep.artifactId(), dep.version());
            if (dep.direct()) {
                targetChildren
                        .computeIfAbsent(aggregatorKey, k -> new LinkedHashSet<>())
                        .add(key);
            }
            // Promote transitive → direct if seen as direct in any module; never demote.
            targetResolved.merge(key, dep, (existing, incoming) -> incoming.direct() ? incoming : existing);
        }
        // Also carry over all child edges from the submodule graph (transitive edges).
        for (Map.Entry<String, Set<String>> entry : source.childrenByParent().entrySet()) {
            String parentKey = entry.getKey();
            if (parentKey.equals(source.rootKey())) {
                continue; // submodule root edges are re-rooted to aggregatorKey above
            }
            targetChildren
                    .computeIfAbsent(parentKey, k -> new LinkedHashSet<>())
                    .addAll(entry.getValue());
        }
    }

    private CollectResult collectDependencies(Model project) {
        CollectRequest request = new CollectRequest();
        request.setRepositories(repositories);
        request.setDependencies(
                project.getDependencies().stream().map(this::toAetherDependency).toList());
        try {
            return repositorySystem.collectDependencies(session, request);
        } catch (DependencyCollectionException ex) {
            throw new IllegalStateException(
                    "Failed to resolve the dependency tree of %s: %s".formatted(projectKey(project), ex.getMessage()),
                    ex);
        }
    }

    private Dependency toAetherDependency(org.apache.maven.model.Dependency dependency) {
        ArtifactType type = session.getArtifactTypeRegistry()
                .get(dependency.getType() == null ? DEFAULT_ARTIFACT_TYPE : dependency.getType());
        String classifier =
                dependency.getClassifier() == null && type != null ? type.getClassifier() : dependency.getClassifier();
        String extension = type == null ? DEFAULT_ARTIFACT_TYPE : type.getExtension();
        Artifact artifact = new DefaultArtifact(
                dependency.getGroupId(), dependency.getArtifactId(), classifier, extension, dependency.getVersion());
        List<Exclusion> exclusions = dependency.getExclusions().stream()
                .map(exclusion -> new Exclusion(exclusion.getGroupId(), exclusion.getArtifactId(), "*", "*"))
                .toList();
        return new Dependency(
                artifact,
                dependency.getScope() == null ? DEFAULT_SCOPE : dependency.getScope(),
                dependency.isOptional(),
                exclusions);
    }

    /**
     * Flattens the resolver's node tree into this module's own model, marking the root's children as direct.
     */
    private static DependencyGraph toGraph(Model project, DependencyNode root) {
        String rootKey = projectKey(project);
        Map<String, Set<String>> childrenByParent = new LinkedHashMap<>();
        Map<String, ResolvedDependency> resolved = new LinkedHashMap<>();
        childrenByParent.put(rootKey, new LinkedHashSet<>());
        for (DependencyNode directDependency : root.getChildren()) {
            traverse(rootKey, directDependency, childrenByParent, resolved, true, new LinkedHashSet<>());
        }

        List<ResolvedDependency> dependencies = new ArrayList<>();
        childrenByParent.get(rootKey).stream().map(resolved::get).forEach(dependencies::add);
        resolved.values().stream().filter(dependency -> !dependency.direct()).forEach(dependencies::add);
        return new DependencyGraph(rootKey, childrenByParent, dependencies);
    }

    private static void traverse(
            String parentKey,
            DependencyNode node,
            Map<String, Set<String>> childrenByParent,
            Map<String, ResolvedDependency> resolved,
            boolean direct,
            Set<String> path) {
        Artifact artifact = node.getArtifact();
        if (artifact == null || artifact.getVersion() == null) {
            return;
        }
        String key = nodeKey(artifact);
        childrenByParent
                .computeIfAbsent(parentKey, ignored -> new LinkedHashSet<>())
                .add(key);
        childrenByParent.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
        resolved.computeIfAbsent(key, ignored -> toResolvedDependency(node, direct));
        if (!path.add(key)) {
            return; // dependency cycle: keep the edge, do not traverse the children again
        }
        for (DependencyNode child : node.getChildren()) {
            traverse(key, child, childrenByParent, resolved, false, path);
        }
        path.remove(key);
    }

    private static ResolvedDependency toResolvedDependency(DependencyNode node, boolean direct) {
        Artifact artifact = node.getArtifact();
        Dependency dependency = node.getDependency();
        String scope = dependency == null || dependency.getScope() == null ? DEFAULT_SCOPE : dependency.getScope();
        return new ResolvedDependency(
                artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), scope, direct);
    }

    private static String nodeKey(Artifact artifact) {
        return "%s:%s:%s".formatted(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }

    private static String projectKey(Model project) {
        return "%s:%s:%s".formatted(project.getGroupId(), project.getArtifactId(), project.getVersion());
    }

    private Model buildEffectiveModel(Path pomFile) {
        ModelBuildingRequest request = new DefaultModelBuildingRequest()
                .setPomFile(pomFile.toFile())
                .setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL)
                .setProcessPlugins(false)
                .setSystemProperties(System.getProperties())
                .setModelResolver(new MavenModelResolver(repositorySystem, session, repositories));
        try {
            return modelBuilder.build(request).getEffectiveModel();
        } catch (ModelBuildingException ex) {
            throw new IllegalStateException("Failed to read %s: %s".formatted(pomFile, ex.getMessage()), ex);
        }
    }

    private static Path locatePom(Path projectDirectory) {
        Path directory = AssertUtil.requireNotNull(projectDirectory, "Project directory must not be null");
        Path pomFile = directory.resolve(POM_FILE_NAME);
        if (!Files.isRegularFile(pomFile)) {
            throw new IllegalArgumentException("No " + POM_FILE_NAME + " found in " + directory);
        }
        return pomFile;
    }

    private static DefaultRepositorySystemSession newSession(Path localRepository) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setSystemProperties(System.getProperties());
        try {
            LocalRepositoryManager localRepositoryManager = new SimpleLocalRepositoryManagerFactory(
                            new DefaultLocalPathComposer())
                    .newInstance(session, new LocalRepository(localRepository.toFile()));
            session.setLocalRepositoryManager(localRepositoryManager);
        } catch (NoLocalRepositoryManagerException ex) {
            throw new IllegalStateException("Failed to use local repository " + localRepository, ex);
        }
        return session;
    }

    private static RepositorySystem newRepositorySystem() {
        // RepositorySystemSupplier is the supported way to assemble a RepositorySystem in resolver 1.9.x;
        // it wires the basic connector and the HTTP transport used to reach Maven Central.
        return new RepositorySystemSupplier().get();
    }

    private static Path localRepository(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return Path.of(System.getProperty("user.home"), ".m2", "repository");
        }
        return Path.of(configuredPath);
    }
}
