package dev.springbootstaticanalysis.shared;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Every setting the backend reads, mirroring {@code application.yml}. */
@ConfigurationProperties("spring-boot-static-analysis")
public record SpringBootStaticAnalysisProperties(
        Path repositoriesRoot,
        long maxSourceFileBytes,
        int maxSourceFiles,
        /** Rejects mutating API requests that did not arrive through the local entrance. */
        boolean readOnly,
        Indexing indexing,
        Graph graph,
        Demo demo) {

    public record Indexing(int maxConcurrentJobs, int queueCapacity) {
    }

    /** Bounds a rendered graph so one request cannot return the entire repository. */
    public record Graph(int maxNodes, int maxEdges) {
    }

    /** The project Compose mounts and the backend registers on startup. */
    public record Demo(
            boolean enabled,
            String displayName,
            String relativePath,
            boolean reindexOnStartup) {
    }
}
