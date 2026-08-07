package dev.springbootstaticanalysis.indexing;

import dev.springbootstaticanalysis.analysis.RepositoryAnalyzer;
import dev.springbootstaticanalysis.analysis.RepositoryAnalysis;
import dev.springbootstaticanalysis.repository.RepositoryService;
import dev.springbootstaticanalysis.repository.RepositoryStore;
import dev.springbootstaticanalysis.shared.ConflictException;
import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class IndexingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexingService.class);

    private final IndexStore indexStore;
    private final RepositoryService repositoryService;
    private final RepositoryStore repositoryStore;
    private final SourceFileDiscovery discovery;
    private final RepositoryAnalyzer analyzer;
    private final AnalysisSnapshotStore snapshots;
    private final IncrementalRepositoryAnalyzer incrementalAnalyzer;
    private final Executor executor;

    public IndexingService(
            IndexStore indexStore,
            RepositoryService repositoryService,
            RepositoryStore repositoryStore,
            SourceFileDiscovery discovery,
            RepositoryAnalyzer analyzer,
            AnalysisSnapshotStore snapshots,
            IncrementalRepositoryAnalyzer incrementalAnalyzer,
            @Qualifier("indexingExecutor") Executor executor) {
        this.indexStore = indexStore;
        this.repositoryService = repositoryService;
        this.repositoryStore = repositoryStore;
        this.discovery = discovery;
        this.analyzer = analyzer;
        this.snapshots = snapshots;
        this.incrementalAnalyzer = incrementalAnalyzer;
        this.executor = executor;
    }

    @PostConstruct
    void recoverInterruptedJobs() {
        indexStore.recoverInterrupted();
    }

    public IndexRun start(UUID repositoryId, IndexMode mode) {
        var repository = repositoryService.get(repositoryId);
        if (mode == IndexMode.INCREMENTAL && repository.activeIndexRunId() == null) {
            mode = IndexMode.FULL;
        }
        IndexRun run = indexStore.create(repositoryId, mode);
        IndexMode selectedMode = mode;
        try {
            executor.execute(() -> execute(repositoryId, run.id(), selectedMode));
        } catch (RejectedExecutionException exception) {
            indexStore.fail(repositoryId, run.id(), "QUEUE_FULL", "Indexing queue is full");
            throw new ConflictException("Indexing queue is full");
        }
        return run;
    }

    private void execute(UUID repositoryId, UUID runId, IndexMode mode) {
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
            if (mode == IndexMode.INCREMENTAL) {
                executeIncremental(repositoryId, runId, root, files);
            } else {
                RepositoryAnalysis analysis = fullAnalysis(root, files);
                assertUnchanged(root, files);
                indexStore.complete(repositoryId, runId, analysis);
            }
        } catch (Exception exception) {
            if (exception instanceof RepositoryChangedDuringIndexException) {
                indexStore.fail(
                        repositoryId,
                        runId,
                        "REPOSITORY_CHANGED_DURING_INDEX",
                        "Repository contents changed while indexing; retry the index run");
                LOGGER.info(
                        "Index run {} for repository {} stopped because source files changed",
                        runId,
                        repositoryId);
                return;
            }
            String reference = runId.toString();
            LOGGER.error(
                    "Index run {} for repository {} failed",
                    runId,
                    repositoryId,
                    exception);
            indexStore.fail(
                    repositoryId,
                    runId,
                    "INDEXING_FAILED",
                    "Indexing failed; check backend logs for run " + reference);
        }
    }

    private void executeIncremental(
            UUID repositoryId,
            UUID runId,
            Path root,
            List<DiscoveredSourceFile> currentFiles) {
        Map<String, String> previous = snapshots.hashes(repositoryId);
        Map<String, DiscoveredSourceFile> current = currentFiles.stream()
                .collect(java.util.stream.Collectors.toMap(
                        DiscoveredSourceFile::relativePath, file -> file));
        Set<String> added = new HashSet<>(current.keySet());
        added.removeAll(previous.keySet());
        Set<String> deleted = new HashSet<>(previous.keySet());
        deleted.removeAll(current.keySet());
        Set<String> modified = current.entrySet().stream()
                .filter(entry -> previous.containsKey(entry.getKey()))
                .filter(entry -> !previous.get(entry.getKey()).equals(entry.getValue().contentHash()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> changed = new HashSet<>(added);
        changed.addAll(modified);
        changed.addAll(deleted);
        Set<String> affected = new HashSet<>(changed);
        affected.addAll(snapshots.dependantPaths(repositoryId, changed));
        ChangeSummary changes = new ChangeSummary(
                Set.copyOf(added),
                Set.copyOf(modified),
                Set.copyOf(deleted),
                Set.copyOf(affected));

        int invalidationCap = Math.min(500, Math.max(1, currentFiles.size() / 4));
        RepositoryAnalysis analysis;
        if (affected.size() > invalidationCap) {
            Set<String> allPaths = new HashSet<>(current.keySet());
            allPaths.addAll(deleted);
            changes = new ChangeSummary(
                    changes.added(),
                    changes.modified(),
                    changes.deleted(),
                    Set.copyOf(allPaths));
            analysis = fullAnalysis(root, currentFiles);
        } else {
            analysis = incrementalAnalyzer.analyze(
                    repositoryId, root, currentFiles, changes);
        }
        assertUnchanged(root, currentFiles);
        indexStore.completeIncremental(
                repositoryId, runId, analysis, changes, currentFiles.size());
    }

    private RepositoryAnalysis fullAnalysis(
            Path root, List<DiscoveredSourceFile> files) {
        return analyzer.analyze(root, files);
    }

    private void assertUnchanged(
            Path root, List<DiscoveredSourceFile> snapshot) {
        Set<String> expectedPaths = snapshot.stream()
                .map(DiscoveredSourceFile::relativePath)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> currentPaths = discovery.discover(root).stream()
                .map(path -> root.relativize(path).toString().replace('\\', '/'))
                .collect(java.util.stream.Collectors.toSet());
        if (!currentPaths.equals(expectedPaths)) {
            throw new RepositoryChangedDuringIndexException();
        }
        for (DiscoveredSourceFile expected : snapshot) {
            Path file = root.resolve(expected.relativePath());
            if (!java.nio.file.Files.exists(file)) {
                throw new RepositoryChangedDuringIndexException();
            }
            DiscoveredSourceFile current = discovery.describe(root, file);
            if (!current.contentHash().equals(expected.contentHash())) {
                throw new RepositoryChangedDuringIndexException();
            }
        }
    }
}
