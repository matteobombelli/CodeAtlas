package dev.sbsa.source;

public record IndexedSourceFile(
        String relativePath,
        String contentHash,
        int lineCount,
        long fileSize) {
}
