package dev.springbootstaticanalysis.indexing;

import dev.springbootstaticanalysis.analysis.AnalyzedRelationship;
import dev.springbootstaticanalysis.analysis.AnalyzedSymbol;
import dev.springbootstaticanalysis.analysis.RelationshipKind;
import dev.springbootstaticanalysis.analysis.ResolutionMethod;
import dev.springbootstaticanalysis.analysis.SymbolKind;
import dev.springbootstaticanalysis.analysis.SymbolRole;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisSnapshotStore {

    private final NamedParameterJdbcTemplate jdbc;

    public AnalysisSnapshotStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, String> hashes(UUID repositoryId) {
        Map<String, String> hashes = new LinkedHashMap<>();
        jdbc.query("""
                SELECT relative_path, content_hash FROM source_files
                WHERE repository_id = :repositoryId
                """, Map.of("repositoryId", repositoryId), row -> {
            hashes.put(row.getString("relative_path"), row.getString("content_hash"));
        });
        return hashes;
    }

    public Set<String> dependantPaths(UUID repositoryId, Set<String> changedPaths) {
        if (changedPaths.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jdbc.query("""
                SELECT DISTINCT source_file.relative_path
                FROM code_relationships r
                JOIN code_symbols target ON target.id = r.target_symbol_id
                JOIN source_files target_file ON target_file.id = target.source_file_id
                JOIN code_symbols source ON source.id = r.source_symbol_id
                JOIN source_files source_file ON source_file.id = source.source_file_id
                WHERE r.repository_id = :repositoryId
                  AND target_file.relative_path IN (:changedPaths)
                """, Map.of("repositoryId", repositoryId, "changedPaths", changedPaths),
                (row, number) -> row.getString(1)));
    }

    public List<AnalyzedSymbol> symbolsExcluding(
            UUID repositoryId, Set<String> excludedPaths) {
        String exclusion = excludedPaths.isEmpty()
                ? ""
                : "AND sf.relative_path NOT IN (:excludedPaths)";
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("repositoryId", repositoryId);
        parameters.put("excludedPaths", excludedPaths.isEmpty() ? Set.of("") : excludedPaths);
        return jdbc.query("""
                SELECT s.*, sf.relative_path,
                       COALESCE(string_agg(sr.role, ',' ORDER BY sr.role), '') AS roles
                FROM code_symbols s
                JOIN source_files sf ON sf.id = s.source_file_id
                LEFT JOIN code_symbol_roles sr ON sr.symbol_id = s.id
                WHERE s.repository_id = :repositoryId
                %s
                GROUP BY s.id, sf.relative_path
                """.formatted(exclusion), parameters, (row, number) -> {
            EnumSet<SymbolRole> roles = EnumSet.noneOf(SymbolRole.class);
            String roleText = row.getString("roles");
            if (!roleText.isBlank()) {
                for (String role : roleText.split(",")) {
                    roles.add(SymbolRole.valueOf(role));
                }
            }
            return new AnalyzedSymbol(
                    row.getObject("id", UUID.class),
                    row.getObject("source_file_id", UUID.class),
                    row.getObject("parent_symbol_id", UUID.class),
                    row.getString("symbol_key"),
                    SymbolKind.valueOf(row.getString("kind")),
                    row.getString("simple_name"),
                    row.getString("qualified_name"),
                    row.getString("signature"),
                    row.getString("visibility"),
                    row.getInt("start_line"),
                    row.getInt("end_line"),
                    row.getInt("start_column"),
                    row.getInt("end_column"),
                    row.getBoolean("is_abstract"),
                    row.getBoolean("is_static"),
                    roles);
        });
    }

    public List<AnalyzedRelationship> managedEntityRelationships(UUID repositoryId) {
        return jdbc.query("""
                SELECT * FROM code_relationships
                WHERE repository_id = :repositoryId AND kind = 'MANAGES_ENTITY'
                """, Map.of("repositoryId", repositoryId),
                (row, number) -> new AnalyzedRelationship(
                        row.getObject("id", UUID.class),
                        row.getObject("source_symbol_id", UUID.class),
                        row.getObject("target_symbol_id", UUID.class),
                        RelationshipKind.MANAGES_ENTITY,
                        row.getDouble("confidence"),
                        ResolutionMethod.valueOf(row.getString("resolution_method")),
                        row.getObject("source_file_id", UUID.class),
                        row.getInt("source_line"),
                        row.getInt("source_column"),
                        row.getString("evidence_text")));
    }
}
