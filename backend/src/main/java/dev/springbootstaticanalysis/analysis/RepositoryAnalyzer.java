package dev.springbootstaticanalysis.analysis;

import dev.springbootstaticanalysis.indexing.DiscoveredSourceFile;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RepositoryAnalyzer {

    private final JavaSourceAnalyzer sourceAnalyzer;
    private final RelationshipAnalyzer relationshipAnalyzer;

    public RepositoryAnalyzer(
            JavaSourceAnalyzer sourceAnalyzer,
            RelationshipAnalyzer relationshipAnalyzer) {
        this.sourceAnalyzer = sourceAnalyzer;
        this.relationshipAnalyzer = relationshipAnalyzer;
    }

    public RepositoryAnalysis analyze(Path root, List<DiscoveredSourceFile> files) {
        RepositoryAnalysis symbols = sourceAnalyzer.analyze(root, files);
        RelationshipAnalysis relationships = relationshipAnalyzer.analyze(root, symbols);
        return new RepositoryAnalysis(
                symbols.files(),
                symbols.packages(),
                symbols.symbols(),
                symbols.endpoints(),
                symbols.warnings(),
                relationships.relationships(),
                relationships.unresolved(),
                relationships.externalReferences());
    }

    public record RelationshipAnalysis(
            List<AnalyzedRelationship> relationships,
            List<UnresolvedRelationship> unresolved,
            List<ExternalReference> externalReferences) {
    }
}
