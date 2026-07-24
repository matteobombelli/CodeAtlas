CREATE TABLE git_file_stats (
    source_file_id UUID PRIMARY KEY REFERENCES source_files(id) ON DELETE CASCADE,
    total_commits INTEGER NOT NULL,
    commits_last_90_days INTEGER NOT NULL,
    last_modified_at TIMESTAMPTZ,
    last_author_name VARCHAR(500),
    last_commit_sha VARCHAR(64),
    contributor_count INTEGER NOT NULL,
    recent_subjects JSONB NOT NULL DEFAULT '[]'::jsonb
);
