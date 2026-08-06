package dev.springbootstaticanalysis.analysis;

import dev.springbootstaticanalysis.shared.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SymbolStore {

    private final NamedParameterJdbcTemplate jdbc;

    public SymbolStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SymbolView get(UUID repositoryId, UUID symbolId) {
        List<SymbolView> values = jdbc.query("""
                SELECT s.*, sf.relative_path,
                       COALESCE(string_agg(sr.role, ',' ORDER BY sr.role), '') AS roles
                FROM code_symbols s
                JOIN source_files sf ON sf.id = s.source_file_id
                LEFT JOIN code_symbol_roles sr ON sr.symbol_id = s.id
                WHERE s.repository_id = :repositoryId AND s.id = :symbolId
                GROUP BY s.id, sf.relative_path
                """, Map.of("repositoryId", repositoryId, "symbolId", symbolId),
                (row, number) -> {
                    String roles = row.getString("roles");
                    return new SymbolView(
                            row.getObject("id", UUID.class),
                            row.getString("kind"),
                            row.getString("simple_name"),
                            row.getString("qualified_name"),
                            row.getString("signature"),
                            row.getString("visibility"),
                            row.getString("relative_path"),
                            row.getInt("start_line"),
                            row.getInt("end_line"),
                            roles.isBlank() ? List.of() : List.of(roles.split(",")));
                });
        if (values.isEmpty()) {
            throw new NotFoundException("Symbol " + symbolId + " does not exist");
        }
        return values.getFirst();
    }

    public List<SymbolView> search(UUID repositoryId, String query) {
        String normalized = query == null ? "" : query.strip().toLowerCase();
        if (normalized.length() < 2) {
            return List.of();
        }
        return jdbc.query("""
                SELECT s.*, sf.relative_path,
                       COALESCE(string_agg(sr.role, ',' ORDER BY sr.role), '') AS roles
                FROM code_symbols s
                JOIN source_files sf ON sf.id = s.source_file_id
                LEFT JOIN code_symbol_roles sr ON sr.symbol_id = s.id
                WHERE s.repository_id = :repositoryId
                  AND lower(s.qualified_name) LIKE :pattern
                GROUP BY s.id, sf.relative_path
                ORDER BY CASE WHEN lower(s.simple_name) = :query THEN 0 ELSE 1 END,
                         s.qualified_name
                LIMIT 100
                """, Map.of(
                        "repositoryId", repositoryId,
                        "query", normalized,
                        "pattern", "%" + normalized + "%"),
                (row, number) -> {
                    String roles = row.getString("roles");
                    return new SymbolView(
                            row.getObject("id", UUID.class),
                            row.getString("kind"),
                            row.getString("simple_name"),
                            row.getString("qualified_name"),
                            row.getString("signature"),
                            row.getString("visibility"),
                            row.getString("relative_path"),
                            row.getInt("start_line"),
                            row.getInt("end_line"),
                            roles.isBlank() ? List.of() : List.of(roles.split(",")));
                });
    }

    public List<SymbolRelationshipView> callers(
            UUID repositoryId, UUID symbolId, boolean testsOnly) {
        return relationships(repositoryId, symbolId, true, testsOnly);
    }

    public List<SymbolRelationshipView> callees(UUID repositoryId, UUID symbolId) {
        return relationships(repositoryId, symbolId, false, false);
    }

    private List<SymbolRelationshipView> relationships(
            UUID repositoryId,
            UUID symbolId,
            boolean incoming,
            boolean testsOnly) {
        String selectedSymbol = incoming ? "r.source_symbol_id" : "r.target_symbol_id";
        String predicate = incoming ? "r.target_symbol_id" : "r.source_symbol_id";
        String testFilter = testsOnly ? "AND r.kind = 'TESTS'" : "";
        String sql = """
                SELECT r.id AS relationship_id, related.id AS symbol_id,
                       related.qualified_name, r.kind, r.confidence,
                       r.resolution_method, sf.relative_path,
                       r.source_line, r.evidence_text
                FROM code_relationships r
                JOIN code_symbols related ON related.id = %s
                JOIN source_files sf ON sf.id = r.source_file_id
                WHERE r.repository_id = :repositoryId AND %s = :symbolId
                %s
                ORDER BY r.confidence DESC, related.qualified_name
                LIMIT 500
                """.formatted(selectedSymbol, predicate, testFilter);
        return jdbc.query(sql, Map.of("repositoryId", repositoryId, "symbolId", symbolId),
                (row, number) -> new SymbolRelationshipView(
                        row.getObject("relationship_id", UUID.class),
                        row.getObject("symbol_id", UUID.class),
                        row.getString("qualified_name"),
                        row.getString("kind"),
                        row.getDouble("confidence"),
                        row.getString("resolution_method"),
                        row.getString("relative_path"),
                        row.getInt("source_line"),
                        row.getString("evidence_text")));
    }
}
