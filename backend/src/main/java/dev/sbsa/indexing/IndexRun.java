package dev.sbsa.indexing;

import java.time.Instant;
import java.util.UUID;

public record IndexRun(
        UUID id,
        UUID repositoryId,
        IndexMode mode,
        IndexStatus status,
        IndexPhase phase,
        int filesDiscovered,
        int filesProcessed,
        int warningsCount,
        int symbolsCreated,
        int endpointsCreated,
        int edgesCreated,
        int filesAdded,
        int filesModified,
        int filesDeleted,
        Instant startedAt,
        Instant completedAt,
        String errorCode,
        String errorSummary) {
}
