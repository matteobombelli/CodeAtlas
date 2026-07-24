package dev.codeatlas.analysis;

import java.util.UUID;

public record AnalyzedRelationship(
        UUID id,
        UUID sourceSymbolId,
        UUID targetSymbolId,
        RelationshipKind kind,
        double confidence,
        ResolutionMethod resolutionMethod,
        UUID sourceFileId,
        int sourceLine,
        int sourceColumn,
        String evidenceText) {
}
