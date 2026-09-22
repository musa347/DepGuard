package io.depguard.eol;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EolRepository extends JpaRepository<EolRecord, EolRecordId> {

    /** All EOL records for a given scan. */
    @Query("select e from EolRecord e where e.id.scanId = :scanId")
    List<EolRecord> findByScanId(@Param("scanId") String scanId);

    /** Existing EOL record for a specific scan+dependency pair, or null. */
    @Query("select e from EolRecord e where e.id.scanId = :scanId and e.id.dependencyId = :dependencyId")
    EolRecord findByScanAndDependencyId(@Param("scanId") String scanId, @Param("dependencyId") String dependencyId);

    /**
     * Full EOL report for a scan: joins through {@link io.depguard.dependency.ScanDependency} to
     * {@link io.depguard.dependency.Dependency} to expose Maven coordinates.
     */
    @Query("""
            select new io.depguard.eol.EolDependencyEntry(
                d.groupId, d.artifactId, d.version,
                e.status, e.eolDate, e.source, e.dataSourceFetchedAt)
            from EolRecord e
            join io.depguard.dependency.ScanDependency sd
                on sd.id.dependencyId = e.id.dependencyId and sd.id.scanId = e.id.scanId
            join io.depguard.dependency.Dependency d
                on d.id.id = sd.id.dependencyId
            where e.id.scanId = :scanId
            order by d.groupId, d.artifactId
            """)
    List<EolDependencyEntry> findEolReportByScanId(@Param("scanId") String scanId);
}
