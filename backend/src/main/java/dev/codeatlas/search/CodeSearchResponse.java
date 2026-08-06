package dev.codeatlas.search;

import java.util.List;

public record CodeSearchResponse(
        List<CodeSearchResult> endpoints,
        List<CodeSearchResult> methods,
        List<CodeSearchResult> files) {
}
