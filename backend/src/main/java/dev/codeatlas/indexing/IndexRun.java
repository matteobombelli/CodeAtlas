package dev.codeatlas.indexing;

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
        Instant startedAt,
        Instant completedAt,
        String errorCode,
        String errorSummary) {
}
