package dev.codeatlas.analysis;

import dev.codeatlas.indexing.DiscoveredSourceFile;
import java.util.List;
import java.util.Map;

public record RepositoryAnalysis(
        List<DiscoveredSourceFile> files,
        Map<java.util.UUID, String> packages,
        List<AnalyzedSymbol> symbols,
        List<AnalyzedEndpoint> endpoints,
        List<AnalysisWarning> warnings) {
}
