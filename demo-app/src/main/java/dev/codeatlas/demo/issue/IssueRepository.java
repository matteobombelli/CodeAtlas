package dev.codeatlas.demo.issue;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueRepository extends JpaRepository<IssueEntity, Long> {

    List<IssueEntity> findByProjectId(Long projectId);

    List<IssueEntity> findByTitleContainingIgnoreCase(String query);
}
