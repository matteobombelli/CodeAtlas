package dev.codeatlas.graph;

import dev.codeatlas.graph.ExecutionGraph.GraphWarning;
import dev.codeatlas.graph.GraphEdge.Evidence;
import dev.codeatlas.graph.GraphNode.SourceLocation;
import dev.codeatlas.shared.InvalidRequestException;
import dev.codeatlas.shared.NotFoundException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GraphStore {

    private static final int DEFAULT_NODE_LIMIT = 100;
    private static final int DEFAULT_EDGE_LIMIT = 250;
    private static final Set<String> TRAVERSABLE =
            Set.of("CALLS", "READS_ENTITY", "WRITES_ENTITY", "MANAGES_ENTITY");

    private final NamedParameterJdbcTemplate jdbc;

    public GraphStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ExecutionGraph endpointGraph(
            UUID repositoryId,
            UUID endpointId,
            int maxDepth,
            boolean includeUncertain,
            boolean includeExternal) {
        if (maxDepth < 1 || maxDepth > 8) {
            throw new InvalidRequestException("maxDepth must be between 1 and 8");
        }
        EndpointRoot root = endpointRoot(repositoryId, endpointId);
        LinkedHashMap<String, GraphNode> nodes = new LinkedHashMap<>();
        List<GraphEdge> edges = new ArrayList<>();
        List<GraphWarning> warnings = new ArrayList<>();

        String endpointNodeId = "endpoint:" + root.endpointId();
        nodes.put(endpointNodeId, new GraphNode(
                endpointNodeId,
                "ENDPOINT",
                "ENDPOINT",
                root.httpMethod() + " " + root.path(),
                root.controller() + "." + root.method(),
                new SourceLocation(root.sourcePath(), root.startLine(), root.endLine()),
                List.of("ENDPOINT")));

        GraphNode controllerMethod = symbolNode(root.methodId());
        nodes.put(controllerMethod.id(), controllerMethod);
        edges.add(new GraphEdge(
                "handles:" + root.endpointId(),
                endpointNodeId,
                controllerMethod.id(),
                "HANDLES_ENDPOINT",
                1.0,
                "EXACT",
                "SPRING_MAPPING",
                new Evidence(root.sourcePath(), root.startLine(), 1, "Spring request mapping")));

        ArrayDeque<Visit> queue = new ArrayDeque<>();
        queue.add(new Visit(root.methodId(), 0));
        boolean truncated = false;
        String reason = null;
        while (!queue.isEmpty()) {
            Visit visit = queue.removeFirst();
            if (visit.depth() >= maxDepth) {
                continue;
            }
            for (StoredEdge edge : outgoing(repositoryId, visit.symbolId())) {
                if (!TRAVERSABLE.contains(edge.kind())
                        || (!includeUncertain && edge.confidence() < 0.70)) {
                    continue;
                }
                if (edges.size() >= DEFAULT_EDGE_LIMIT) {
                    truncated = true;
                    reason = "EDGE_LIMIT";
                    queue.clear();
                    break;
                }
                GraphNode target = symbolNode(edge.targetId());
                if (!nodes.containsKey(target.id()) && nodes.size() >= DEFAULT_NODE_LIMIT) {
                    truncated = true;
                    reason = "NODE_LIMIT";
                    queue.clear();
                    break;
                }
                boolean unseen = nodes.putIfAbsent(target.id(), target) == null;
                edges.add(edge.toGraphEdge());
                if (unseen) {
                    queue.addLast(new Visit(edge.targetId(), visit.depth() + 1));
                }
            }
            if (!includeExternal) {
                continue;
            }
            for (StoredExternal reference : external(repositoryId, visit.symbolId())) {
                if (nodes.size() >= DEFAULT_NODE_LIMIT || edges.size() >= DEFAULT_EDGE_LIMIT) {
                    truncated = true;
                    reason = nodes.size() >= DEFAULT_NODE_LIMIT ? "NODE_LIMIT" : "EDGE_LIMIT";
                    queue.clear();
                    break;
                }
                String id = "external:" + reference.id();
                nodes.putIfAbsent(id, new GraphNode(
                        id, "EXTERNAL", "EXTERNAL", reference.displayName(),
                        "External or unavailable dependency",
                        new SourceLocation(reference.path(), reference.line(), reference.line()),
                        List.of("EXTERNAL")));
                edges.add(new GraphEdge(
                        "external-edge:" + reference.id(),
                        visit.symbolId().toString(),
                        id,
                        "CALLS",
                        1.0,
                        "EXACT_EXTERNAL",
                        "EXTERNAL_REFERENCE",
                        new Evidence(
                                reference.path(), reference.line(), reference.column(),
                                reference.displayName())));
            }
        }

        int unresolvedCount = unresolvedCount(repositoryId, nodes.keySet());
        if (unresolvedCount > 0) {
            warnings.add(new GraphWarning(
                    "UNRESOLVED_RELATIONSHIPS",
                    unresolvedCount + " call expression(s) could not be resolved."));
        }
        if (truncated) {
            warnings.add(new GraphWarning(
                    "TRUNCATED",
                    "Graph stopped at the configured " + reason.toLowerCase().replace('_', ' ') + "."));
        }
        return new ExecutionGraph(
                endpointNodeId,
                List.copyOf(nodes.values()),
                edges,
                warnings,
                truncated,
                reason);
    }

    public ExecutionGraph blastRadius(
            UUID repositoryId,
            UUID symbolId,
            int maxDepth,
            boolean includeUncertain) {
        if (maxDepth < 1 || maxDepth > 8) {
            throw new InvalidRequestException("maxDepth must be between 1 and 8");
        }
        LinkedHashMap<String, GraphNode> nodes = new LinkedHashMap<>();
        List<GraphEdge> edges = new ArrayList<>();
        List<GraphWarning> warnings = new ArrayList<>();
        GraphNode root = symbolNode(symbolId);
        nodes.put(root.id(), root);
        for (EndpointRoot endpoint : endpointsFor(repositoryId, symbolId)) {
            String endpointNodeId = "endpoint:" + endpoint.endpointId();
            nodes.put(endpointNodeId, new GraphNode(
                    endpointNodeId,
                    "ENDPOINT",
                    "ENDPOINT",
                    endpoint.httpMethod() + " " + endpoint.path(),
                    endpoint.controller() + "." + endpoint.method(),
                    new SourceLocation(
                            endpoint.sourcePath(), endpoint.startLine(), endpoint.endLine()),
                    List.of("ENDPOINT")));
            edges.add(new GraphEdge(
                    "affected:" + endpoint.endpointId(),
                    endpointNodeId,
                    root.id(),
                    "REACHES",
                    1.0,
                    "EXACT",
                    "REVERSE_CALL_PATH",
                    new Evidence(
                            endpoint.sourcePath(), endpoint.startLine(), 1,
                            "Endpoint is implemented by the selected symbol")));
        }
        ArrayDeque<Visit> queue = new ArrayDeque<>();
        queue.add(new Visit(symbolId, 0));
        boolean truncated = false;
        String reason = null;

        for (StoredEdge edge : outgoing(repositoryId, symbolId)) {
            if (Set.of("READS_ENTITY", "WRITES_ENTITY", "MANAGES_ENTITY").contains(edge.kind())) {
                GraphNode target = symbolNode(edge.targetId());
                nodes.putIfAbsent(target.id(), target);
                edges.add(edge.toGraphEdge());
            }
        }

        while (!queue.isEmpty()) {
            Visit visit = queue.removeFirst();
            if (visit.depth() >= maxDepth) {
                continue;
            }
            for (StoredEdge edge : incoming(repositoryId, visit.symbolId())) {
                if (!Set.of("CALLS", "TESTS", "IMPLEMENTS", "REFERENCES").contains(edge.kind())
                        || (!includeUncertain && edge.confidence() < 0.70)) {
                    continue;
                }
                if (nodes.size() >= DEFAULT_NODE_LIMIT || edges.size() >= DEFAULT_EDGE_LIMIT) {
                    truncated = true;
                    reason = nodes.size() >= DEFAULT_NODE_LIMIT ? "NODE_LIMIT" : "EDGE_LIMIT";
                    queue.clear();
                    break;
                }
                GraphNode source = symbolNode(edge.sourceId());
                boolean unseen = nodes.putIfAbsent(source.id(), source) == null;
                edges.add(edge.toGraphEdge());
                for (EndpointRoot endpoint : endpointsFor(repositoryId, edge.sourceId())) {
                    String endpointNodeId = "endpoint:" + endpoint.endpointId();
                    nodes.putIfAbsent(endpointNodeId, new GraphNode(
                            endpointNodeId,
                            "ENDPOINT",
                            "ENDPOINT",
                            endpoint.httpMethod() + " " + endpoint.path(),
                            endpoint.controller() + "." + endpoint.method(),
                            new SourceLocation(
                                    endpoint.sourcePath(), endpoint.startLine(), endpoint.endLine()),
                            List.of("ENDPOINT")));
                    edges.add(new GraphEdge(
                            "affected:" + endpoint.endpointId(),
                            endpointNodeId,
                            source.id(),
                            "REACHES",
                            1.0,
                            "EXACT",
                            "REVERSE_CALL_PATH",
                            new Evidence(
                                    endpoint.sourcePath(), endpoint.startLine(), 1,
                                    "Endpoint reaches the selected symbol")));
                }
                if (unseen && !source.roles().contains("TEST")) {
                    queue.addLast(new Visit(edge.sourceId(), visit.depth() + 1));
                }
            }
        }

        warnings.add(new GraphWarning(
                "POTENTIAL_IMPACT",
                "Static relationships indicate potential impact, not runtime certainty."));
        if (truncated) {
            warnings.add(new GraphWarning(
                    "TRUNCATED",
                    "Blast radius stopped at the configured "
                            + reason.toLowerCase().replace('_', ' ') + "."));
        }
        return new ExecutionGraph(
                symbolId.toString(),
                List.copyOf(nodes.values()),
                edges,
                warnings,
                truncated,
                reason);
    }

    private EndpointRoot endpointRoot(UUID repositoryId, UUID endpointId) {
        List<EndpointRoot> values = jdbc.query("""
                SELECT e.id, e.http_method, e.path, e.controller_method_id,
                       parent.simple_name AS controller, method.simple_name AS method,
                       sf.relative_path, method.start_line, method.end_line
                FROM http_endpoints e
                JOIN code_symbols method ON method.id = e.controller_method_id
                JOIN code_symbols parent ON parent.id = method.parent_symbol_id
                JOIN source_files sf ON sf.id = method.source_file_id
                WHERE e.repository_id = :repositoryId AND e.id = :endpointId
                """, Map.of("repositoryId", repositoryId, "endpointId", endpointId),
                (row, number) -> new EndpointRoot(
                        row.getObject("id", UUID.class),
                        row.getString("http_method"),
                        row.getString("path"),
                        row.getObject("controller_method_id", UUID.class),
                        row.getString("controller"),
                        row.getString("method"),
                        row.getString("relative_path"),
                        row.getInt("start_line"),
                        row.getInt("end_line")));
        if (values.isEmpty()) {
            throw new NotFoundException("Endpoint " + endpointId + " does not exist");
        }
        return values.getFirst();
    }

    private List<EndpointRoot> endpointsFor(UUID repositoryId, UUID methodId) {
        return jdbc.query("""
                SELECT e.id, e.http_method, e.path, e.controller_method_id,
                       parent.simple_name AS controller, method.simple_name AS method,
                       sf.relative_path, method.start_line, method.end_line
                FROM http_endpoints e
                JOIN code_symbols method ON method.id = e.controller_method_id
                JOIN code_symbols parent ON parent.id = method.parent_symbol_id
                JOIN source_files sf ON sf.id = method.source_file_id
                WHERE e.repository_id = :repositoryId
                  AND e.controller_method_id = :methodId
                """, Map.of("repositoryId", repositoryId, "methodId", methodId),
                (row, number) -> new EndpointRoot(
                        row.getObject("id", UUID.class),
                        row.getString("http_method"),
                        row.getString("path"),
                        row.getObject("controller_method_id", UUID.class),
                        row.getString("controller"),
                        row.getString("method"),
                        row.getString("relative_path"),
                        row.getInt("start_line"),
                        row.getInt("end_line")));
    }

    private GraphNode symbolNode(UUID symbolId) {
        List<GraphNode> values = jdbc.query("""
                SELECT s.id, s.kind, s.simple_name, s.qualified_name, s.signature,
                       s.start_line, s.end_line, sf.relative_path,
                       COALESCE(string_agg(sr.role, ',' ORDER BY sr.role), '') AS roles
                FROM code_symbols s
                JOIN source_files sf ON sf.id = s.source_file_id
                LEFT JOIN code_symbol_roles sr
                    ON sr.symbol_id = COALESCE(s.parent_symbol_id, s.id)
                WHERE s.id = :symbolId
                GROUP BY s.id, sf.relative_path
                """, Map.of("symbolId", symbolId), (row, number) -> {
            String rolesText = row.getString("roles");
            List<String> roles = rolesText.isBlank() ? List.of() : List.of(rolesText.split(","));
            String role = preferredRole(roles, row.getString("kind"));
            String signature = row.getString("signature");
            return new GraphNode(
                    row.getObject("id", UUID.class).toString(),
                    "SYMBOL",
                    role,
                    row.getString("simple_name") + (signature == null ? "" : signature),
                    row.getString("qualified_name"),
                    new SourceLocation(
                            row.getString("relative_path"),
                            row.getInt("start_line"),
                            row.getInt("end_line")),
                    roles);
        });
        if (values.isEmpty()) {
            throw new NotFoundException("Symbol " + symbolId + " does not exist");
        }
        return values.getFirst();
    }

    private List<StoredEdge> outgoing(UUID repositoryId, UUID sourceId) {
        return jdbc.query("""
                SELECT r.*, sf.relative_path
                FROM code_relationships r
                JOIN source_files sf ON sf.id = r.source_file_id
                WHERE r.repository_id = :repositoryId AND r.source_symbol_id = :sourceId
                ORDER BY r.confidence DESC, r.kind, r.source_line
                """, Map.of("repositoryId", repositoryId, "sourceId", sourceId),
                (row, number) -> new StoredEdge(
                        row.getObject("id", UUID.class),
                        row.getObject("source_symbol_id", UUID.class),
                        row.getObject("target_symbol_id", UUID.class),
                        row.getString("kind"),
                        row.getDouble("confidence"),
                        row.getString("resolution_method"),
                        row.getString("relative_path"),
                        row.getInt("source_line"),
                        row.getInt("source_column"),
                        row.getString("evidence_text")));
    }

    private List<StoredEdge> incoming(UUID repositoryId, UUID targetId) {
        return jdbc.query("""
                SELECT r.*, sf.relative_path
                FROM code_relationships r
                JOIN source_files sf ON sf.id = r.source_file_id
                WHERE r.repository_id = :repositoryId AND r.target_symbol_id = :targetId
                ORDER BY r.confidence DESC, r.kind, r.source_line
                """, Map.of("repositoryId", repositoryId, "targetId", targetId),
                (row, number) -> new StoredEdge(
                        row.getObject("id", UUID.class),
                        row.getObject("source_symbol_id", UUID.class),
                        row.getObject("target_symbol_id", UUID.class),
                        row.getString("kind"),
                        row.getDouble("confidence"),
                        row.getString("resolution_method"),
                        row.getString("relative_path"),
                        row.getInt("source_line"),
                        row.getInt("source_column"),
                        row.getString("evidence_text")));
    }

    private List<StoredExternal> external(UUID repositoryId, UUID sourceId) {
        return jdbc.query("""
                SELECT x.*, sf.relative_path
                FROM external_references x
                JOIN source_files sf ON sf.id = x.source_file_id
                WHERE x.repository_id = :repositoryId AND x.source_symbol_id = :sourceId
                ORDER BY x.source_line
                LIMIT 20
                """, Map.of("repositoryId", repositoryId, "sourceId", sourceId),
                (row, number) -> new StoredExternal(
                        row.getObject("id", UUID.class),
                        row.getString("display_name"),
                        row.getString("relative_path"),
                        row.getInt("source_line"),
                        row.getInt("source_column")));
    }

    private int unresolvedCount(UUID repositoryId, Set<String> graphNodeIds) {
        List<UUID> symbolIds = graphNodeIds.stream()
                .filter(id -> !id.contains(":"))
                .map(UUID::fromString)
                .toList();
        if (symbolIds.isEmpty()) {
            return 0;
        }
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM unresolved_relationships
                WHERE repository_id = :repositoryId AND source_symbol_id IN (:symbolIds)
                """, Map.of("repositoryId", repositoryId, "symbolIds", symbolIds), Integer.class);
        return count == null ? 0 : count;
    }

    private static String preferredRole(List<String> roles, String fallback) {
        for (String candidate : List.of(
                "CONTROLLER", "SERVICE", "REPOSITORY", "ENTITY", "TEST", "COMPONENT")) {
            if (roles.contains(candidate)) {
                return candidate;
            }
        }
        return fallback;
    }

    private record Visit(UUID symbolId, int depth) {
    }

    private record EndpointRoot(
            UUID endpointId,
            String httpMethod,
            String path,
            UUID methodId,
            String controller,
            String method,
            String sourcePath,
            int startLine,
            int endLine) {
    }

    private record StoredExternal(
            UUID id,
            String displayName,
            String path,
            int line,
            int column) {
    }

    private record StoredEdge(
            UUID id,
            UUID sourceId,
            UUID targetId,
            String kind,
            double confidence,
            String resolutionMethod,
            String path,
            int line,
            int column,
            String evidenceText) {

        GraphEdge toGraphEdge() {
            return new GraphEdge(
                    id.toString(),
                    sourceId.toString(),
                    targetId.toString(),
                    kind,
                    confidence,
                    confidence >= 0.90 ? "HIGH"
                            : confidence >= 0.70 ? "INFERRED" : "AMBIGUOUS",
                    resolutionMethod,
                    new Evidence(path, line, column, evidenceText));
        }
    }
}
