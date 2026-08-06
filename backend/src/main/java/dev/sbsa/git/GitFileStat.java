package dev.sbsa.git;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GitFileStat(
        UUID sourceFileId,
        int totalCommits,
        int commitsLast90Days,
        Instant lastModifiedAt,
        String lastAuthorName,
        String lastCommitSha,
        int contributorCount,
        List<String> recentSubjects) {
}
