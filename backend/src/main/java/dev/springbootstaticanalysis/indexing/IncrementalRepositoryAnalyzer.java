package dev.springbootstaticanalysis.indexing;

import dev.springbootstaticanalysis.analysis.JavaSourceAnalyzer;
import dev.springbootstaticanalysis.analysis.RelationshipAnalyzer;
import dev.springbootstaticanalysis.analysis.RepositoryAnalysis;
import dev.springbootstaticanalysis.analysis.RepositoryAnalyzer.RelationshipAnalysis;
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

    public IncrementalRepositoryAnalyzer(
            JavaSourceAnalyzer sourceAnalyzer,
            RelationshipAnalyzer relationshipAnalyzer,
            AnalysisSnapshotStore snapshots) {
        this.sourceAnalyzer = sourceAnalyzer;
        this.relationshipAnalyzer = relationshipAnalyzer;
        this.snapshots = snapshots;
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
        List<dev.springbootstaticanalysis.analysis.AnalyzedSymbol> combined = new ArrayList<>(
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
                List.of());
        RelationshipAnalysis resolved = relationshipAnalyzer.analyze(
                root,
                resolutionInput,
                affectedFiles,
                snapshots.managedEntityRelationships(repositoryId));
        Set<UUID> freshSymbolIds = fresh.symbols().stream()
                .map(dev.springbootstaticanalysis.analysis.AnalyzedSymbol::id)
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
                resolved.externalReferences());
    }
}
