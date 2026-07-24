# Code Atlas

Interactive execution maps and change-impact analysis for Spring Boot repositories.

Code Atlas is a local-first developer tool that connects REST endpoints to the
controllers, services, repositories, entities, tests, and source evidence behind
them. The initial release targets Java 21 Spring Boot repositories and reports
uncertain or unresolved relationships instead of inventing edges.

## Repository layout

- `backend` — Spring Boot API and analysis engine
- `frontend` — React user interface
- `demo-app` — independent Spring Boot application used as an analysis target
- `fixtures` — small analysis inputs with deterministic expected graphs
- `docs` — architecture notes and decisions

## Requirements

- Java 21
- Node.js 22
- Docker with Docker Compose

## Run locally

```bash
docker compose up --build
```

Open <http://localhost:3000>. The empty foundation displays the health of the
backend and PostgreSQL. Repository indexing is introduced in later milestones.

Useful commands:

```bash
make start
make stop
make test
make clean
```

## Current scope

The project is in active development. The first public release will support
local Java/Spring Boot repositories, endpoint execution graphs, source evidence,
tests, entity access, potential blast radius, Git file metadata, and incremental
rescanning.

Imported repositories are treated as untrusted input. Code Atlas never runs
their Gradle, Maven, or repository scripts.

## License

[MIT](LICENSE)
