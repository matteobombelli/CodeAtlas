# Spring Boot Static Analysis

Reads a Java/Spring Boot project from disk and builds a browsable graph of how
its code connects: HTTP endpoints to controller methods, controllers to
services, services to Spring Data repositories, repositories to JPA entities,
plus the tests that reach them.

Nothing is built or executed. It parses the `.java` files with JavaParser,
stores the result in PostgreSQL, and serves it to a React frontend.

## Run it

Requires Docker with Compose.

```bash
docker compose up --build     # stop with docker compose down
```

Open <http://localhost:3000>. Compose mounts this checkout read-only into the
backend, which registers it as a project and indexes it in the background; the
frontend opens that graph once the index is ready. Later starts rescan
incrementally, so edits you make show up after a restart.

Set `SPRING_BOOT_STATIC_ANALYSIS_FRONTEND_PORT` before starting if port 3000 is
taken.

## Analysing your own project

The backend only opens paths under
`SPRING_BOOT_STATIC_ANALYSIS_REPOSITORIES_ROOT` (`/workspace/repositories` in
the container). Mount your project there by adding a volume to the `backend`
service in `compose.yaml`:

```yaml
    volumes:
      - ./:/workspace/repositories/spring-boot-static-analysis:ro,Z
      - /path/to/my-project:/workspace/repositories/my-project:ro,Z
```

Restart Compose, pick **Add another project…** from the project dropdown, and
enter the path relative to that root (`my-project`). It registers the project
and starts indexing.

## Repository layout

- `backend` — Spring Boot API, parser, indexer, graph queries, Flyway schema
- `frontend` — React app; React Flow + ELK for the graph, source viewer
- `demo-app` — a small standalone Spring Boot issue tracker used as an analysis
  target so the output can be checked by hand
- `docs` — architecture, graph model, indexing pipeline, limitations, ADRs

## Development

Requires Java 21, Node 22, and either PostgreSQL 17 on `localhost:5432` or
Docker (the tests use Testcontainers).

```bash
./gradlew test                 # backend and demo-app tests
./gradlew :backend:bootRun     # backend on :8080

cd frontend
npm ci
npm run dev                    # :5173, proxies /api and /actuator to :8080
npm test                       # vitest
npm run test:e2e               # playwright, needs the stack running
```

Backend configuration lives in `backend/src/main/resources/application.yml`;
every setting there has a `SPRING_BOOT_STATIC_ANALYSIS_*` environment variable
override. The frontend requests its API with paths relative to the page it was
served from, so the same bundle works at a site root or behind a proxy prefix.

The database holds only what an index run derives from the mounted source, so
the schema is one Flyway baseline rather than a migration history. Recreate the
volume (`docker compose down --volumes`) when that baseline changes.

## What the graph shows

Every edge records the source expression it came from and a confidence score
between 0 and 1, shown as **Exact** (a matching declaration in the project) or
**Inferred** (a Spring or naming convention supports it, e.g. a derived Spring
Data query method). See
[docs/resolution-confidence.md](docs/resolution-confidence.md) for the score
per kind of evidence.

Calls with more than one possible target are kept as ambiguous with the
candidate count rather than guessing; calls into libraries become external
terminal references; anything else stays listed as unresolved. Graph queries
are bounded by depth and by node/edge caps (`graph.max-nodes`,
`graph.max-edges` in `application.yml`), so a whole repository is never
rendered at once.

It handles conventional Maven/Gradle Java layouts. It does not run your build,
resolve third-party dependencies, read bytecode, or fully resolve reflection,
proxies, and lambdas. Kotlin, raw SQL, and messaging flows are out of scope —
see [docs/limitations.md](docs/limitations.md).

## Deploying publicly

`compose.prod.yaml` adds a hardened overlay that publishes the frontend twice,
both on loopback, for a TLS reverse proxy to sit in front of:

| Entrance | Default port | Who reaches it | Mutating requests |
| --- | --- | --- | --- |
| public | 3000 | whoever the reverse proxy forwards | rejected with `405` |
| local | 3001 | only someone already on the host | accepted |

```bash
export SPRING_BOOT_STATIC_ANALYSIS_DB_PASSWORD='<random-value>'
docker compose -f compose.yaml -f compose.prod.yaml up -d --build --wait
```

Point the reverse proxy at the public port only, and override
`SPRING_BOOT_STATIC_ANALYSIS_FRONTEND_PORT` or
`SPRING_BOOT_STATIC_ANALYSIS_LOCAL_PORT` if those ports are taken. If the site
is served under a prefix, have the proxy strip it and redirect the bare prefix
to its trailing-slash form.

Adding a project from the local entrance means opening it directly — on the
host, or over an SSH tunnel:

```bash
ssh -N -L 3001:127.0.0.1:3001 user@host   # then open http://localhost:3001
```

Nginx sets the header that marks a request local, and clears it on the public
server block, so a visitor cannot get write access by sending it themselves.

The overlay keeps its database in its own volume, named by
`SPRING_BOOT_STATIC_ANALYSIS_DB_VOLUME`. Point that at a new name when the
Flyway baseline changes; the stack rebuilds the index on the next start.

## License

[MIT](LICENSE). Bundled dependencies are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
