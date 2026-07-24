# Architecture

Code Atlas is a local-first modular monolith.

The React frontend calls a Spring Boot REST API. The backend coordinates
repository discovery, static analysis, graph projection, Git inspection, and
PostgreSQL persistence. Imported repositories are mounted read-only beneath
configured repository roots and are never built or executed.

Backend feature packages communicate through application-layer interfaces.
Persistence adapters remain behind those interfaces. PostgreSQL stores the
normalized code model and serves bounded graph queries; it is not exposed
directly to analysis components.

The frontend is a separate TypeScript application. During development Vite
proxies backend paths; the production Nginx image performs the same routing.

See the architecture decision records in [`docs/adr`](adr/).
