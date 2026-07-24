# Code Atlas

Interactive execution maps and change-impact analysis for Spring Boot repositories.

Code Atlas is a local-first static-analysis tool. Select a REST endpoint to see
the controller, application calls, Spring Data access, JPA entities, tests,
source evidence, Git history, and potential blast radius behind it. Every edge
includes its confidence and resolution evidence; ambiguity is retained as a
diagnostic instead of being silently resolved.

## What it reveals

For an indexed endpoint, Code Atlas can show:

- controller-to-service and service-to-repository calls;
- entity reads and writes inferred from Spring Data methods;
- constructor injection and interface relationships;
- directly related tests and reverse change-impact paths;
- exact source ranges and the expression supporting each edge;
- file-level commit history, churn, authors, and recent subjects;
- unresolved, ambiguous, and external calls.

The graph is deliberately bounded. Its default view prioritizes useful domain
behavior over framework plumbing and never tries to render the entire repository.

## Run the self-analysis demo

Requirements: Docker with Docker Compose.

```bash
docker compose up --build
```

Open <http://localhost:3000>. On a clean database, Compose mounts this checkout
read-only, registers it as **Code Atlas · self-analysis**, and indexes it in the
background. Browse the detected endpoints and open
`POST /api/repositories/{repositoryId}/index` to follow the indexing workflow.

The independent `demo-app` Gradle project is **Atlas Tasks**, a small Spring Boot
issue tracker containing projects, issues, assignment, notifications, comments,
derived repository queries, and integration tests. It gives the analyzer a
compact, manually verifiable target inside the self-analysis repository.

Useful commands:

```bash
make start
make stop
make test
make clean
```

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
paths are always relative to `CODE_ATLAS_REPOSITORIES_ROOT`; imported projects
are never built or executed.

## Architecture

Code Atlas is a Spring Boot modular monolith with a React frontend and
PostgreSQL graph storage. Indexing is a bounded background job:

```text
discover → hash → parse → resolve → analyze Git → atomic commit
```

Full indexing builds a fresh graph. Incremental indexing compares content hashes,
reparses changed files and direct dependants, and falls back to broad
re-resolution when invalidation exceeds a safety cap. A commit checks the source
snapshot again so a changing working tree is never presented as current.

See [architecture](docs/architecture.md), [graph model](docs/graph-model.md),
[indexing pipeline](docs/indexing-pipeline.md), and
[resolution confidence](docs/resolution-confidence.md). Independent
[Codex milestone prompts](docs/codex-prompts/README.md) preserve the scoped
implementation sequence.

## Measured development smoke run

On the development checkout used for v0.1:

| Operation | Result |
| --- | ---: |
| Full self-index | 101 Java files, 432 symbols, and 249 edges in 0.85 s |
| Earlier one-file incremental edit | 2 of 84 files reparsed in about 0.09 s |
| Earlier unchanged rescan | 0 of 84 files reparsed in about 0.08 s |

These are smoke measurements, not cross-machine benchmark claims. Results vary
with repository shape, Git history, hardware, storage, and container runtime.

## Accuracy model

- **Exact** — one project declaration matches the call/type evidence.
- **Inferred** — a controlled Spring or naming convention supports the edge.
- **Ambiguous** — more than one project target remains possible.
- **Unresolved** — no supported target could be established.

Confidence is attached to individual relationships, not reported as a vague
repository-wide “accuracy” score.

## Current limitations

v0.1 targets conventional Java/Spring Boot source layouts. It does not execute
Gradle or Maven, download dependency models, inspect bytecode, consume runtime
traces, or promise complete reflection/lambda/proxy resolution. Kotlin, raw-SQL
table inference, messaging paths, and symbol-level Git lineage are out of scope.
See [limitations](docs/limitations.md) for the full boundary.

## Repository layout

- `backend` — Spring Boot API, indexing engine, graph queries, and Flyway schema
- `frontend` — React, React Flow, ELK layout, and source/evidence inspector
- `demo-app` — independent Atlas Tasks Spring Boot analysis target
- `docs` — architecture, confidence model, demo script, and ADRs

## License

[MIT](LICENSE)
