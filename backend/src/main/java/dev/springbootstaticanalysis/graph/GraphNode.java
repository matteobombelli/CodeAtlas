package dev.springbootstaticanalysis.graph;

import java.util.List;

public record GraphNode(
        String id,
        String resourceType,
        String kind,
        String label,
        String subtitle,
        SourceLocation source,
        List<String> roles) {

    public record SourceLocation(String path, int startLine, int endLine) {
    }
}
