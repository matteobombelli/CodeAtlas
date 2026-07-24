package dev.codeatlas.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codeatlas.indexing.DiscoveredSourceFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RelationshipAnalyzerTest {

    @TempDir
    Path root;

    @Test
    void resolvesDirectCallsInjectionAndSpringDataEntityAccess() throws Exception {
        String relative = "src/main/java/demo/ProjectFlow.java";
        Path source = root.resolve(relative);
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package demo;

                @RestController
                class ProjectController {
                    private final ProjectService service;
                    ProjectController(ProjectService service) { this.service = service; }
                    @PostMapping("/projects")
                    ProjectEntity create() { return service.create(); }
                }

                @Entity class ProjectEntity {}

                interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {}

                @Service
                class ProjectService {
                    private final ProjectRepository repository;
                    ProjectService(ProjectRepository repository) { this.repository = repository; }
                    ProjectEntity create() { return repository.save(new ProjectEntity()); }
                }
                """);
        DiscoveredSourceFile file = new DiscoveredSourceFile(
                UUID.randomUUID(), relative, "MAIN", ".", "0".repeat(64), 20, Files.size(source));
        RepositoryAnalyzer analyzer =
                new RepositoryAnalyzer(new JavaSourceAnalyzer(), new RelationshipAnalyzer());

        RepositoryAnalysis analysis = analyzer.analyze(root, List.of(file));

        assertThat(analysis.relationships())
                .extracting(AnalyzedRelationship::kind)
                .contains(
                        RelationshipKind.INJECTS,
                        RelationshipKind.CALLS,
                        RelationshipKind.MANAGES_ENTITY,
                        RelationshipKind.WRITES_ENTITY);
        assertThat(analysis.relationships())
                .filteredOn(edge -> edge.kind() == RelationshipKind.CALLS)
                .allMatch(edge -> edge.confidence() >= 0.9);
    }
}
