package dev.springbootstaticanalysis.demo.project;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {
}
