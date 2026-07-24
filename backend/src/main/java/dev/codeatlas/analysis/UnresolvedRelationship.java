package dev.codeatlas.analysis;

import java.util.UUID;

public record UnresolvedRelationship(
        UUID id,
        UUID sourceSymbolId,
        UUID sourceFileId,
        String expression,
        RelationshipKind expectedKind,
        int sourceLine,
        String failureReason,
        int candidateCount) {
}
