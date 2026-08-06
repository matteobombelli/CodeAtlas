package dev.sbsa.analysis;

import dev.sbsa.indexing.DiscoveredSourceFile;
import dev.sbsa.git.GitFileStat;
import java.util.List;
import java.util.Map;

public record RepositoryAnalysis(
        List<DiscoveredSourceFile> files,
        Map<java.util.UUID, String> packages,
        List<AnalyzedSymbol> symbols,
        List<AnalyzedEndpoint> endpoints,
        List<AnalysisWarning> warnings,
        List<AnalyzedRelationship> relationships,
        List<UnresolvedRelationship> unresolved,
        List<ExternalReference> externalReferences,
        List<GitFileStat> gitFileStats) {
}
