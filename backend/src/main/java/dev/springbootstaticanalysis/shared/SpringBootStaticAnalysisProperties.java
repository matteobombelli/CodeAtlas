package dev.springbootstaticanalysis.shared;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring-boot-static-analysis")
public record SpringBootStaticAnalysisProperties(
        Path repositoriesRoot,
        long maxSourceFileBytes,
        int maxSourceFiles,
        Indexing indexing) {

    public record Indexing(int maxConcurrentJobs, int queueCapacity) {
    }
}
