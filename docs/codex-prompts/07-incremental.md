# Milestone 7 prompt — Git and incremental index

Add file-level Git statistics and hash-based incremental rescanning.

Aggregate history in one JGit walk. Detect added, modified, and deleted source
paths; include direct dependants; cap invalidation and fall back to broad
re-analysis. Recheck the snapshot before an atomic commit and preserve the prior
active graph on failure.

Acceptance: one-file changes reparse only the affected set, unchanged rescans
parse zero files, broad public changes trigger fallback, and the UI reports
processed and changed-file counts.
