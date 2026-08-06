# Milestone 0 prompt — foundation

Create the Spring Boot Static Analysis monorepo foundation.

Use Java 21, Spring Boot, Gradle Kotlin DSL, PostgreSQL, Flyway, React,
TypeScript, Vite, and React Flow. Add backend and frontend containers plus
Compose, health reporting for the backend/database, Testcontainers setup, CI,
and an independent Spring Boot `demo-app`. Do not implement indexing.

Acceptance: `docker compose up --build` starts PostgreSQL, backend, and frontend;
the UI reports both health components; backend, frontend, and demo-app tests
pass independently.
