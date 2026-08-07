-- Resolve any historical duplicate active runs before enforcing the invariant.
WITH ranked_active_runs AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY repository_id
               ORDER BY started_at DESC, id
           ) AS position
    FROM index_runs
    WHERE status IN ('QUEUED', 'RUNNING')
)
UPDATE index_runs
SET status = 'FAILED',
    phase = 'FAILED',
    completed_at = now(),
    error_code = 'SUPERSEDED_ACTIVE_RUN',
    error_summary = 'Superseded while enforcing one active index run per repository'
WHERE id IN (
    SELECT id FROM ranked_active_runs WHERE position > 1
);

CREATE UNIQUE INDEX index_runs_single_active_idx
    ON index_runs (repository_id)
    WHERE status IN ('QUEUED', 'RUNNING');

-- Graph queries inspect these rows once per visited symbol.
CREATE INDEX unresolved_relationships_repository_source_idx
    ON unresolved_relationships (repository_id, source_symbol_id);

CREATE INDEX external_references_repository_source_idx
    ON external_references (repository_id, source_symbol_id);
