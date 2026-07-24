package dev.codeatlas.demo.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codeatlas.demo.AtlasTasksApplication;
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

    @Test
    void createsAndListsProjects() {
        ProjectEntity created = projectService.create("Code Atlas");

        assertThat(created.getId()).isNotNull();
        assertThat(projectService.findAll())
                .extracting(ProjectEntity::getName)
                .contains("Code Atlas");
    }
}
