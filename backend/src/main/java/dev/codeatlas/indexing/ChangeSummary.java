package dev.codeatlas.indexing;

import java.util.Set;

public record ChangeSummary(
        Set<String> added,
        Set<String> modified,
        Set<String> deleted,
        Set<String> affected) {

    public int changedCount() {
        return added.size() + modified.size() + deleted.size();
    }
}
