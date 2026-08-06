package dev.springbootstaticanalysis.demo.issue;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentService {

    private final IssueRepository issueRepository;
    private final NotificationSender notificationSender;

    public AssignmentService(
            IssueRepository issueRepository,
            NotificationSender notificationSender) {
        this.issueRepository = issueRepository;
        this.notificationSender = notificationSender;
    }

    @Transactional
    public IssueEntity assign(Long issueId, String assignee) {
        IssueEntity issue = issueRepository.findById(issueId).orElseThrow();
        issue.assignTo(assignee);
        notificationSender.issueAssigned(issue);
        return issue;
    }
}
