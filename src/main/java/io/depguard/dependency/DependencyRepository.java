package io.depguard.dependency;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DependencyRepository extends JpaRepository<Dependency, DependencyId> {

    @Query("""
            select d
            from Dependency d
            where d.groupId = :groupId
              and d.artifactId = :artifactId
              and d.version = :version
              and d.ecosystem = :ecosystem
            """)
    Optional<Dependency> findByKey(
            @Param("groupId") String groupId,
            @Param("artifactId") String artifactId,
            @Param("version") String version,
            @Param("ecosystem") Ecosystem ecosystem);
}
