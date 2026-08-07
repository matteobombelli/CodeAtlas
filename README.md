# Spring Boot Static Analysis

An experiment in static code navigation and change-impact analysis for Spring
Boot repositories.

Spring Boot Static Analysis reads a local repository and connects REST endpoints
to controller methods, application calls, Spring Data access, JPA entities,
tests, and source evidence. Search starts from an endpoint, named callable, or
indexed Java file.
Callable results cover functions, methods, and constructors. Each edge
records its confidence and resolution evidence. Ambiguous calls stay visible as
diagnostics.

## What it reveals

For indexed code, Spring Boot Static Analysis can show:

- controller-to-service and service-to-repository calls;
- entity reads and writes inferred from Spring Data methods;
- constructor injection and interface relationships;
- directly related tests and reverse change-impact paths;
- exact source ranges and the expression supporting each edge;
- unresolved, ambiguous, and external calls;
- grouped search for endpoints, functions and methods, and files;
- bounded dependency and blast-radius views for callable symbols.

Graph queries have explicit depth, node, and edge limits. The default view omits
framework plumbing and does not try to render an entire repository at once.

## Run the self-analysis demo

Requirements: Docker with Docker Compose.

```bash
docker compose up --build
```

Open <http://localhost:3000>. On a clean database, Compose mounts this checkout
read-only, registers it as **Spring Boot Static Analysis source**, and indexes it
in the background. The frontend opens the self-analysis graph as soon as the
index is ready, starting at `GET /api/repositories/{repositoryId}/search`.

The independent `demo-app` Gradle project is **Analysis Tasks**, a small Spring
Boot issue tracker containing projects, issues, assignment, notifications,
comments, derived repository queries, and integration tests. It gives the
analyzer a compact, manually verifiable target inside the self-analysis
repository.

Useful commands:

```bash
make start
make stop
make test
make clean
```

## Public deployment

The supported public configuration is the read-only production overlay. Start
it on a clean host with a strong, randomly generated database password:

```bash
export SPRING_BOOT_STATIC_ANALYSIS_DB_PASSWORD='<strong-random-value>'
export SPRING_BOOT_STATIC_ANALYSIS_BASE_PATH='/'
docker compose -f compose.yaml -f compose.prod.yaml up -d --build --wait
```

The frontend listens only on `127.0.0.1:3000` by default. Put an HTTPS reverse proxy in
front of that address and keep the backend and PostgreSQL off the host network.
Set `SPRING_BOOT_STATIC_ANALYSIS_FRONTEND_PORT` before starting Compose if the
proxy needs a different loopback port.
If the site lives below a prefix such as `/projects/spring-boot-static-analysis/`,
set `SPRING_BOOT_STATIC_ANALYSIS_BASE_PATH` to that exact trailing-slash path and
configure the proxy to strip the prefix before forwarding.

The overlay sets `SPRING_BOOT_STATIC_ANALYSIS_READ_ONLY=true`. The backend then
rejects every non-GET/HEAD/OPTIONS request under `/api/` with `405`, while the
frontend hides project registration and indexing controls. Configure TLS,
request limits, access-log retention, and monitoring at the reverse proxy.

Back up the named PostgreSQL volume regularly. A portable logical backup can be
created with:

```bash
docker compose -f compose.yaml -f compose.prod.yaml exec -T postgres \
  pg_dump -U spring_boot_static_analysis -Fc spring_boot_static_analysis \
  > spring-boot-static-analysis.dump
```

Test restoring that dump before relying on it. Do not reuse the local demo
volume in production; the overlay defaults to the separate
`spring-boot-static-analysis-prod-postgres` volume.

## Development

Requirements:

- Java 21
- Node.js 22
- PostgreSQL 17, or Docker for Testcontainers

Run the Java tests:

```bash
./gradlew test
```

Run the frontend:

```bash
cd frontend
npm ci
npm run dev
```

The backend expects PostgreSQL at `localhost:5432` by default. Repository API
paths are always relative to
`SPRING_BOOT_STATIC_ANALYSIS_REPOSITORIES_ROOT`; imported projects are never
built or executed.

## Architecture

Spring Boot Static Analysis is a Spring Boot modular monolith with a React
frontend and PostgreSQL graph storage. Indexing is a bounded background job:

```text
discover → hash → parse → resolve → atomic commit
```

Full indexing builds a fresh graph. Incremental indexing compares content hashes,
reparses changed files and direct dependants, and falls back to broad
re-resolution when invalidation exceeds a safety cap. A commit checks the source
snapshot again so a changing working tree is never presented as current.

See [architecture](docs/architecture.md), [graph model](docs/graph-model.md),
[indexing pipeline](docs/indexing-pipeline.md), and
[resolution confidence](docs/resolution-confidence.md).

## Measured development smoke run

On the development checkout used for v0.1:

| Operation | Result |
| --- | ---: |
| Full self-index | 101 Java files, 432 symbols, and 249 edges in 0.85 s |
| Earlier one-file incremental edit | 2 of 84 files reparsed in about 0.09 s |
| Earlier unchanged rescan | 0 of 84 files reparsed in about 0.08 s |

These are smoke measurements, not cross-machine benchmark claims. Results vary
with repository shape, hardware, storage, and container runtime.

## Accuracy model

- **Exact:** one project declaration matches the call/type evidence.
- **Inferred:** a controlled Spring or naming convention supports the edge.
- **Ambiguous:** more than one project target remains possible.
- **Unresolved:** no supported target could be established.

Confidence is attached to individual relationships, not reported as a vague
repository-wide “accuracy” score.

## Current limitations

v0.1 targets conventional Java/Spring Boot source layouts. It does not execute
Gradle or Maven, download dependency models, inspect bytecode, consume runtime
traces, or promise complete reflection/lambda/proxy resolution. Kotlin, raw-SQL
table inference, and messaging paths are out of scope.
See [limitations](docs/limitations.md) for the full boundary.

## Repository layout

- `backend`: Spring Boot API, indexing engine, graph queries, and Flyway schema
- `frontend`: React, React Flow, ELK layout, and source/evidence inspector
- `demo-app`: independent Analysis Tasks Spring Boot analysis target
- `docs`: architecture, confidence model, demo script, and ADRs

## License

[MIT](LICENSE). See [third-party notices](THIRD_PARTY_NOTICES.md) for bundled
dependencies with separate licenses.
