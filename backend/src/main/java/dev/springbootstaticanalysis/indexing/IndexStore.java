package dev.springbootstaticanalysis.indexing;

import dev.springbootstaticanalysis.analysis.RepositoryAnalysis;
import dev.springbootstaticanalysis.shared.ConflictException;
import dev.springbootstaticanalysis.shared.NotFoundException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
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
        try {
            jdbc.update("""
                    INSERT INTO index_runs (
                        id, repository_id, mode, status, phase, started_at
                    ) VALUES (:id, :repositoryId, :mode, 'QUEUED', 'QUEUED', :startedAt)
                    """, new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("repositoryId", repositoryId)
                    .addValue("mode", mode.name())
                    .addValue("startedAt", Timestamp.from(now)));
        } catch (DuplicateKeyException exception) {
            // The count guard can race under READ COMMITTED. The partial unique
            // index is authoritative when two requests insert concurrently.
            throw new ConflictException("Repository already has an active index run");
        }
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
    public void complete(UUID repositoryId, UUID runId, RepositoryAnalysis analysis) {
        jdbc.update("DELETE FROM source_files WHERE repository_id = :repositoryId",
                Map.of("repositoryId", repositoryId));
        persistSourceFiles(repositoryId, runId, analysis);
        persistSymbols(repositoryId, runId, analysis);
        finish(repositoryId, runId, analysis, analysis.files().size(), new ChangeSummary(
                Set.of(), Set.of(), Set.of(), Set.of()));
    }

    @Transactional
    public void completeIncremental(
            UUID repositoryId,
            UUID runId,
            RepositoryAnalysis analysis,
            ChangeSummary changes,
            int totalDiscovered) {
        Set<String> removedPaths = new java.util.HashSet<>(changes.affected());
        removedPaths.addAll(changes.deleted());
        if (!removedPaths.isEmpty()) {
            jdbc.update("""
                    DELETE FROM source_files
                    WHERE repository_id = :repositoryId
                      AND relative_path IN (:paths)
                    """, Map.of("repositoryId", repositoryId, "paths", removedPaths));
        }
        persistSourceFiles(repositoryId, runId, analysis);
        persistSymbols(repositoryId, runId, analysis);
        finish(repositoryId, runId, analysis, totalDiscovered, changes);
    }

    private void persistSourceFiles(
            UUID repositoryId,
            UUID runId,
            RepositoryAnalysis analysis) {
        for (DiscoveredSourceFile file : analysis.files()) {
            jdbc.update("""
                    INSERT INTO source_files (
                        id, repository_id, relative_path, source_set, module_name,
                        content_hash, line_count, file_size, package_name, observed_in_run_id
                    ) VALUES (
                        :id, :repositoryId, :relativePath, :sourceSet, :moduleName,
                        :contentHash, :lineCount, :fileSize, :packageName, :runId
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
                    .addValue("packageName", analysis.packages().get(file.id()))
                    .addValue("runId", runId));
        }
    }

    private void finish(
            UUID repositoryId,
            UUID runId,
            RepositoryAnalysis analysis,
            int totalDiscovered,
            ChangeSummary changes) {
        Instant completed = Instant.now();
        jdbc.update("""
                UPDATE index_runs SET status = 'COMPLETE', phase = 'COMPLETE',
                    files_discovered = :filesDiscovered,
                    files_processed = :filesProcessed, completed_at = :completedAt,
                    warnings_count = :warnings, symbols_created = :symbols,
                    endpoints_created = :endpoints, edges_created = :edges,
                    files_added = :filesAdded, files_modified = :filesModified,
                    files_deleted = :filesDeleted
                WHERE id = :runId
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("filesDiscovered", totalDiscovered)
                .addValue("filesProcessed", analysis.files().size())
                .addValue("warnings", analysis.warnings().size())
                .addValue("symbols", analysis.symbols().size())
                .addValue("endpoints", analysis.endpoints().size())
                .addValue("edges", analysis.relationships().size())
                .addValue("filesAdded", changes.added().size())
                .addValue("filesModified", changes.modified().size())
                .addValue("filesDeleted", changes.deleted().size())
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

    private void persistSymbols(UUID repositoryId, UUID runId, RepositoryAnalysis analysis) {
        for (var symbol : analysis.symbols()) {
            jdbc.update("""
                    INSERT INTO code_symbols (
                        id, repository_id, source_file_id, parent_symbol_id, symbol_key,
                        kind, simple_name, qualified_name, signature, visibility,
                        start_line, end_line, start_column, end_column,
                        is_abstract, is_static, observed_in_run_id
                    ) VALUES (
                        :id, :repositoryId, :sourceFileId, :parentSymbolId, :symbolKey,
                        :kind, :simpleName, :qualifiedName, :signature, :visibility,
                        :startLine, :endLine, :startColumn, :endColumn,
                        :abstractSymbol, :staticSymbol, :runId
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", symbol.id())
                    .addValue("repositoryId", repositoryId)
                    .addValue("sourceFileId", symbol.sourceFileId())
                    .addValue("parentSymbolId", symbol.parentSymbolId())
                    .addValue("symbolKey", symbol.symbolKey())
                    .addValue("kind", symbol.kind().name())
                    .addValue("simpleName", symbol.simpleName())
                    .addValue("qualifiedName", symbol.qualifiedName())
                    .addValue("signature", symbol.signature())
                    .addValue("visibility", symbol.visibility())
                    .addValue("startLine", symbol.startLine())
                    .addValue("endLine", symbol.endLine())
                    .addValue("startColumn", symbol.startColumn())
                    .addValue("endColumn", symbol.endColumn())
                    .addValue("abstractSymbol", symbol.abstractSymbol())
                    .addValue("staticSymbol", symbol.staticSymbol())
                    .addValue("runId", runId));
            for (var role : symbol.roles()) {
                jdbc.update("""
                        INSERT INTO code_symbol_roles (symbol_id, role)
                        VALUES (:symbolId, :role)
                        """, Map.of("symbolId", symbol.id(), "role", role.name()));
            }
        }
        for (var endpoint : analysis.endpoints()) {
            jdbc.update("""
                    INSERT INTO http_endpoints (
                        id, repository_id, controller_method_id, http_method, path,
                        request_type, response_type, observed_in_run_id
                    ) VALUES (
                        :id, :repositoryId, :methodId, :httpMethod, :path,
                        :requestType, :responseType, :runId
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", endpoint.id())
                    .addValue("repositoryId", repositoryId)
                    .addValue("methodId", endpoint.controllerMethodId())
                    .addValue("httpMethod", endpoint.httpMethod())
                    .addValue("path", endpoint.path())
                    .addValue("requestType", endpoint.requestType())
                    .addValue("responseType", endpoint.responseType())
                    .addValue("runId", runId));
        }
        for (var warning : analysis.warnings()) {
            String message = warning.message() == null ? "Unknown analysis warning" : warning.message();
            jdbc.update("""
                    INSERT INTO index_warnings (
                        id, index_run_id, source_file_id, category, message, source_line
                    ) VALUES (:id, :runId, :sourceFileId, :category, :message, :sourceLine)
                    """, new MapSqlParameterSource()
                    .addValue("id", warning.id())
                    .addValue("runId", runId)
                    .addValue("sourceFileId", warning.sourceFileId())
                    .addValue("category", warning.category())
                    .addValue("message", message.substring(0, Math.min(1900, message.length())))
                    .addValue("sourceLine", warning.sourceLine()));
        }
        for (var relationship : analysis.relationships()) {
            jdbc.update("""
                    INSERT INTO code_relationships (
                        id, repository_id, source_symbol_id, target_symbol_id, kind,
                        confidence, resolution_method, source_file_id, source_line,
                        source_column, evidence_text, observed_in_run_id
                    ) VALUES (
                        :id, :repositoryId, :sourceSymbolId, :targetSymbolId, :kind,
                        :confidence, :resolutionMethod, :sourceFileId, :sourceLine,
                        :sourceColumn, :evidenceText, :runId
                    )
                    ON CONFLICT DO NOTHING
                    """, new MapSqlParameterSource()
                    .addValue("id", relationship.id())
                    .addValue("repositoryId", repositoryId)
                    .addValue("sourceSymbolId", relationship.sourceSymbolId())
                    .addValue("targetSymbolId", relationship.targetSymbolId())
                    .addValue("kind", relationship.kind().name())
                    .addValue("confidence", relationship.confidence())
                    .addValue("resolutionMethod", relationship.resolutionMethod().name())
                    .addValue("sourceFileId", relationship.sourceFileId())
                    .addValue("sourceLine", relationship.sourceLine())
                    .addValue("sourceColumn", relationship.sourceColumn())
                    .addValue("evidenceText", relationship.evidenceText().substring(
                            0, Math.min(1900, relationship.evidenceText().length())))
                    .addValue("runId", runId));
        }
        for (var unresolved : analysis.unresolved()) {
            jdbc.update("""
                    INSERT INTO unresolved_relationships (
                        id, repository_id, source_symbol_id, source_file_id, expression,
                        expected_kind, source_line, failure_reason, candidate_count,
                        observed_in_run_id
                    ) VALUES (
                        :id, :repositoryId, :sourceSymbolId, :sourceFileId, :expression,
                        :expectedKind, :sourceLine, :failureReason, :candidateCount, :runId
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", unresolved.id())
                    .addValue("repositoryId", repositoryId)
                    .addValue("sourceSymbolId", unresolved.sourceSymbolId())
                    .addValue("sourceFileId", unresolved.sourceFileId())
                    .addValue("expression", unresolved.expression().substring(
                            0, Math.min(1900, unresolved.expression().length())))
                    .addValue("expectedKind", unresolved.expectedKind().name())
                    .addValue("sourceLine", unresolved.sourceLine())
                    .addValue("failureReason", unresolved.failureReason())
                    .addValue("candidateCount", unresolved.candidateCount())
                    .addValue("runId", runId));
        }
        for (var reference : analysis.externalReferences()) {
            jdbc.update("""
                    INSERT INTO external_references (
                        id, repository_id, source_symbol_id, source_file_id,
                        display_name, source_line, source_column, observed_in_run_id
                    ) VALUES (
                        :id, :repositoryId, :sourceSymbolId, :sourceFileId,
                        :displayName, :sourceLine, :sourceColumn, :runId
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", reference.id())
                    .addValue("repositoryId", repositoryId)
                    .addValue("sourceSymbolId", reference.sourceSymbolId())
                    .addValue("sourceFileId", reference.sourceFileId())
                    .addValue("displayName", reference.displayName().substring(
                            0, Math.min(1400, reference.displayName().length())))
                    .addValue("sourceLine", reference.sourceLine())
                    .addValue("sourceColumn", reference.sourceColumn())
                    .addValue("runId", runId));
        }
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
                "SELECT * FROM index_runs WHERE id = :id",
                Map.of("id", id),
                this::map);
        if (rows.isEmpty()) {
            throw new NotFoundException("Index run " + id + " does not exist");
        }
        return rows.getFirst();
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
                row.getInt("symbols_created"),
                row.getInt("endpoints_created"),
                row.getInt("edges_created"),
                row.getInt("files_added"),
                row.getInt("files_modified"),
                row.getInt("files_deleted"),
                row.getTimestamp("started_at").toInstant(),
                completed == null ? null : completed.toInstant(),
                row.getString("error_code"),
                row.getString("error_summary"));
    }
}
