# Milestone 1 prompt — repository lifecycle

Implement safe local repository registration and the index-run lifecycle.

Accept only paths relative to a configured root. Canonicalize paths, reject
symlink escapes, read Git branch/commit/dirty state with JGit, detect Maven or
Gradle without executing either, discover conventional Java source files, hash
them, and expose real background-job phases through REST and the UI.

Acceptance: a mounted Git repository can be created, listed, removed, queued,
and safely counted; stale running jobs fail on restart; path-boundary and
discovery tests pass.
