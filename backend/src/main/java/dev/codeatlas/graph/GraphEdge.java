package dev.codeatlas.graph;

public record GraphEdge(
        String id,
        String source,
        String target,
        String kind,
        double confidence,
        String confidenceLabel,
        String resolutionMethod,
        Evidence evidence) {

    public record Evidence(String path, int line, int column, String text) {
    }
}
