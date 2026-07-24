package dev.codeatlas.api;

import dev.codeatlas.graph.ExecutionGraph;
import dev.codeatlas.graph.GraphStore;
import dev.codeatlas.repository.RepositoryService;
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
}
