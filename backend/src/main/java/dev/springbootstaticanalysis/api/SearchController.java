package dev.springbootstaticanalysis.api;

import dev.springbootstaticanalysis.repository.RepositoryService;
import dev.springbootstaticanalysis.search.CodeSearchResponse;
import dev.springbootstaticanalysis.search.CodeSearchStore;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/search")
public class SearchController {

    private final RepositoryService repositories;
    private final CodeSearchStore search;

    public SearchController(RepositoryService repositories, CodeSearchStore search) {
        this.repositories = repositories;
        this.search = search;
    }

    @GetMapping
    CodeSearchResponse search(
            @PathVariable UUID repositoryId,
            @RequestParam(name = "q") String query) {
        repositories.get(repositoryId);
        return search.search(repositoryId, query);
    }
}
