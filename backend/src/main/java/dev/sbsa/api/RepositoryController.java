package dev.sbsa.api;

import dev.sbsa.indexing.IndexMode;
import dev.sbsa.indexing.IndexRun;
import dev.sbsa.indexing.IndexingService;
import dev.sbsa.repository.RegisteredRepository;
import dev.sbsa.repository.RepositoryService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    private final RepositoryService repositoryService;
    private final IndexingService indexingService;

    public RepositoryController(
            RepositoryService repositoryService,
            IndexingService indexingService) {
        this.repositoryService = repositoryService;
        this.indexingService = indexingService;
    }

    @PostMapping
    ResponseEntity<RegisteredRepository> create(@RequestBody CreateRepositoryRequest request) {
        RegisteredRepository created =
                repositoryService.register(request.displayName(), request.relativePath());
        return ResponseEntity.created(URI.create("/api/repositories/" + created.id())).body(created);
    }

    @GetMapping
    List<RegisteredRepository> list() {
        return repositoryService.list();
    }

    @GetMapping("/{repositoryId}")
    RegisteredRepository get(@PathVariable UUID repositoryId) {
        return repositoryService.get(repositoryId);
    }

    @DeleteMapping("/{repositoryId}")
    ResponseEntity<Void> delete(@PathVariable UUID repositoryId) {
        repositoryService.delete(repositoryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{repositoryId}/index")
    ResponseEntity<IndexRun> index(
            @PathVariable UUID repositoryId,
            @RequestParam(defaultValue = "FULL") IndexMode mode) {
        IndexRun run = indexingService.start(repositoryId, mode);
        return ResponseEntity.accepted().body(run);
    }

    @GetMapping("/{repositoryId}/index-runs")
    List<IndexRun> indexRuns(@PathVariable UUID repositoryId) {
        repositoryService.get(repositoryId);
        return indexingService.list(repositoryId);
    }

    public record CreateRepositoryRequest(String displayName, String relativePath) {
    }
}
