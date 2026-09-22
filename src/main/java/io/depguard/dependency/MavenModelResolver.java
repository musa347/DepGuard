package io.depguard.dependency;

import java.util.List;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Resolves the parent POMs and imported BOMs of a scanned project through the Maven Resolver, so the
 * effective model of a project POM can be built without invoking the Maven binary.
 *
 * <p>Only the repositories handed to this resolver are consulted: repositories declared inside a scanned
 * POM are deliberately ignored, keeping resolution limited to Maven Central.
 */
class MavenModelResolver implements ModelResolver {

    private static final String POM_EXTENSION = "pom";

    private final RepositorySystem repositorySystem;

    private final RepositorySystemSession session;

    private final List<RemoteRepository> repositories;

    MavenModelResolver(
            RepositorySystem repositorySystem, RepositorySystemSession session, List<RemoteRepository> repositories) {
        this.repositorySystem = repositorySystem;
        this.session = session;
        this.repositories = repositories;
    }

    @Override
    public ModelSource resolveModel(String groupId, String artifactId, String version)
            throws UnresolvableModelException {
        return resolvePom(groupId, artifactId, version);
    }

    @Override
    public ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
        return resolvePom(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
    }

    @Override
    public ModelSource resolveModel(Dependency dependency) throws UnresolvableModelException {
        return resolvePom(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
    }

    @Override
    public void addRepository(Repository repository) throws InvalidRepositoryException {
        // Intentionally ignored: a scanned POM must not redirect resolution to other remote repositories.
    }

    @Override
    public void addRepository(Repository repository, boolean replace) throws InvalidRepositoryException {
        // Intentionally ignored, see addRepository(Repository).
    }

    @Override
    public ModelResolver newCopy() {
        return new MavenModelResolver(repositorySystem, session, repositories);
    }

    private ModelSource resolvePom(String groupId, String artifactId, String version)
            throws UnresolvableModelException {
        ArtifactRequest request = new ArtifactRequest(
                new DefaultArtifact(groupId, artifactId, "", POM_EXTENSION, version), repositories, "parent POM");
        try {
            ArtifactResult result = repositorySystem.resolveArtifact(session, request);
            return new FileModelSource(result.getArtifact().getFile());
        } catch (ArtifactResolutionException | RuntimeException ex) {
            throw new UnresolvableModelException(
                    "Failed to resolve %s:%s:%s: %s".formatted(groupId, artifactId, version, ex.getMessage()),
                    groupId,
                    artifactId,
                    version,
                    ex);
        }
    }
}
