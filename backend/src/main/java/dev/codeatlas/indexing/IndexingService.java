package dev.codeatlas.indexing;

import dev.codeatlas.analysis.RepositoryAnalyzer;
import dev.codeatlas.repository.RepositoryStore;
import dev.codeatlas.shared.ConflictException;
import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class IndexingService {

    private final IndexStore indexStore;
    private final RepositoryStore repositoryStore;
    private final SourceFileDiscovery discovery;
    private final RepositoryAnalyzer analyzer;
    private final Executor executor;

    public IndexingService(
            IndexStore indexStore,
            RepositoryStore repositoryStore,
            SourceFileDiscovery discovery,
            RepositoryAnalyzer analyzer,
            @Qualifier("indexingExecutor") Executor executor) {
        this.indexStore = indexStore;
        this.repositoryStore = repositoryStore;
        this.discovery = discovery;
        this.analyzer = analyzer;
        this.executor = executor;
    }

    @PostConstruct
    void recoverInterruptedJobs() {
        indexStore.recoverInterrupted();
    }

    public IndexRun start(UUID repositoryId, IndexMode mode) {
        repositoryStore.get(repositoryId);
        if (mode == IndexMode.INCREMENTAL) {
            // The API contract is stable before targeted invalidation lands.
            mode = IndexMode.FULL;
        }
        IndexRun run = indexStore.create(repositoryId, mode);
        try {
            executor.execute(() -> execute(repositoryId, run.id()));
        } catch (RejectedExecutionException exception) {
            indexStore.fail(repositoryId, run.id(), "QUEUE_FULL", "Indexing queue is full");
            throw new ConflictException("Indexing queue is full");
        }
        return run;
    }

    public IndexRun get(UUID runId) {
        return indexStore.get(runId);
    }

    public List<IndexRun> list(UUID repositoryId) {
        return indexStore.list(repositoryId);
    }

    private void execute(UUID repositoryId, UUID runId) {
        try {
            Path root = repositoryStore.canonicalPath(repositoryId);
            indexStore.phase(runId, IndexPhase.DISCOVERING, 0, 0);
            List<Path> paths = discovery.discover(root);
            indexStore.phase(runId, IndexPhase.HASHING, paths.size(), 0);
            List<DiscoveredSourceFile> files = new ArrayList<>();
            for (int index = 0; index < paths.size(); index++) {
                files.add(discovery.describe(root, paths.get(index)));
                if ((index + 1) % 25 == 0 || index + 1 == paths.size()) {
                    indexStore.phase(runId, IndexPhase.HASHING, paths.size(), index + 1);
                }
            }
            indexStore.phase(runId, IndexPhase.PARSING, paths.size(), paths.size());
            indexStore.complete(repositoryId, runId, analyzer.analyze(root, files));
        } catch (Exception exception) {
            indexStore.fail(repositoryId, runId, "INDEXING_FAILED", exception.getMessage());
        }
    }
}
