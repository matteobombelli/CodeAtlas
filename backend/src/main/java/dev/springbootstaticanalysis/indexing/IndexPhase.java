package dev.springbootstaticanalysis.indexing;

public enum IndexPhase {
    QUEUED,
    DISCOVERING,
    HASHING,
    PARSING,
    COMPLETE,
    FAILED
}
