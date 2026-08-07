# Changelog

All notable changes are documented here. This project follows semantic
versioning.

## Unreleased

- Added a server-enforced read-only mode and a hardened production Compose
  overlay for public portfolio hosting behind an HTTPS reverse proxy.
- Rejected symlinked source files, enforced one active index run per repository,
  and refreshed the bundled self-analysis index after each deployment.
- Enforced configurable graph bounds and unique edge IDs across dependency,
  file, and blast-radius views.
- Added dependency auditing, Dependabot, production smoke coverage, safe indexing
  failure logs, and database query indexes used during graph traversal.

## 0.1.0 - 2026-07-24

- Added safe local project registration and bounded background indexing.
- Added Java symbol, Spring component, HTTP endpoint, call, test, repository,
  and JPA entity analysis with source evidence.
- Added confidence-ranked execution graphs and potential blast-radius traversal.
- Added secure source excerpts and relationship evidence.
- Added hash-based incremental rescanning with dependant invalidation and atomic
  graph replacement.
- Added the React Flow/ELK map UI, Compose self-analysis demo, and Analysis Tasks
  fixture application.

Known limitations are documented in `docs/limitations.md`.
