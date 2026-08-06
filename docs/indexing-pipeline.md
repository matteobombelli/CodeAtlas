# Indexing pipeline

## Full index

1. Resolve a registered path beneath the configured repository root.
2. Discover conventional `src/main/java` and `src/test/java` files while
   excluding build, generated, IDE, dependency, and VCS directories.
3. Hash each accepted file and enforce file-count and file-size limits.
4. Parse Java 21 syntax and persist parse warnings without abandoning other files.
5. Extract source-located symbols, Spring roles, and combined HTTP mappings.
6. Resolve structural, call, test, and Spring Data/entity relationships.
7. Re-check the complete source snapshot.
8. Replace the active graph in one database transaction.

Imported repositories are treated as data. Spring Boot Static Analysis never invokes their
wrapper, build tool, scripts, annotation processors, or application code.

## Incremental index

The current source hashes are compared with the active index. Added, modified,
and deleted paths form the initial invalidation set. Direct dependant source
files are added, then affected files are reparsed and their outgoing graph data
is replaced atomically.

If invalidation exceeds the smaller of 500 files or one quarter of the
repository, the run performs broad re-analysis. This avoids presenting a narrow
incremental result after changes to widely used public types.

Before committing, discovery and hashing run again. Any added, deleted, or
modified Java source fails the run with `REPOSITORY_CHANGED_DURING_INDEX`; the
previous completed graph remains active.

## Failure semantics

Parse problems become warnings. Unsupported expressions become unresolved or
external diagnostics. Infrastructure failures fail the run. On startup, stale
running jobs are marked `PROCESS_INTERRUPTED`.
