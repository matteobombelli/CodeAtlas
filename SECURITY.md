# Security policy

Spring Boot Static Analysis v0.1 is a local-first, single-user developer tool.
Do not expose the default Compose stack, backend port, or database directly to
an untrusted network.

The supported public configuration is `compose.prod.yaml` behind an HTTPS
reverse proxy. It binds the frontend to loopback and publishes two entrances.
The public one enables backend read-only mode, which rejects every mutating
`/api/` request. The second entrance is reachable only from the host, and its
Nginx server block is the only one that sets `X-Local-Entrance`, which the
backend accepts as permission to mutate. The public server block always clears
that header, so it cannot be supplied by a visitor. Forward only the public
port from the reverse proxy, and keep the proxy responsible for TLS, request
limiting, access logs, and host-level access controls.

Anyone who can reach the local entrance can register any project beneath the
repositories root and read its source through the API. Treat host access as
equivalent to full access.

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
