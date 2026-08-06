# Demo script

1. Run `docker compose up --build` and open <http://localhost:3000>.
2. The **Spring Boot Static Analysis source** repository opens when its initial index is ready.
3. Follow `GET /api/repositories/{repositoryId}/search` from its controller into
   the repository and search stores.
4. Search for `execute`. Confirm that functions and methods appear separately
   from endpoints and files.
5. Open `IndexingService.execute` to view its blast radius, including callers,
   tests, and affected endpoints where present.
6. Switch to **Dependencies** to inspect what `execute` calls, then open
   `IndexingService.java` to view its declared callables.
7. Select a node to read relationship evidence, confidence, and source.
8. Make a harmless one-file Java edit, rerun the index through the API, and
   reload the map to review the change.

For a compact domain example, browse the Analysis Tasks endpoints under
`demo-app`, including issue assignment and comments. Its two notification
implementations provide a small polymorphic call site with more than one valid
target.
