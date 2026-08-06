package dev.springbootstaticanalysis.analysis;

public enum ResolutionMethod {
    AST_DECLARATION,
    EXACT_PROJECT_TYPE,
    UNIQUE_NAME_AND_ARITY,
    CONSTRUCTOR_INJECTION,
    SPRING_DATA_GENERIC,
    SPRING_DATA_METHOD,
    TEST_DIRECT_CALL,
    TEST_NAMING_CONVENTION
}
