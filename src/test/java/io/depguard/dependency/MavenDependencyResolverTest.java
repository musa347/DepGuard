package io.depguard.dependency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Resolves the {@code test-pom.xml} fixture against Maven Central.
 *
 * <p>The local Maven repository is reused as a cache — exactly what the resolver does in production — so
 * only artifacts missing from it are downloaded. Resolution is metadata-only: POM descriptors are read,
 * no build is executed.
 */
class MavenDependencyResolverTest {

    private static final String FIXTURE_POM = "src/test/resources/test-pom.xml";

    private static final String PROJECT_ROOT_KEY = "com.example:payment-service:0.0.1-SNAPSHOT";

    private final MavenDependencyResolver resolver = new MavenDependencyResolver("");

    @TempDir
    Path projectDirectory;

    @Test
    void resolvesDeclaredDependenciesAsDirect() {
        List<ResolvedDependency> dependencies = resolver.resolveDependencies(projectWithFixturePom());

        assertThat(dependency(dependencies, "org.springframework.boot", "spring-boot-starter-web"))
                .isPresent()
                .get()
                .satisfies(dependency -> {
                    // the version is supplied by spring-boot-starter-parent's dependencyManagement
                    assertThat(dependency.version()).isEqualTo("3.3.0");
                    assertThat(dependency.scope()).isEqualTo("compile");
                    assertThat(dependency.direct()).isTrue();
                });
        assertThat(dependency(dependencies, "com.h2database", "h2"))
                .isPresent()
                .get()
                .satisfies(dependency -> {
                    assertThat(dependency.scope()).isEqualTo("runtime");
                    assertThat(dependency.direct()).isTrue();
                });
        assertThat(dependency(dependencies, "org.junit.jupiter", "junit-jupiter"))
                .isPresent()
                .get()
                .satisfies(dependency -> {
                    assertThat(dependency.scope()).isEqualTo("test");
                    assertThat(dependency.direct()).isTrue();
                });
    }

    @Test
    void resolvesTransitiveDependenciesAsNotDirect() {
        List<ResolvedDependency> dependencies = resolver.resolveDependencies(projectWithFixturePom());

        assertThat(dependency(dependencies, "org.springframework", "spring-core"))
                .isPresent()
                .get()
                .satisfies(dependency -> assertThat(dependency.direct()).isFalse());
        assertThat(dependency(dependencies, "org.springframework", "spring-web"))
                .isPresent()
                .get()
                .satisfies(dependency -> assertThat(dependency.direct()).isFalse());
        assertThat(dependency(dependencies, "org.apache.tomcat.embed", "tomcat-embed-core"))
                .isPresent()
                .get()
                .satisfies(dependency -> assertThat(dependency.direct()).isFalse());
    }

    @Test
    void resolvesEveryDependencyWithAVersionAndAScope() {
        List<ResolvedDependency> dependencies = resolver.resolveDependencies(projectWithFixturePom());

        assertThat(dependencies).hasSizeGreaterThan(5);
        assertThat(dependencies).allSatisfy(dependency -> {
            assertThat(dependency.groupId()).isNotBlank();
            assertThat(dependency.artifactId()).isNotBlank();
            assertThat(dependency.version()).isNotBlank();
            assertThat(dependency.scope()).isNotBlank();
        });
    }

    @Test
    void resolvesEachArtifactToASingleVersion() {
        List<ResolvedDependency> dependencies = resolver.resolveDependencies(projectWithFixturePom());

        assertThat(dependencies)
                .extracting(dependency -> dependency.groupId() + ":" + dependency.artifactId())
                .doesNotHaveDuplicates();
    }

    @Test
    void keepsParentChildRelationshipsAndListsDirectDependenciesFirst() {
        DependencyGraph graph = resolver.resolveGraph(projectWithFixturePom());

        assertThat(graph.rootKey()).isEqualTo(PROJECT_ROOT_KEY);
        assertThat(graph.directKeys()).hasSize(3);
        assertThat(graph.directKeys())
                .filteredOn(key -> key.startsWith("org.springframework.boot:spring-boot-starter-web:"))
                .hasSize(1);
        assertThat(graph.directKeys())
                .filteredOn(key -> key.startsWith("com.h2database:h2:"))
                .hasSize(1);
        assertThat(graph.directKeys())
                .filteredOn(key -> key.startsWith("org.junit.jupiter:junit-jupiter:"))
                .hasSize(1);
        assertThat(graph.dependencies().getFirst().direct()).isTrue();
        assertThat(graph.dependencies().stream()
                        .filter(ResolvedDependency::direct)
                        .count())
                .isEqualTo(graph.directKeys().size());

        // spring-web is reached through spring-boot-starter-web, not through the project itself
        String starterWeb = graph.directKeys().stream()
                .filter(key -> key.startsWith("org.springframework.boot:spring-boot-starter-web:"))
                .findFirst()
                .orElseThrow();
        assertThat(graph.childrenByParent().get(starterWeb))
                .filteredOn(key -> key.startsWith("org.springframework:spring-web:"))
                .hasSize(1);

        printTree(graph);
    }

    @Test
    void resolvesMultiModuleProjectByMergingAllSubmoduleDependencies() throws IOException {
        // Arrange: root aggregator POM with one submodule
        String rootPom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>multi-parent</artifactId>
                  <version>1.0</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>child</module>
                  </modules>
                </project>
                """;
        // Child declares a real, small dependency so the resolver can fetch its metadata.
        String childPom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>multi-parent</artifactId>
                    <version>1.0</version>
                  </parent>
                  <artifactId>child</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.logging.log4j</groupId>
                      <artifactId>log4j-core</artifactId>
                      <version>2.13.3</version>
                    </dependency>
                  </dependencies>
                </project>
                """;
        Files.writeString(projectDirectory.resolve("pom.xml"), rootPom);
        Path childDir = Files.createDirectories(projectDirectory.resolve("child"));
        Files.writeString(childDir.resolve("pom.xml"), childPom);

        // Act
        List<ResolvedDependency> dependencies = resolver.resolveDependencies(projectDirectory);

        // Assert: log4j-core (direct in child) surfaces as a direct dependency of the aggregate
        assertThat(dependency(dependencies, "org.apache.logging.log4j", "log4j-core"))
                .isPresent()
                .get()
                .satisfies(dep -> {
                    assertThat(dep.version()).isEqualTo("2.13.3");
                    assertThat(dep.direct()).isTrue();
                });
        // Transitives of log4j-core should also appear
        assertThat(dependencies.stream().anyMatch(dep -> !dep.direct())).isTrue();
    }

    @Test
    void failsWhenTheDirectoryHasNoPom() throws IOException {
        Path emptyDirectory = Files.createDirectories(projectDirectory.resolve("empty"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolver.resolveDependencies(emptyDirectory))
                .withMessage("No pom.xml found in " + emptyDirectory);
    }

    @Test
    void failsWhenThePomCannotBeRead() throws IOException {
        Files.writeString(projectDirectory.resolve("pom.xml"), "<project><broken");

        assertThatThrownBy(() -> resolver.resolveDependencies(projectDirectory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to read");
    }

    private Path projectWithFixturePom() {
        try {
            Files.copy(Path.of(FIXTURE_POM), projectDirectory.resolve("pom.xml"));
            return projectDirectory;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare the test project", ex);
        }
    }

    private static Optional<ResolvedDependency> dependency(
            List<ResolvedDependency> dependencies, String groupId, String artifactId) {
        return dependencies.stream()
                .filter(candidate -> candidate.groupId().equals(groupId)
                        && candidate.artifactId().equals(artifactId))
                .findFirst();
    }

    /** Demo output of Task 3: the resolved tree of the fixture project. */
    private static void printTree(DependencyGraph graph) {
        System.out.println("project: " + graph.rootKey());
        graph.dependencies()
                .forEach(dependency ->
                        System.out.printf("[%s] %s%n", dependency.direct() ? "DIRECT    " : "TRANSITIVE", dependency));
    }
}
