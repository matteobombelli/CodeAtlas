package dev.codeatlas.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codeatlas.indexing.DiscoveredSourceFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaSourceAnalyzerTest {

    @TempDir
    Path root;

    @Test
    void extractsLanguageSymbolsRolesAndCombinedEndpointPaths() throws Exception {
        String relative = "src/main/java/demo/ProjectController.java";
        Path source = root.resolve(relative);
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package demo;

                @RestController
                @RequestMapping({"/api/projects", "/projects"})
                class ProjectController {
                    private final ProjectService service;

                    ProjectController(ProjectService service) {
                        this.service = service;
                    }

                    @PostMapping(path = {"", "/create"})
                    Project create(CreateRequest request) {
                        return service.create(request);
                    }

                    record CreateRequest(String name) {}
                }
                """);
        DiscoveredSourceFile file = new DiscoveredSourceFile(
                UUID.randomUUID(), relative, "MAIN", ".", "0".repeat(64), 20, Files.size(source));

        RepositoryAnalysis analysis = new JavaSourceAnalyzer().analyze(root, List.of(file));

        assertThat(analysis.warnings()).isEmpty();
        assertThat(analysis.symbols())
                .extracting(AnalyzedSymbol::kind)
                .contains(SymbolKind.CLASS, SymbolKind.FIELD, SymbolKind.CONSTRUCTOR,
                        SymbolKind.METHOD, SymbolKind.RECORD);
        assertThat(analysis.symbols())
                .filteredOn(symbol -> symbol.roles().contains(SymbolRole.CONTROLLER))
                .extracting(AnalyzedSymbol::qualifiedName)
                .containsExactly("demo.ProjectController");
        assertThat(analysis.endpoints())
                .extracting(endpoint -> endpoint.httpMethod() + " " + endpoint.path())
                .containsExactlyInAnyOrder(
                        "POST /api/projects",
                        "POST /api/projects/create",
                        "POST /projects",
                        "POST /projects/create");
    }

    @Test
    void recordsParseFailureInsteadOfFabricatingSymbols() throws Exception {
        String relative = "src/main/java/demo/Broken.java";
        Path source = root.resolve(relative);
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package demo; class Broken {");
        DiscoveredSourceFile file = new DiscoveredSourceFile(
                UUID.randomUUID(), relative, "MAIN", ".", "0".repeat(64), 1, Files.size(source));

        RepositoryAnalysis analysis = new JavaSourceAnalyzer().analyze(root, List.of(file));

        assertThat(analysis.symbols()).isEmpty();
        assertThat(analysis.warnings()).isNotEmpty();
    }
}
