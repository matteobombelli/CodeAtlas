# v0.1 limitations

Spring Boot Static Analysis is an inspectable approximation for ordinary Spring Boot projects,
not a Java compiler or runtime tracer.

Supported well:

- local Git working trees under approved roots;
- conventional Maven and Gradle Java source sets;
- Spring controllers and combined mapping paths;
- constructor injection and project-local direct calls;
- common Spring Data repositories and JPA entities;
- JUnit annotations, direct test calls, and test naming conventions;
- file-level Git history;
- bounded endpoint and reverse impact graphs.

Explicitly out of scope:

- Kotlin and other source languages;
- executing imported builds or downloading their dependency graph;
- bytecode-only implementations and external library internals;
- complete lambda, method-reference, reflection, proxy, and runtime-polymorphism
  resolution;
- raw SQL table inference, Kafka/message flows, and runtime traces;
- symbol-level Git lineage or blame;
- authentication, remote repository hosting, multi-user collaboration, or cloud
  isolation;
- a full IDE, generic repository chat, or code-quality score.

Spring annotations can be recognized by controlled names when third-party types
are unavailable. That is useful but less strong evidence than fully resolved
project declarations. Working-tree indexing is intentional; a dirty flag and
snapshot re-check make that state visible.
