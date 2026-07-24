package dev.codeatlas.api;

import dev.codeatlas.analysis.SymbolRelationshipView;
import dev.codeatlas.analysis.SymbolStore;
import dev.codeatlas.analysis.SymbolView;
import dev.codeatlas.repository.RepositoryService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/symbols")
public class SymbolController {

    private final RepositoryService repositories;
    private final SymbolStore symbols;

    public SymbolController(RepositoryService repositories, SymbolStore symbols) {
        this.repositories = repositories;
        this.symbols = symbols;
    }

    @GetMapping("/{symbolId}")
    SymbolView get(@PathVariable UUID repositoryId, @PathVariable UUID symbolId) {
        repositories.get(repositoryId);
        return symbols.get(repositoryId, symbolId);
    }

    @GetMapping("/search")
    List<SymbolView> search(
            @PathVariable UUID repositoryId,
            @RequestParam(name = "q") String query) {
        repositories.get(repositoryId);
        return symbols.search(repositoryId, query);
    }

    @GetMapping("/{symbolId}/callers")
    List<SymbolRelationshipView> callers(
            @PathVariable UUID repositoryId, @PathVariable UUID symbolId) {
        return symbols.callers(repositoryId, symbolId, false);
    }

    @GetMapping("/{symbolId}/callees")
    List<SymbolRelationshipView> callees(
            @PathVariable UUID repositoryId, @PathVariable UUID symbolId) {
        return symbols.callees(repositoryId, symbolId);
    }

    @GetMapping("/{symbolId}/tests")
    List<SymbolRelationshipView> tests(
            @PathVariable UUID repositoryId, @PathVariable UUID symbolId) {
        return symbols.callers(repositoryId, symbolId, true);
    }
}
