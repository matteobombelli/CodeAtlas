package dev.codeatlas.analysis;

import java.util.Set;
import java.util.UUID;

public record AnalyzedSymbol(
        UUID id,
        UUID sourceFileId,
        UUID parentSymbolId,
        String symbolKey,
        SymbolKind kind,
        String simpleName,
        String qualifiedName,
        String signature,
        String visibility,
        int startLine,
        int endLine,
        int startColumn,
        int endColumn,
        boolean abstractSymbol,
        boolean staticSymbol,
        Set<SymbolRole> roles) {
}
