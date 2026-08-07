-- Everything in this schema is derived from the mounted source and is rebuilt
-- by an index run, so the history was collapsed into one baseline rather than
-- carried forward. Recreate the volume when this file changes.

CREATE TABLE repositories (
    id UUID PRIMARY KEY,
    display_name VARCHAR(200) NOT NULL,
    relative_path VARCHAR(1000) NOT NULL UNIQUE,
    canonical_path VARCHAR(2000) NOT NULL,
    build_system VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    active_index_run_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    last_indexed_at TIMESTAMPTZ
);

CREATE TABLE index_runs (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    mode VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    phase VARCHAR(30) NOT NULL,
    files_discovered INTEGER NOT NULL DEFAULT 0,
    files_processed INTEGER NOT NULL DEFAULT 0,
    warnings_count INTEGER NOT NULL DEFAULT 0,
    symbols_created INTEGER NOT NULL DEFAULT 0,
    endpoints_created INTEGER NOT NULL DEFAULT 0,
    edges_created INTEGER NOT NULL DEFAULT 0,
    files_added INTEGER NOT NULL DEFAULT 0,
    files_modified INTEGER NOT NULL DEFAULT 0,
    files_deleted INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(100),
    error_summary VARCHAR(2000)
);

ALTER TABLE repositories
    ADD CONSTRAINT repositories_active_run_fk
    FOREIGN KEY (active_index_run_id) REFERENCES index_runs(id);

CREATE INDEX index_runs_repository_started_idx
    ON index_runs(repository_id, started_at DESC);

-- One repository may only have one run queued or running at a time.
CREATE UNIQUE INDEX index_runs_single_active_idx
    ON index_runs (repository_id)
    WHERE status IN ('QUEUED', 'RUNNING');

CREATE TABLE source_files (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    relative_path VARCHAR(2000) NOT NULL,
    source_set VARCHAR(20) NOT NULL,
    module_name VARCHAR(500) NOT NULL,
    package_name VARCHAR(1000),
    content_hash CHAR(64) NOT NULL,
    line_count INTEGER NOT NULL,
    file_size BIGINT NOT NULL,
    observed_in_run_id UUID NOT NULL REFERENCES index_runs(id),
    UNIQUE (repository_id, relative_path)
);

CREATE INDEX source_files_repository_idx ON source_files(repository_id);

CREATE TABLE code_symbols (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    source_file_id UUID NOT NULL REFERENCES source_files(id) ON DELETE CASCADE,
    parent_symbol_id UUID REFERENCES code_symbols(id) ON DELETE CASCADE,
    symbol_key CHAR(64) NOT NULL,
    kind VARCHAR(30) NOT NULL,
    simple_name VARCHAR(500) NOT NULL,
    qualified_name VARCHAR(1500) NOT NULL,
    signature VARCHAR(2000),
    visibility VARCHAR(30) NOT NULL,
    start_line INTEGER NOT NULL,
    end_line INTEGER NOT NULL,
    start_column INTEGER NOT NULL,
    end_column INTEGER NOT NULL,
    is_abstract BOOLEAN NOT NULL DEFAULT FALSE,
    is_static BOOLEAN NOT NULL DEFAULT FALSE,
    observed_in_run_id UUID NOT NULL REFERENCES index_runs(id),
    UNIQUE (repository_id, symbol_key)
);

CREATE INDEX code_symbols_repository_kind_idx
    ON code_symbols(repository_id, kind);
CREATE INDEX code_symbols_qualified_name_idx
    ON code_symbols(repository_id, qualified_name);
CREATE INDEX code_symbols_parent_idx ON code_symbols(parent_symbol_id);

CREATE TABLE code_symbol_roles (
    symbol_id UUID NOT NULL REFERENCES code_symbols(id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL,
    PRIMARY KEY (symbol_id, role)
);

CREATE TABLE http_endpoints (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    controller_method_id UUID NOT NULL REFERENCES code_symbols(id) ON DELETE CASCADE,
    http_method VARCHAR(20) NOT NULL,
    path VARCHAR(2000) NOT NULL,
    request_type VARCHAR(1500),
    response_type VARCHAR(1500),
    observed_in_run_id UUID NOT NULL REFERENCES index_runs(id),
    UNIQUE (repository_id, controller_method_id, http_method, path)
);

CREATE INDEX http_endpoints_repository_path_idx
    ON http_endpoints(repository_id, path);

CREATE TABLE index_warnings (
    id UUID PRIMARY KEY,
    index_run_id UUID NOT NULL REFERENCES index_runs(id) ON DELETE CASCADE,
    source_file_id UUID REFERENCES source_files(id) ON DELETE CASCADE,
    category VARCHAR(100) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    source_line INTEGER
);

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

-- Graph traversal inspects these rows once per visited symbol.
CREATE INDEX unresolved_relationships_repository_source_idx
    ON unresolved_relationships (repository_id, source_symbol_id);
CREATE INDEX external_references_repository_source_idx
    ON external_references (repository_id, source_symbol_id);
