package io.depguard.risk;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskRepository extends JpaRepository<RiskAssessment, RiskAssessmentId> {

    List<RiskAssessment> findByIdScanId(String scanId);
}
