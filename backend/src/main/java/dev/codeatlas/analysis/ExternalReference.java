package dev.codeatlas.analysis;

import java.util.UUID;

public record ExternalReference(
        UUID id,
        UUID sourceSymbolId,
        UUID sourceFileId,
        String displayName,
        int sourceLine,
        int sourceColumn) {
}
