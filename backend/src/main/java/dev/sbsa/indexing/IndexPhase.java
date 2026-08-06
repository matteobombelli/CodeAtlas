package dev.sbsa.indexing;

public enum IndexPhase {
    QUEUED,
    DISCOVERING,
    HASHING,
    PARSING,
    COMPLETE,
    FAILED
}
