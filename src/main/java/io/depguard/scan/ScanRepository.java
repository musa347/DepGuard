package io.depguard.scan;

import io.depguard.project.ProjectId;
import io.depguard.shared.ScanId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScanRepository extends JpaRepository<Scan, ScanId> {

    @Query("SELECT s FROM Scan s WHERE s.projectId = :projectId ORDER BY s.createdAt DESC LIMIT 1")
    Optional<Scan> findLatestByProjectId(ProjectId projectId);
}
