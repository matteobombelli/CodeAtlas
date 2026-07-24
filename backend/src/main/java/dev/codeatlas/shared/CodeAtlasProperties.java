package dev.codeatlas.shared;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("code-atlas")
public record CodeAtlasProperties(
        Path repositoriesRoot,
        long maxSourceFileBytes,
        int maxSourceFiles,
        Indexing indexing) {

    public record Indexing(int maxConcurrentJobs, int queueCapacity) {
    }
}
