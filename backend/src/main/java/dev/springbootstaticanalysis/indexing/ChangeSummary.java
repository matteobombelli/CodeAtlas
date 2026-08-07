package dev.springbootstaticanalysis.indexing;

import java.util.Set;

/** Which files an incremental run must reanalyse, and why. */
public record ChangeSummary(
        Set<String> added,
        Set<String> modified,
        Set<String> deleted,
        Set<String> affected) {
}
