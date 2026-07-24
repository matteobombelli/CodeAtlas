package dev.codeatlas.analysis;

public enum RelationshipKind {
    EXTENDS,
    IMPLEMENTS,
    INJECTS,
    CALLS,
    MANAGES_ENTITY,
    READS_ENTITY,
    WRITES_ENTITY,
    RETURNS_TYPE,
    ACCEPTS_TYPE,
    TESTS,
    REFERENCES
}
