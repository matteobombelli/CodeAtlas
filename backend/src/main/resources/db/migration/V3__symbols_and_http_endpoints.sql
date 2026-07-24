ALTER TABLE source_files ADD COLUMN package_name VARCHAR(1000);
ALTER TABLE index_runs ADD COLUMN symbols_created INTEGER NOT NULL DEFAULT 0;
ALTER TABLE index_runs ADD COLUMN endpoints_created INTEGER NOT NULL DEFAULT 0;

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
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
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
