# Spring Boot Static Analysis

Reads a Java/Spring Boot project from disk and builds a browsable graph of how
its code connects: HTTP endpoints to controller methods, controllers to
services, services to Spring Data repositories, repositories to JPA entities,
plus the tests that reach them.

Nothing is built or executed. It parses the `.java` files with JavaParser,
stores the result in PostgreSQL, and serves it to a React frontend.

## Run it locally

Requires Docker with Compose. From a clone of this repository:

```bash
docker compose up --build
```

Open <http://localhost:3000>. That is the whole setup — Compose starts
PostgreSQL, the backend, and the frontend, mounts this checkout read-only into
the backend, registers it as a project, and indexes it in the background. The
map opens on its own once the first index finishes, which takes a few seconds.

```bash
docker compose down              # stop
docker compose down --volumes    # stop and discard the index
```

Later starts rescan incrementally, so edits you make to this repository show up
after a restart. If port 3000 is taken, set a different one first:

```bash
SPRING_BOOT_STATIC_ANALYSIS_FRONTEND_PORT=3010 docker compose up --build
```

A local run is fully writable: the **Project** dropdown includes **Add another
project…**, and the API accepts registration and indexing requests. A public
deployment is not — see [Deploying publicly](#deploying-publicly).

## Analyse your own project

The backend only opens paths beneath
`SPRING_BOOT_STATIC_ANALYSIS_REPOSITORIES_ROOT`, which is
`/workspace/repositories` inside the container. A project has to be mounted
there before it can be registered; this is what stops the API from reading
arbitrary paths on your machine.

**1. Mount it.** Add a line to the `backend` service's `volumes:` in
`compose.yaml`, mapping a directory on your machine to a name under the
repositories root:

```yaml
    volumes:
      - ./:/workspace/repositories/spring-boot-static-analysis:ro,Z
      - /home/you/code/my-project:/workspace/repositories/my-project:ro,Z
```

Mount a parent directory instead if you want several projects available without
editing Compose again:

```yaml
      - /home/you/code:/workspace/repositories/code:ro,Z
```

**2. Restart.** `docker compose up --build -d` — new mounts need the container
recreated.

**3. Register it.** Open the app, choose **Add another project…** from the
**Project** dropdown, and enter the path *relative to the repositories root* —
`my-project`, or `code/my-project` if you mounted the parent. The last path
segment becomes the display name. It registers the project, starts a full
index, and switches to it; the dropdown then holds every indexed project.

The path must be a readable directory that exists under the root, or
registration is refused. Anything with a conventional Maven or Gradle layout
works — `src/main/java` and `src/test/java` are what get discovered. Kotlin
files, build output, and generated sources are skipped.

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
volume when that baseline changes.

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

`compose.prod.yaml` adds a read-only overlay: the backend answers `405` to
anything under `/api/` that is not a GET, HEAD, or OPTIONS, and the frontend
hides the registration and indexing controls. Visitors browse whichever
projects the deployment itself mounted and indexed; adding a project stays a
local activity.

```bash
export SPRING_BOOT_STATIC_ANALYSIS_DB_PASSWORD='<random-value>'
docker compose -f compose.yaml -f compose.prod.yaml up -d --build --wait
```

Only the frontend is published, on `127.0.0.1`; put an HTTPS reverse proxy in
front of it. If the site is served under a prefix, have the proxy strip it and
redirect the bare prefix to its trailing-slash form.

The overlay keeps its database in its own volume, named by
`SPRING_BOOT_STATIC_ANALYSIS_DB_VOLUME`. Point that at a new name when the
Flyway baseline changes; the stack rebuilds the index on the next start.

## License

[MIT](LICENSE). Bundled dependencies are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
