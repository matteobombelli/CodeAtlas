package dev.sbsa.git;

import java.time.Instant;
import java.util.List;

public record GitFileHistoryView(
        int totalCommits,
        int commitsLast90Days,
        Instant lastModifiedAt,
        String lastAuthorName,
        String lastCommitSha,
        int contributorCount,
        List<String> recentSubjects) {
}
