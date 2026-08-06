package dev.sbsa.shared;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sbsa")
public record SbsaProperties(
        Path repositoriesRoot,
        long maxSourceFileBytes,
        int maxSourceFiles,
        /** Rejects mutating API requests. Enabled for public deployments. */
        boolean readOnly,
        Indexing indexing,
        Graph graph) {

    public record Indexing(int maxConcurrentJobs, int queueCapacity) {
    }

    /** Bounds on a single rendered graph, so one query cannot return the whole repository. */
    public record Graph(int maxNodes, int maxEdges) {
    }
}
