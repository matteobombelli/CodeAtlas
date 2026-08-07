# Spring Boot Static Analysis

Parses a Java/Spring Boot project and shows how its code connects: HTTP
endpoints to controllers, controllers to services, services to repositories,
repositories to JPA entities, plus the tests that reach them.

Nothing is built or executed. It reads `.java` files with JavaParser, stores the
graph in PostgreSQL, and serves it to a React frontend.

## Run it

Needs Docker with Compose.

```bash
docker compose up --build
```

Open <http://localhost:3000>. Compose starts PostgreSQL, the backend, and the
frontend, mounts this checkout read-only into the backend, and indexes it. The
graph appears after a few seconds.

```bash
docker compose down              # stop
docker compose down --volumes    # stop and drop the index
```

Restarts rescan incrementally, so local edits show up after a restart. If port
3000 is taken:

```bash
SPRING_BOOT_STATIC_ANALYSIS_FRONTEND_PORT=3010 docker compose up --build
```

## Analyse another project

The backend only opens paths under `/workspace/repositories`
(`SPRING_BOOT_STATIC_ANALYSIS_REPOSITORIES_ROOT`), so a project must be mounted
there first. This is what keeps the API from reading arbitrary paths.

1. Add it to the `backend` service's `volumes:` in `compose.yaml`:

   ```yaml
       volumes:
         - ./:/workspace/repositories/spring-boot-static-analysis:ro,Z
         - /home/you/code/my-project:/workspace/repositories/my-project:ro,Z
   ```

   Mount a parent directory (`/home/you/code:/workspace/repositories/code:ro,Z`)
   to add several projects without editing Compose again.

2. `docker compose up --build -d`. New mounts need the container recreated.

3. In the app, pick **Add another project…** from the **Project** dropdown and
   enter the path relative to the repositories root: `my-project`, or
   `code/my-project` if you mounted the parent. It registers, indexes, and
   switches to the project.

Conventional Maven and Gradle layouts work; `src/main/java` and `src/test/java`
are what get discovered. Kotlin files, build output, and generated sources are
skipped.

## What the graph shows

Every edge records the source expression it came from and a confidence score
between 0 and 1, shown as **Exact** (a matching declaration in the project) or
**Inferred** (a Spring or naming convention supports it, such as a derived
Spring Data query method). Scores per kind of evidence are in
[docs/resolution-confidence.md](docs/resolution-confidence.md).

Calls with several possible targets are kept as ambiguous with a candidate count
rather than guessed. Calls into libraries become external terminal references;
the rest stay unresolved. Queries are bounded by depth and by `graph.max-nodes`
/ `graph.max-edges`, so a whole repository is never rendered at once.

It does not run your build, resolve third-party dependencies, read bytecode, or
fully resolve reflection, proxies, and lambdas. Kotlin, raw SQL, and messaging
are out of scope. See [docs/limitations.md](docs/limitations.md).

## Layout

- `backend`: Spring Boot API, parser, indexer, graph queries, Flyway schema
- `frontend`: React app, React Flow + ELK for the graph, source viewer
- `demo-app`: small Spring Boot issue tracker used as an analysis target
- `docs`: architecture, graph model, indexing pipeline, limitations, ADRs

## Development

Needs Java 21, Node 22, and either PostgreSQL 17 on `localhost:5432` or Docker
(the tests use Testcontainers).

```bash
./gradlew test                 # backend and demo-app tests
./gradlew :backend:bootRun     # backend on :8080

cd frontend
npm ci
npm run dev                    # :5173, proxies /api and /actuator to :8080
npm test                       # vitest
npm run test:e2e               # playwright, needs the stack running
```

Backend settings live in `backend/src/main/resources/application.yml`; each one
has a `SPRING_BOOT_STATIC_ANALYSIS_*` environment override. The frontend uses
paths relative to the page it was served from, so the same bundle works at a
site root or behind a proxy prefix.

The database holds only what indexing derives from the source, so the schema is
a single Flyway baseline rather than a migration history. Recreate the volume
when that baseline changes.

## Deploying publicly

`compose.prod.yaml` makes the stack read-only: the backend answers `405` to
anything under `/api/` that is not GET, HEAD, or OPTIONS, and the frontend hides
the registration and indexing controls. Visitors browse whatever the deployment
mounted and indexed; adding projects stays local.

```bash
export SPRING_BOOT_STATIC_ANALYSIS_DB_PASSWORD='<random-value>'
docker compose -f compose.yaml -f compose.prod.yaml up -d --build --wait
```

Only the frontend is published, on `127.0.0.1`, so put an HTTPS reverse proxy in
front of it. If the site is served under a prefix, have the proxy strip it and
redirect the bare prefix to its trailing-slash form.

The overlay keeps its database in its own volume, named by
`SPRING_BOOT_STATIC_ANALYSIS_DB_VOLUME`. Point that at a new name when the
Flyway baseline changes.

## License

[MIT](LICENSE). Bundled dependencies are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
