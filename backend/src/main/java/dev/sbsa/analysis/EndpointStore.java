package dev.sbsa.analysis;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EndpointStore {

    private final NamedParameterJdbcTemplate jdbc;

    public EndpointStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<HttpEndpointView> list(UUID repositoryId, String search) {
        String normalizedSearch = search == null ? "" : search.strip().toLowerCase();
        return jdbc.query("""
                SELECT e.id, e.http_method, e.path, e.controller_method_id,
                       parent.qualified_name AS controller,
                       method.simple_name AS method, method.signature,
                       sf.relative_path, method.start_line, method.end_line,
                       e.request_type, e.response_type
                FROM http_endpoints e
                JOIN code_symbols method ON method.id = e.controller_method_id
                JOIN code_symbols parent ON parent.id = method.parent_symbol_id
                JOIN source_files sf ON sf.id = method.source_file_id
                WHERE e.repository_id = :repositoryId
                  AND (:search = '' OR lower(e.path) LIKE :pattern
                       OR lower(parent.qualified_name) LIKE :pattern)
                ORDER BY parent.qualified_name, e.path, e.http_method
                LIMIT 500
                """, Map.of(
                        "repositoryId", repositoryId,
                        "search", normalizedSearch,
                        "pattern", "%" + normalizedSearch + "%"),
                (row, number) -> new HttpEndpointView(
                        row.getObject("id", UUID.class),
                        row.getString("http_method"),
                        row.getString("path"),
                        row.getObject("controller_method_id", UUID.class),
                        row.getString("controller"),
                        row.getString("method"),
                        row.getString("signature"),
                        row.getString("relative_path"),
                        row.getInt("start_line"),
                        row.getInt("end_line"),
                        row.getString("request_type"),
                        row.getString("response_type")));
    }
}
