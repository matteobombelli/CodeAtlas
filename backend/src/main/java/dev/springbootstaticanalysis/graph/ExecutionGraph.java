package dev.springbootstaticanalysis.graph;

import java.util.List;

public record ExecutionGraph(
        String rootNodeId,
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        List<GraphWarning> warnings,
        boolean truncated,
        String truncationReason) {

    public record GraphWarning(String type, String message) {
    }
}
