package dev.sbsa.analysis;

import java.util.List;
import java.util.UUID;

public record SymbolView(
        UUID id,
        String kind,
        String simpleName,
        String qualifiedName,
        String signature,
        String visibility,
        String sourcePath,
        int startLine,
        int endLine,
        List<String> roles) {
}
