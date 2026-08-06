# Demo script

1. Run `docker compose up --build` and open <http://localhost:3000>.
2. The **Code Atlas source** repository opens when its initial index is ready.
3. Follow `GET /api/repositories/{repositoryId}/search` from its controller into
   the repository and search stores.
4. Search for `IndexingService`. Confirm that methods and the source file appear
   in separate result groups.
5. Open `IndexingService.execute` to view its forward call graph, then trace its
   incoming references.
6. Open `IndexingService.java` to view the methods declared by the file.
7. Select a node to read relationship evidence, confidence, source, and Git data.
8. Make a harmless one-file Java edit and choose **Rescan repository**.
9. Review the processed, added, modified, and deleted file counts.

For a compact domain example, browse the Atlas Tasks endpoints under
`demo-app`, including issue assignment and comments. Its two notification
implementations provide a small polymorphic call site with more than one valid
target.
