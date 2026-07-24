package dev.codeatlas.api;

import dev.codeatlas.indexing.IndexRun;
import dev.codeatlas.indexing.IndexingService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/index-runs")
public class IndexRunController {

    private final IndexingService indexingService;

    public IndexRunController(IndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @GetMapping("/{indexRunId}")
    IndexRun get(@PathVariable UUID indexRunId) {
        return indexingService.get(indexRunId);
    }
}
