package dev.springbootstaticanalysis.search;

import java.util.List;

public record CodeSearchResponse(
        List<CodeSearchResult> endpoints,
        List<CodeSearchResult> callables,
        List<CodeSearchResult> files) {
}
