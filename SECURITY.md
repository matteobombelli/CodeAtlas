# Security policy

Spring Boot Static Analysis v0.1 is a local-first, single-user developer tool.
Do not expose it directly to an untrusted network.

Imported repositories must be mounted beneath
`SPRING_BOOT_STATIC_ANALYSIS_REPOSITORIES_ROOT`. The API accepts only relative
paths, canonicalizes them, rejects symlink escapes, and serves source only from
files in the active index. Imported builds and scripts are never executed, and
source content is not written to application logs.

Please report vulnerabilities privately through GitHub security advisories
rather than a public issue. Include reproduction steps, affected version, and
impact. No formal response SLA is offered for this portfolio release.
