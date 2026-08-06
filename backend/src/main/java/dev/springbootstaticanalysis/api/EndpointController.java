package dev.springbootstaticanalysis.api;

import dev.springbootstaticanalysis.analysis.EndpointStore;
import dev.springbootstaticanalysis.analysis.HttpEndpointView;
import dev.springbootstaticanalysis.repository.RepositoryService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/http-endpoints")
public class EndpointController {

    private final RepositoryService repositoryService;
    private final EndpointStore endpointStore;

    public EndpointController(RepositoryService repositoryService, EndpointStore endpointStore) {
        this.repositoryService = repositoryService;
        this.endpointStore = endpointStore;
    }

    @GetMapping
    List<HttpEndpointView> list(
            @PathVariable UUID repositoryId,
            @RequestParam(defaultValue = "") String search) {
        repositoryService.get(repositoryId);
        return endpointStore.list(repositoryId, search);
    }
}
