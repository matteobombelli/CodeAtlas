# Contributing

Open an issue before large changes so scope and graph semantics can be agreed.
Keep the backend a modular monolith and do not add distributed infrastructure
without an accepted architecture decision.

For parser or resolver changes:

- add a minimal Java fixture or focused source-string test;
- assert confidence and evidence, not only source/target identifiers;
- preserve ambiguous and unresolved outcomes;
- never execute an imported repository.

Before submitting:

```bash
./gradlew test
cd frontend
npm ci
npm run lint
npm test
npm run build
```

Use small commits that leave the repository in a working state. Update the
architecture and confidence documentation when changing core graph semantics.
