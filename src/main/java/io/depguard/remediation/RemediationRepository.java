package io.depguard.remediation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RemediationRepository extends JpaRepository<RemediationRecommendation, String> {

    List<RemediationRecommendation> findByScanId(String scanId);

    boolean existsByScanIdAndDependencyId(String scanId, String dependencyId);
}
