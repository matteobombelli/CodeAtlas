# Architecture

Code Atlas is a local-first modular monolith.

The React frontend calls a Spring Boot REST API. The backend coordinates
repository discovery, static analysis, graph projection, Git inspection, and
PostgreSQL persistence. Imported repositories are mounted read-only beneath
configured repository roots and are never built or executed.

The indexing coordinator drives a staged pipeline and writes a new active graph
atomically. Analysis components consume source snapshots rather than database
rows. Query stores project normalized data into bounded endpoint, source,
symbol, and blast-radius API responses.

```text
React UI
   │ REST
Spring Boot modular monolith
   ├── repository boundary + Git metadata
   ├── indexing coordinator
   ├── Java/Spring relationship analysis
   ├── graph/source query projections
   └── PostgreSQL + Flyway
             │
        read-only Git mount
```

PostgreSQL stores the normalized code model and serves bounded graph queries.
The bounded Java executor runs one CPU-intensive index at a time by default;
there is no distributed job infrastructure.

The frontend is a separate TypeScript application. During development Vite
proxies backend paths; the production Nginx image performs the same routing.

See the architecture decision records in [`docs/adr`](adr/), the
[graph model](graph-model.md), and the [indexing pipeline](indexing-pipeline.md).
