# Demo script

1. Run `docker compose up --build` and open <http://localhost:3000>.
2. Wait for **Spring Boot Static Analysis · self-analysis** to become `READY`.
3. Browse endpoints and select
   `POST /api/repositories/{repositoryId}/index`.
4. Follow `RepositoryController.index` into `IndexingService`, parsing,
   relationship analysis, and persistence.
5. Select an edge to read its evidence, confidence, and exact source excerpt.
6. Select `IndexingService.execute` and show its potential blast radius.
7. Inspect related tests and file-level Git history.
8. Make a harmless one-file Java edit and choose **Rescan repository**.
9. Show the processed/total and added/modified/deleted summary.

For a compact domain example, browse the Atlas Tasks endpoints under
`demo-app`, including issue assignment and comments. The two notification
implementations deliberately expose polymorphism rather than pretending every
call is exact.
