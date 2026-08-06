# ADR 0002: Store the code graph in PostgreSQL

- Status: Accepted
- Date: 2026-07-24

## Decision

Store symbols, relationships, evidence, diagnostics, and repository metadata in
PostgreSQL. Use recursive SQL for bounded traversal.

## Rationale

The graph is relational, query depth is bounded, and PostgreSQL also supports the
job and repository metadata required by the experiment. A second graph database
would add operational complexity before demonstrating the core analysis.

## Consequences

Graph APIs enforce depth, node, and edge limits. Neo4j and a synchronization
pipeline are not part of v0.1.
