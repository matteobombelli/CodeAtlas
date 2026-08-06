package dev.springbootstaticanalysis.indexing;

import java.util.UUID;

public record DiscoveredSourceFile(
        UUID id,
        String relativePath,
        String sourceSet,
        String moduleName,
        String contentHash,
        int lineCount,
        long fileSize) {
}
