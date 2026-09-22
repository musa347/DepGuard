package io.depguard.project;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProjectRepository extends JpaRepository<Project, ProjectId> {

    Optional<Project> findByRepositoryUrl(String repositoryUrl);

    boolean existsByRepositoryUrl(String repositoryUrl);
}
