package dev.springbootstaticanalysis.shared;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring-boot-static-analysis")
public record SpringBootStaticAnalysisProperties(
        Path repositoriesRoot,
        long maxSourceFileBytes,
        int maxSourceFiles,
        /** Rejects mutating API requests. Enable this on every public deployment. */
        boolean readOnly,
        Indexing indexing,
        Graph graph) {

    public record Indexing(int maxConcurrentJobs, int queueCapacity) {
    }

    /** Bounds a rendered graph so one request cannot return the entire repository. */
    public record Graph(int maxNodes, int maxEdges) {
    }
}
