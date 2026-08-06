package dev.springbootstaticanalysis.demo.issue;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class IssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;
    private String title;
    private String assignee;

    @Enumerated(EnumType.STRING)
    private IssueStatus status;

    protected IssueEntity() {
    }

    public IssueEntity(Long projectId, String title) {
        this.projectId = projectId;
        this.title = title;
        this.status = IssueStatus.OPEN;
    }

    public void assignTo(String assignee) {
        this.assignee = assignee;
    }

    public void changeStatus(IssueStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getTitle() {
        return title;
    }

    public String getAssignee() {
        return assignee;
    }

    public IssueStatus getStatus() {
        return status;
    }
}
