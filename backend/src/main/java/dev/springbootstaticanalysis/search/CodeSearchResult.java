package dev.springbootstaticanalysis.search;

import java.util.UUID;

public record CodeSearchResult(
        UUID id,
        UUID symbolId,
        String kind,
        String label,
        String detail,
        String sourcePath,
        int startLine,
        int endLine,
        String httpMethod) {
}
