package dev.sbsa.demo.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sbsa.demo.AtlasTasksApplication;
import dev.sbsa.demo.comment.CommentService;
import dev.sbsa.demo.issue.AssignmentService;
import dev.sbsa.demo.issue.IssueService;
import dev.sbsa.demo.issue.IssueStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = AtlasTasksApplication.class)
class ProjectIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ProjectService projectService;

    @Autowired
    private IssueService issueService;

    @Autowired
    private AssignmentService assignmentService;

    @Autowired
    private CommentService commentService;

    @Test
    void createsAndListsProjects() {
        ProjectEntity created = projectService.create("Spring Boot Static Analysis");

        assertThat(created.getId()).isNotNull();
        assertThat(projectService.findAll())
                .extracting(ProjectEntity::getName)
                .contains("Spring Boot Static Analysis");
    }

    @Test
    void followsAnIssueFromCreationThroughAssignmentAndCommenting() {
        ProjectEntity project = projectService.create("Atlas Tasks");
        var issue = issueService.create(project.getId(), "Map the indexing endpoint");

        assignmentService.assign(issue.getId(), "Mina");
        issueService.changeStatus(issue.getId(), IssueStatus.IN_PROGRESS);
        commentService.add(issue.getId(), "Static analysis is underway");

        assertThat(issueService.findForProject(project.getId()))
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.getAssignee()).isEqualTo("Mina");
                    assertThat(saved.getStatus()).isEqualTo(IssueStatus.IN_PROGRESS);
                });
        assertThat(commentService.findForIssue(issue.getId()))
                .extracting(comment -> comment.getBody())
                .containsExactly("Static analysis is underway");
    }
}
