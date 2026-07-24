package dev.codeatlas.source;

import dev.codeatlas.shared.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SourceStore {

    private final NamedParameterJdbcTemplate jdbc;

    public SourceStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public IndexedSourceFile get(UUID repositoryId, String relativePath) {
        List<IndexedSourceFile> rows = jdbc.query("""
                SELECT relative_path, content_hash, line_count, file_size
                FROM source_files
                WHERE repository_id = :repositoryId AND relative_path = :relativePath
                """, Map.of("repositoryId", repositoryId, "relativePath", relativePath),
                (row, number) -> new IndexedSourceFile(
                        row.getString("relative_path"),
                        row.getString("content_hash"),
                        row.getInt("line_count"),
                        row.getLong("file_size")));
        if (rows.isEmpty()) {
            throw new NotFoundException("Source file was not part of the active index");
        }
        return rows.getFirst();
    }
}
