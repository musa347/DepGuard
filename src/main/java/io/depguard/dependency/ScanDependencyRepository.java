package io.depguard.dependency;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScanDependencyRepository extends JpaRepository<ScanDependency, ScanDependencyId> {

    @Query("""
            select new io.depguard.dependency.ScanDependencyCoords(
                d.id.id, sd.id.scanId, d.groupId, d.artifactId, d.version, sd.scope, sd.direct)
            from ScanDependency sd
            join Dependency d on d.id.id = sd.id.dependencyId
            where sd.id.scanId = :scanId
            """)
    List<ScanDependencyCoords> findScanDependenciesWithCoords(@Param("scanId") String scanId);

    @Query("""
            select new io.depguard.dependency.ScanDependencyView(
                d.groupId, d.artifactId, d.version, sd.scope, sd.direct)
            from ScanDependency sd
            join Dependency d on d.id.id = sd.id.dependencyId
            where sd.id.scanId = :scanId
            """)
    List<ScanDependencyView> findDependenciesOfScan(@Param("scanId") String scanId);

    boolean existsById_ScanId(String scanId);
}
