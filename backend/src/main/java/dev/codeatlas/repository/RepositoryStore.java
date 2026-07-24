package dev.codeatlas.repository;

import dev.codeatlas.shared.ConflictException;
import dev.codeatlas.shared.NotFoundException;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RepositoryStore {

    private final NamedParameterJdbcTemplate jdbc;

    public RepositoryStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RegisteredRepository create(
            UUID id,
            String displayName,
            String relativePath,
            Path canonicalPath,
            String branch,
            String headSha,
            boolean dirty,
            BuildSystem buildSystem) {
        Instant now = Instant.now();
        try {
            jdbc.update("""
                    INSERT INTO repositories (
                        id, display_name, relative_path, canonical_path, default_branch,
                        head_sha, dirty, build_system, status, created_at
                    ) VALUES (
                        :id, :displayName, :relativePath, :canonicalPath, :branch,
                        :headSha, :dirty, :buildSystem, 'REGISTERED', :createdAt
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("displayName", displayName)
                    .addValue("relativePath", relativePath)
                    .addValue("canonicalPath", canonicalPath.toString())
                    .addValue("branch", branch)
                    .addValue("headSha", headSha)
                    .addValue("dirty", dirty)
                    .addValue("buildSystem", buildSystem.name())
                    .addValue("createdAt", Timestamp.from(now)));
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Repository path is already registered");
        }
        return get(id);
    }

    public List<RegisteredRepository> list() {
        return jdbc.query(REPOSITORY_SELECT + " ORDER BY r.created_at DESC", Map.of(), this::map);
    }

    public RegisteredRepository get(UUID id) {
        List<RegisteredRepository> results =
                jdbc.query(REPOSITORY_SELECT + " WHERE r.id = :id", Map.of("id", id), this::map);
        if (results.isEmpty()) {
            throw new NotFoundException("Repository " + id + " does not exist");
        }
        return results.getFirst();
    }

    public Path canonicalPath(UUID id) {
        List<String> results = jdbc.query(
                "SELECT canonical_path FROM repositories WHERE id = :id",
                Map.of("id", id),
                (row, number) -> row.getString(1));
        if (results.isEmpty()) {
            throw new NotFoundException("Repository " + id + " does not exist");
        }
        return Path.of(results.getFirst());
    }

    public void updateGitState(
            UUID id,
            String branch,
            String headSha,
            boolean dirty) {
        int changed = jdbc.update("""
                UPDATE repositories
                SET default_branch = :branch, head_sha = :headSha, dirty = :dirty
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("branch", branch)
                .addValue("headSha", headSha)
                .addValue("dirty", dirty));
        if (changed == 0) {
            throw new NotFoundException("Repository " + id + " does not exist");
        }
    }

    @Transactional
    public void delete(UUID id) {
        jdbc.update(
                "UPDATE repositories SET active_index_run_id = NULL WHERE id = :id",
                Map.of("id", id));
        int changed = jdbc.update("DELETE FROM repositories WHERE id = :id", Map.of("id", id));
        if (changed == 0) {
            throw new NotFoundException("Repository " + id + " does not exist");
        }
    }

    private RegisteredRepository map(java.sql.ResultSet row, int rowNumber)
            throws java.sql.SQLException {
        Timestamp lastIndexed = row.getTimestamp("last_indexed_at");
        return new RegisteredRepository(
                row.getObject("id", UUID.class),
                row.getString("display_name"),
                row.getString("relative_path"),
                row.getString("default_branch"),
                row.getString("head_sha"),
                row.getBoolean("dirty"),
                BuildSystem.valueOf(row.getString("build_system")),
                RepositoryStatus.valueOf(row.getString("status")),
                row.getObject("active_index_run_id", UUID.class),
                row.getTimestamp("created_at").toInstant(),
                lastIndexed == null ? null : lastIndexed.toInstant(),
                row.getInt("source_file_count"));
    }

    private static final String REPOSITORY_SELECT = """
            SELECT r.*, (
                SELECT count(*) FROM source_files sf WHERE sf.repository_id = r.id
            ) AS source_file_count
            FROM repositories r
            """;
}
