# ADR 0001: Use a modular monolith

- Status: Accepted
- Date: 2026-07-24

## Decision

Build one Spring Boot deployment divided into feature packages with explicit
application interfaces.

## Rationale

Indexing, analysis, persistence, and graph queries need strong boundaries but do
not require independent scaling or distributed coordination. A modular monolith
keeps deployment and transactions simple while retaining testable seams.

## Consequences

Feature packages must not reach directly into another feature's persistence
adapter. Kafka, service discovery, and distributed transactions are excluded.
