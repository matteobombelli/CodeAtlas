package dev.sbsa.git;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GitStatsStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public GitStatsStore(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public GitFileHistoryView forSymbol(UUID repositoryId, UUID symbolId) {
        List<GitFileHistoryView> values = jdbc.query("""
                SELECT g.* FROM git_file_stats g
                JOIN code_symbols s ON s.source_file_id = g.source_file_id
                WHERE s.repository_id = :repositoryId AND s.id = :symbolId
                """, Map.of("repositoryId", repositoryId, "symbolId", symbolId),
                (row, number) -> new GitFileHistoryView(
                        row.getInt("total_commits"),
                        row.getInt("commits_last_90_days"),
                        row.getTimestamp("last_modified_at") == null
                                ? null : row.getTimestamp("last_modified_at").toInstant(),
                        row.getString("last_author_name"),
                        row.getString("last_commit_sha"),
                        row.getInt("contributor_count"),
                        readSubjects(row.getString("recent_subjects"))));
        return values.isEmpty()
                ? new GitFileHistoryView(0, 0, null, null, null, 0, List.of())
                : values.getFirst();
    }

    private List<String> readSubjects(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }
}
