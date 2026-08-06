package dev.springbootstaticanalysis.demo.project;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectEntity> list() {
        return projectService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectEntity create(@RequestBody CreateProjectRequest request) {
        return projectService.create(request.name());
    }

    public record CreateProjectRequest(String name) {
    }
}
