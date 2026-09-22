package io.depguard.scan;

import io.depguard.shared.ScanId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanRepository extends JpaRepository<Scan, ScanId> {}
