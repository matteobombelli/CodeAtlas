# Security policy

Spring Boot Static Analysis v0.1 is a local-first, single-user developer tool.
Do not expose the default Compose stack, backend port, or database directly to
an untrusted network.

The supported public configuration is `compose.prod.yaml` behind an HTTPS
reverse proxy. It binds only the frontend to loopback and enables backend
read-only mode, which rejects every mutating `/api/` request. Registering and
indexing projects is therefore a local-only activity: run the stack on your own
machine to do it. Keep the reverse proxy responsible for TLS, request limiting,
access logs, and host-level access controls.

Imported repositories must be mounted beneath
`SPRING_BOOT_STATIC_ANALYSIS_REPOSITORIES_ROOT`. The API accepts only relative
paths, canonicalizes them, rejects symlink escapes, and serves source only from
files in the active index. Imported builds and scripts are never executed, and
source file bodies are never deliberately written to application logs. Parser
exceptions may still identify the affected file and token in private backend
logs; public API errors contain only an index-run reference.

Use a unique production database password. The database holds only derived
analysis output, so a lost volume costs an index run rather than data.
Dependency updates and the production Compose smoke test run through GitHub
configuration in this repository.

Please report vulnerabilities privately through GitHub security advisories
rather than a public issue. Include reproduction steps, affected version, and
impact. No formal response SLA is offered for this portfolio release.
