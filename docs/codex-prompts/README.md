# Milestone prompts

These prompts are intentionally independent. Start a fresh Codex task for one
milestone only, after its predecessor is green. The repository currently
contains the completed v0.1 implementation; the prompts preserve the delivery
sequence for maintenance or reconstruction.

Standing constraints for every prompt:

- implement only the named milestone;
- keep the backend a modular monolith;
- do not add Kafka, Redis, Neo4j, Kubernetes, an LLM, or remote repository auth;
- never build or execute an imported repository;
- retain confidence and evidence for inferred relationships;
- retain ambiguity and unresolved diagnostics;
- enforce repository-root and graph-size boundaries;
- test relevant behavior and commit a cohesive green checkpoint.
