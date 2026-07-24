ALTER TABLE index_runs ADD COLUMN edges_created INTEGER NOT NULL DEFAULT 0;

CREATE TABLE code_relationships (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    source_symbol_id UUID NOT NULL REFERENCES code_symbols(id) ON DELETE CASCADE,
    target_symbol_id UUID NOT NULL REFERENCES code_symbols(id) ON DELETE CASCADE,
    kind VARCHAR(40) NOT NULL,
    confidence NUMERIC(4,3) NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    resolution_method VARCHAR(50) NOT NULL,
    source_file_id UUID NOT NULL REFERENCES source_files(id) ON DELETE CASCADE,
    source_line INTEGER NOT NULL,
    source_column INTEGER NOT NULL,
    evidence_text VARCHAR(2000) NOT NULL,
    observed_in_run_id UUID NOT NULL REFERENCES index_runs(id),
    UNIQUE (
        repository_id, source_symbol_id, target_symbol_id, kind,
        source_file_id, source_line, source_column
    )
);

CREATE INDEX code_relationships_source_idx
    ON code_relationships(repository_id, source_symbol_id, kind);
CREATE INDEX code_relationships_target_idx
    ON code_relationships(repository_id, target_symbol_id, kind);

CREATE TABLE unresolved_relationships (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    source_symbol_id UUID NOT NULL REFERENCES code_symbols(id) ON DELETE CASCADE,
    source_file_id UUID NOT NULL REFERENCES source_files(id) ON DELETE CASCADE,
    expression VARCHAR(2000) NOT NULL,
    expected_kind VARCHAR(40) NOT NULL,
    source_line INTEGER NOT NULL,
    failure_reason VARCHAR(100) NOT NULL,
    candidate_count INTEGER NOT NULL DEFAULT 0,
    observed_in_run_id UUID NOT NULL REFERENCES index_runs(id)
);

CREATE TABLE external_references (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    source_symbol_id UUID NOT NULL REFERENCES code_symbols(id) ON DELETE CASCADE,
    source_file_id UUID NOT NULL REFERENCES source_files(id) ON DELETE CASCADE,
    display_name VARCHAR(1500) NOT NULL,
    source_line INTEGER NOT NULL,
    source_column INTEGER NOT NULL,
    observed_in_run_id UUID NOT NULL REFERENCES index_runs(id)
);
