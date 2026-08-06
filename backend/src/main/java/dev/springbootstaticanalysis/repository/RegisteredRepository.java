package dev.springbootstaticanalysis.repository;

import java.time.Instant;
import java.util.UUID;

public record RegisteredRepository(
        UUID id,
        String displayName,
        String relativePath,
        BuildSystem buildSystem,
        RepositoryStatus status,
        UUID activeIndexRunId,
        Instant createdAt,
        Instant lastIndexedAt,
        int sourceFileCount) {
}
