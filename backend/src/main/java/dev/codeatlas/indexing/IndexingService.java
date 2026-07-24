package dev.codeatlas.indexing;

import dev.codeatlas.analysis.RepositoryAnalyzer;
import dev.codeatlas.analysis.RepositoryAnalysis;
import dev.codeatlas.git.GitHistoryAnalyzer;
import dev.codeatlas.repository.RepositoryService;
import dev.codeatlas.repository.RepositoryStore;
import dev.codeatlas.shared.ConflictException;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class IndexingService {

    private final IndexStore indexStore;
    private final RepositoryService repositoryService;
    private final RepositoryStore repositoryStore;
    private final SourceFileDiscovery discovery;
    private final RepositoryAnalyzer analyzer;
    private final GitHistoryAnalyzer gitHistoryAnalyzer;
    private final AnalysisSnapshotStore snapshots;
    private final IncrementalRepositoryAnalyzer incrementalAnalyzer;
    private final Executor executor;

    public IndexingService(
            IndexStore indexStore,
            RepositoryService repositoryService,
            RepositoryStore repositoryStore,
            SourceFileDiscovery discovery,
            RepositoryAnalyzer analyzer,
            GitHistoryAnalyzer gitHistoryAnalyzer,
            AnalysisSnapshotStore snapshots,
            IncrementalRepositoryAnalyzer incrementalAnalyzer,
            @Qualifier("indexingExecutor") Executor executor) {
        this.indexStore = indexStore;
        this.repositoryService = repositoryService;
        this.repositoryStore = repositoryStore;
        this.discovery = discovery;
        this.analyzer = analyzer;
        this.gitHistoryAnalyzer = gitHistoryAnalyzer;
        this.snapshots = snapshots;
        this.incrementalAnalyzer = incrementalAnalyzer;
        this.executor = executor;
    }

    @PostConstruct
    void recoverInterruptedJobs() {
        indexStore.recoverInterrupted();
    }

    public IndexRun start(UUID repositoryId, IndexMode mode) {
        var repository = repositoryService.refreshGitState(repositoryId);
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

    public IndexRun get(UUID runId) {
        return indexStore.get(runId);
    }

    public List<IndexRun> list(UUID repositoryId) {
        return indexStore.list(repositoryId);
    }

    private void execute(UUID repositoryId, UUID runId, IndexMode mode) {
        try {
            String startingHead = repositoryService.refreshGitState(repositoryId).headSha();
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
                executeIncremental(repositoryId, runId, root, files, startingHead);
            } else {
                RepositoryAnalysis analysis = fullAnalysis(root, files);
                assertUnchanged(root, files);
                assertHeadUnchanged(repositoryId, startingHead);
                indexStore.complete(repositoryId, runId, analysis);
            }
        } catch (Exception exception) {
            String code = exception instanceof RepositoryChangedDuringIndexException
                    ? "REPOSITORY_CHANGED_DURING_INDEX" : "INDEXING_FAILED";
            indexStore.fail(repositoryId, runId, code, exception.getMessage());
        }
    }

    private void executeIncremental(
            UUID repositoryId,
            UUID runId,
            Path root,
            List<DiscoveredSourceFile> currentFiles,
            String startingHead) {
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
        assertHeadUnchanged(repositoryId, startingHead);
        indexStore.completeIncremental(
                repositoryId, runId, analysis, changes, currentFiles.size());
    }

    private void assertHeadUnchanged(UUID repositoryId, String startingHead) {
        String currentHead = repositoryService.refreshGitState(repositoryId).headSha();
        if (!java.util.Objects.equals(startingHead, currentHead)) {
            throw new RepositoryChangedDuringIndexException();
        }
    }

    private RepositoryAnalysis fullAnalysis(
            Path root, List<DiscoveredSourceFile> files) {
        RepositoryAnalysis analysis = analyzer.analyze(root, files);
        return new RepositoryAnalysis(
                analysis.files(),
                analysis.packages(),
                analysis.symbols(),
                analysis.endpoints(),
                analysis.warnings(),
                analysis.relationships(),
                analysis.unresolved(),
                analysis.externalReferences(),
                gitHistoryAnalyzer.analyze(root, files));
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
