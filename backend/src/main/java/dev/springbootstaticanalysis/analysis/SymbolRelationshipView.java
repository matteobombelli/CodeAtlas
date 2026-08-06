package dev.springbootstaticanalysis.analysis;

import java.util.UUID;

public record SymbolRelationshipView(
        UUID relationshipId,
        UUID symbolId,
        String qualifiedName,
        String kind,
        double confidence,
        String resolutionMethod,
        String evidencePath,
        int evidenceLine,
        String evidenceText) {
}
