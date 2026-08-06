package dev.sbsa.demo.project;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public ProjectEntity create(String name) {
        return projectRepository.save(new ProjectEntity(name));
    }

    public List<ProjectEntity> findAll() {
        return projectRepository.findAll();
    }
}
