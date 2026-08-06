DROP TABLE IF EXISTS git_file_stats;

ALTER TABLE repositories
    DROP COLUMN IF EXISTS default_branch,
    DROP COLUMN IF EXISTS head_sha,
    DROP COLUMN IF EXISTS dirty;
