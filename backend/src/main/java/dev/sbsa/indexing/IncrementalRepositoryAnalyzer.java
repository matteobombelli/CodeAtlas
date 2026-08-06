package dev.sbsa.indexing;

import dev.sbsa.analysis.JavaSourceAnalyzer;
import dev.sbsa.analysis.RelationshipAnalyzer;
import dev.sbsa.analysis.RepositoryAnalysis;
import dev.sbsa.analysis.RepositoryAnalyzer.RelationshipAnalysis;
import dev.sbsa.git.GitHistoryAnalyzer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IncrementalRepositoryAnalyzer {

    private final JavaSourceAnalyzer sourceAnalyzer;
    private final RelationshipAnalyzer relationshipAnalyzer;
    private final AnalysisSnapshotStore snapshots;
    private final GitHistoryAnalyzer gitHistory;

    public IncrementalRepositoryAnalyzer(
            JavaSourceAnalyzer sourceAnalyzer,
            RelationshipAnalyzer relationshipAnalyzer,
            AnalysisSnapshotStore snapshots,
            GitHistoryAnalyzer gitHistory) {
        this.sourceAnalyzer = sourceAnalyzer;
        this.relationshipAnalyzer = relationshipAnalyzer;
        this.snapshots = snapshots;
        this.gitHistory = gitHistory;
    }

    public RepositoryAnalysis analyze(
            UUID repositoryId,
            Path root,
            List<DiscoveredSourceFile> currentFiles,
            ChangeSummary changes) {
        List<DiscoveredSourceFile> affectedFiles = currentFiles.stream()
                .filter(file -> changes.affected().contains(file.relativePath()))
                .toList();
        RepositoryAnalysis fresh = sourceAnalyzer.analyze(root, affectedFiles);
        List<dev.sbsa.analysis.AnalyzedSymbol> combined = new ArrayList<>(
                snapshots.symbolsExcluding(repositoryId, changes.affected()));
        combined.addAll(fresh.symbols());
        RepositoryAnalysis resolutionInput = new RepositoryAnalysis(
                affectedFiles,
                fresh.packages(),
                combined,
                fresh.endpoints(),
                fresh.warnings(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        RelationshipAnalysis resolved = relationshipAnalyzer.analyze(
                root,
                resolutionInput,
                affectedFiles,
                snapshots.managedEntityRelationships(repositoryId));
        Set<UUID> freshSymbolIds = fresh.symbols().stream()
                .map(dev.sbsa.analysis.AnalyzedSymbol::id)
                .collect(java.util.stream.Collectors.toSet());
        var relationships = resolved.relationships().stream()
                .filter(edge -> freshSymbolIds.contains(edge.sourceSymbolId()))
                .toList();
        return new RepositoryAnalysis(
                affectedFiles,
                fresh.packages(),
                fresh.symbols(),
                fresh.endpoints(),
                fresh.warnings(),
                relationships,
                resolved.unresolved(),
                resolved.externalReferences(),
                gitHistory.analyze(root, affectedFiles));
    }
}
