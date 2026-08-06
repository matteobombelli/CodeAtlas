package dev.springbootstaticanalysis.api;

import dev.springbootstaticanalysis.source.SourceExcerpt;
import dev.springbootstaticanalysis.source.SourceService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/source")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @GetMapping
    SourceExcerpt source(
            @PathVariable UUID repositoryId,
            @RequestParam String path,
            @RequestParam int startLine,
            @RequestParam int endLine) {
        return sourceService.excerpt(repositoryId, path, startLine, endLine);
    }
}
