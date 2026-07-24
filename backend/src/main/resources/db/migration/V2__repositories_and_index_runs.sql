CREATE TABLE repositories (
    id UUID PRIMARY KEY,
    display_name VARCHAR(200) NOT NULL,
    relative_path VARCHAR(1000) NOT NULL UNIQUE,
    canonical_path VARCHAR(2000) NOT NULL,
    default_branch VARCHAR(300),
    head_sha VARCHAR(64),
    dirty BOOLEAN NOT NULL DEFAULT FALSE,
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

CREATE TABLE source_files (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    relative_path VARCHAR(2000) NOT NULL,
    source_set VARCHAR(20) NOT NULL,
    module_name VARCHAR(500) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    line_count INTEGER NOT NULL,
    file_size BIGINT NOT NULL,
    observed_in_run_id UUID NOT NULL REFERENCES index_runs(id),
    UNIQUE (repository_id, relative_path)
);

CREATE INDEX source_files_repository_idx ON source_files(repository_id);
