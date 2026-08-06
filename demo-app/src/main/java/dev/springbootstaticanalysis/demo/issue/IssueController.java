package dev.springbootstaticanalysis.demo.issue;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/issues")
public class IssueController {

    private final IssueService issueService;
    private final AssignmentService assignmentService;

    public IssueController(IssueService issueService, AssignmentService assignmentService) {
        this.issueService = issueService;
        this.assignmentService = assignmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssueEntity create(@RequestBody CreateIssueRequest request) {
        return issueService.create(request.projectId(), request.title());
    }

    @GetMapping
    public List<IssueEntity> search(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false, defaultValue = "") String query) {
        return projectId == null
                ? issueService.search(query)
                : issueService.findForProject(projectId);
    }

    @PatchMapping("/{issueId}/status")
    public IssueEntity changeStatus(
            @PathVariable Long issueId,
            @RequestBody ChangeStatusRequest request) {
        return issueService.changeStatus(issueId, request.status());
    }

    @PatchMapping("/{issueId}/assignee")
    public IssueEntity assign(
            @PathVariable Long issueId,
            @RequestBody AssignIssueRequest request) {
        return assignmentService.assign(issueId, request.assignee());
    }

    public record CreateIssueRequest(Long projectId, String title) {
    }

    public record ChangeStatusRequest(IssueStatus status) {
    }

    public record AssignIssueRequest(String assignee) {
    }
}
