package dev.codeatlas.search;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CodeSearchStore {

    private static final int RESULT_LIMIT = 20;

    private final NamedParameterJdbcTemplate jdbc;

    public CodeSearchStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CodeSearchResponse search(UUID repositoryId, String query) {
        String normalized = query == null ? "" : query.strip().toLowerCase();
        if (normalized.length() < 2) {
            return new CodeSearchResponse(List.of(), List.of(), List.of());
        }
        Map<String, Object> parameters = Map.of(
                "repositoryId", repositoryId,
                "query", normalized,
                "pattern", "%" + normalized + "%",
                "limit", RESULT_LIMIT);
        return new CodeSearchResponse(
                endpoints(parameters),
                methods(parameters),
                files(parameters));
    }

    private List<CodeSearchResult> endpoints(Map<String, Object> parameters) {
        return jdbc.query("""
                SELECT e.id, e.path AS label,
                       parent.simple_name || '.' || method.simple_name AS detail,
                       sf.relative_path, method.start_line, method.end_line,
                       e.http_method
                FROM http_endpoints e
                JOIN code_symbols method ON method.id = e.controller_method_id
                JOIN code_symbols parent ON parent.id = method.parent_symbol_id
                JOIN source_files sf ON sf.id = method.source_file_id
                WHERE e.repository_id = :repositoryId
                  AND (lower(e.path) LIKE :pattern
                       OR lower(e.http_method) LIKE :pattern
                       OR lower(method.simple_name) LIKE :pattern
                       OR lower(parent.qualified_name) LIKE :pattern
                       OR lower(sf.relative_path) LIKE :pattern)
                ORDER BY CASE WHEN lower(e.path) = :query THEN 0 ELSE 1 END,
                         e.path, e.http_method
                LIMIT :limit
                """, parameters, (row, number) -> new CodeSearchResult(
                row.getObject("id", UUID.class),
                row.getString("label"),
                row.getString("detail"),
                row.getString("relative_path"),
                row.getInt("start_line"),
                row.getInt("end_line"),
                row.getString("http_method")));
    }

    private List<CodeSearchResult> methods(Map<String, Object> parameters) {
        return jdbc.query("""
                SELECT s.id,
                       s.simple_name || COALESCE(s.signature, '') AS label,
                       s.qualified_name AS detail,
                       sf.relative_path, s.start_line, s.end_line
                FROM code_symbols s
                JOIN source_files sf ON sf.id = s.source_file_id
                WHERE s.repository_id = :repositoryId
                  AND s.kind = 'METHOD'
                  AND (lower(s.simple_name) LIKE :pattern
                       OR lower(s.qualified_name) LIKE :pattern
                       OR lower(COALESCE(s.signature, '')) LIKE :pattern
                       OR lower(sf.relative_path) LIKE :pattern)
                ORDER BY CASE WHEN lower(s.simple_name) = :query THEN 0 ELSE 1 END,
                         s.qualified_name, s.start_line
                LIMIT :limit
                """, parameters, (row, number) -> new CodeSearchResult(
                row.getObject("id", UUID.class),
                row.getString("label"),
                row.getString("detail"),
                row.getString("relative_path"),
                row.getInt("start_line"),
                row.getInt("end_line"),
                null));
    }

    private List<CodeSearchResult> files(Map<String, Object> parameters) {
        return jdbc.query("""
                SELECT sf.id, sf.relative_path AS label,
                       COALESCE(sf.package_name, sf.module_name, sf.source_set) AS detail,
                       sf.relative_path, sf.line_count
                FROM source_files sf
                WHERE sf.repository_id = :repositoryId
                  AND (lower(sf.relative_path) LIKE :pattern
                       OR lower(COALESCE(sf.package_name, '')) LIKE :pattern)
                ORDER BY CASE
                           WHEN lower(sf.relative_path) = :query THEN 0
                           WHEN lower(sf.relative_path) LIKE :query || '%' THEN 1
                           ELSE 2
                         END,
                         sf.relative_path
                LIMIT :limit
                """, parameters, (row, number) -> new CodeSearchResult(
                row.getObject("id", UUID.class),
                row.getString("label"),
                row.getString("detail"),
                row.getString("relative_path"),
                1,
                row.getInt("line_count"),
                null));
    }
}
