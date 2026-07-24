package dev.codeatlas.source;

public record SourceExcerpt(
        String path,
        int startLine,
        int endLine,
        String language,
        String content,
        String contentHash) {
}
