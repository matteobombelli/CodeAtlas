package dev.codeatlas.indexing;

import dev.codeatlas.shared.ConflictException;
import dev.codeatlas.shared.NotFoundException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class IndexStore {

    private final NamedParameterJdbcTemplate jdbc;

    public IndexStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public IndexRun create(UUID repositoryId, IndexMode mode) {
        Integer active = jdbc.queryForObject("""
                SELECT count(*) FROM index_runs
                WHERE repository_id = :repositoryId AND status IN ('QUEUED', 'RUNNING')
                """, Map.of("repositoryId", repositoryId), Integer.class);
        if (active != null && active > 0) {
            throw new ConflictException("Repository already has an active index run");
        }

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO index_runs (
                    id, repository_id, mode, status, phase, started_at
                ) VALUES (:id, :repositoryId, :mode, 'QUEUED', 'QUEUED', :startedAt)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("repositoryId", repositoryId)
                .addValue("mode", mode.name())
                .addValue("startedAt", Timestamp.from(now)));
        jdbc.update("""
                UPDATE repositories SET status = 'INDEXING' WHERE id = :repositoryId
                """, Map.of("repositoryId", repositoryId));
        return get(id);
    }

    public void phase(UUID runId, IndexPhase phase, int discovered, int processed) {
        jdbc.update("""
                UPDATE index_runs
                SET status = 'RUNNING', phase = :phase,
                    files_discovered = :discovered, files_processed = :processed
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", runId)
                .addValue("phase", phase.name())
                .addValue("discovered", discovered)
                .addValue("processed", processed));
    }

    @Transactional
    public void complete(UUID repositoryId, UUID runId, List<DiscoveredSourceFile> files) {
        jdbc.update("DELETE FROM source_files WHERE repository_id = :repositoryId",
                Map.of("repositoryId", repositoryId));
        for (DiscoveredSourceFile file : files) {
            jdbc.update("""
                    INSERT INTO source_files (
                        id, repository_id, relative_path, source_set, module_name,
                        content_hash, line_count, file_size, observed_in_run_id
                    ) VALUES (
                        :id, :repositoryId, :relativePath, :sourceSet, :moduleName,
                        :contentHash, :lineCount, :fileSize, :runId
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", file.id())
                    .addValue("repositoryId", repositoryId)
                    .addValue("relativePath", file.relativePath())
                    .addValue("sourceSet", file.sourceSet())
                    .addValue("moduleName", file.moduleName())
                    .addValue("contentHash", file.contentHash())
                    .addValue("lineCount", file.lineCount())
                    .addValue("fileSize", file.fileSize())
                    .addValue("runId", runId));
        }
        Instant completed = Instant.now();
        jdbc.update("""
                UPDATE index_runs SET status = 'COMPLETE', phase = 'COMPLETE',
                    files_processed = files_discovered, completed_at = :completedAt
                WHERE id = :runId
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("completedAt", Timestamp.from(completed)));
        jdbc.update("""
                UPDATE repositories
                SET status = 'READY', active_index_run_id = :runId, last_indexed_at = :completedAt
                WHERE id = :repositoryId
                """, new MapSqlParameterSource()
                .addValue("repositoryId", repositoryId)
                .addValue("runId", runId)
                .addValue("completedAt", Timestamp.from(completed)));
    }

    @Transactional
    public void fail(UUID repositoryId, UUID runId, String code, String summary) {
        Instant completed = Instant.now();
        jdbc.update("""
                UPDATE index_runs SET status = 'FAILED', phase = 'FAILED',
                    error_code = :code, error_summary = :summary, completed_at = :completedAt
                WHERE id = :runId
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("code", code)
                .addValue("summary", summary == null ? "Indexing failed" : summary.substring(0, Math.min(1900, summary.length())))
                .addValue("completedAt", Timestamp.from(completed)));
        jdbc.update("""
                UPDATE repositories SET status = CASE
                    WHEN active_index_run_id IS NULL THEN 'FAILED' ELSE 'READY' END
                WHERE id = :repositoryId
                """, Map.of("repositoryId", repositoryId));
    }

    @Transactional
    public void recoverInterrupted() {
        Instant completed = Instant.now();
        jdbc.update("""
                UPDATE index_runs SET status = 'FAILED', phase = 'FAILED',
                    error_code = 'PROCESS_INTERRUPTED',
                    error_summary = 'Backend stopped before indexing completed',
                    completed_at = :completedAt
                WHERE status IN ('QUEUED', 'RUNNING')
                """, Map.of("completedAt", Timestamp.from(completed)));
        jdbc.update("""
                UPDATE repositories SET status = CASE
                    WHEN active_index_run_id IS NULL THEN 'FAILED' ELSE 'READY' END
                WHERE status = 'INDEXING'
                """, Map.of());
    }

    public IndexRun get(UUID id) {
        List<IndexRun> rows = jdbc.query(
                INDEX_SELECT + " WHERE id = :id",
                Map.of("id", id),
                this::map);
        if (rows.isEmpty()) {
            throw new NotFoundException("Index run " + id + " does not exist");
        }
        return rows.getFirst();
    }

    public List<IndexRun> list(UUID repositoryId) {
        return jdbc.query(
                INDEX_SELECT + " WHERE repository_id = :repositoryId ORDER BY started_at DESC LIMIT 100",
                Map.of("repositoryId", repositoryId),
                this::map);
    }

    private IndexRun map(java.sql.ResultSet row, int rowNumber) throws java.sql.SQLException {
        Timestamp completed = row.getTimestamp("completed_at");
        return new IndexRun(
                row.getObject("id", UUID.class),
                row.getObject("repository_id", UUID.class),
                IndexMode.valueOf(row.getString("mode")),
                IndexStatus.valueOf(row.getString("status")),
                IndexPhase.valueOf(row.getString("phase")),
                row.getInt("files_discovered"),
                row.getInt("files_processed"),
                row.getInt("warnings_count"),
                row.getTimestamp("started_at").toInstant(),
                completed == null ? null : completed.toInstant(),
                row.getString("error_code"),
                row.getString("error_summary"));
    }

    private static final String INDEX_SELECT = "SELECT * FROM index_runs";
}
