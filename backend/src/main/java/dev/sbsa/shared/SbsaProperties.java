package dev.sbsa.shared;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sbsa")
public record SbsaProperties(
        Path repositoriesRoot,
        long maxSourceFileBytes,
        int maxSourceFiles,
        Indexing indexing) {

    public record Indexing(int maxConcurrentJobs, int queueCapacity) {
    }
}
