package dev.codeatlas.analysis;

import java.util.UUID;

public record AnalysisWarning(
        UUID id,
        UUID sourceFileId,
        String category,
        String message,
        Integer sourceLine) {
}
