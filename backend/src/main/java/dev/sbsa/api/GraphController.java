package dev.sbsa.api;

import dev.sbsa.graph.ExecutionGraph;
import dev.sbsa.graph.GraphStore;
import dev.sbsa.repository.RepositoryService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/graphs")
public class GraphController {

    private final RepositoryService repositoryService;
    private final GraphStore graphStore;

    public GraphController(RepositoryService repositoryService, GraphStore graphStore) {
        this.repositoryService = repositoryService;
        this.graphStore = graphStore;
    }

    @GetMapping("/endpoint/{endpointId}")
    ExecutionGraph endpoint(
            @PathVariable UUID repositoryId,
            @PathVariable UUID endpointId,
            @RequestParam(defaultValue = "4") int maxDepth,
            @RequestParam(defaultValue = "true") boolean includeUncertain,
            @RequestParam(defaultValue = "false") boolean includeExternal) {
        repositoryService.get(repositoryId);
        return graphStore.endpointGraph(
                repositoryId, endpointId, maxDepth, includeUncertain, includeExternal);
    }

    @GetMapping("/blast-radius/{symbolId}")
    ExecutionGraph blastRadius(
            @PathVariable UUID repositoryId,
            @PathVariable UUID symbolId,
            @RequestParam(defaultValue = "4") int maxDepth,
            @RequestParam(defaultValue = "true") boolean includeUncertain) {
        repositoryService.get(repositoryId);
        return graphStore.blastRadius(repositoryId, symbolId, maxDepth, includeUncertain);
    }
}
