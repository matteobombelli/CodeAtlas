package dev.codeatlas.search;

import java.util.UUID;

public record CodeSearchResult(
        UUID id,
        String label,
        String detail,
        String sourcePath,
        int startLine,
        int endLine,
        String httpMethod) {
}
