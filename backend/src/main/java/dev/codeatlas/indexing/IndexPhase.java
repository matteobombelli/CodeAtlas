package dev.codeatlas.indexing;

public enum IndexPhase {
    QUEUED,
    DISCOVERING,
    HASHING,
    PARSING,
    COMPLETE,
    FAILED
}
