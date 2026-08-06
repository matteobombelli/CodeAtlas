package dev.springbootstaticanalysis.demo.issue;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueService {

    private final IssueRepository issueRepository;

    public IssueService(IssueRepository issueRepository) {
        this.issueRepository = issueRepository;
    }

    public IssueEntity create(Long projectId, String title) {
        return issueRepository.save(new IssueEntity(projectId, title));
    }

    public List<IssueEntity> findForProject(Long projectId) {
        return issueRepository.findByProjectId(projectId);
    }

    public List<IssueEntity> search(String query) {
        return issueRepository.findByTitleContainingIgnoreCase(query);
    }

    @Transactional
    public IssueEntity changeStatus(Long issueId, IssueStatus status) {
        IssueEntity issue = issueRepository.findById(issueId).orElseThrow();
        issue.changeStatus(status);
        return issue;
    }
}
