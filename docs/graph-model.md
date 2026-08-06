# Graph model

Spring Boot Static Analysis stores one normalized symbol table and a typed relationship table.
Language identity and framework meaning remain separate: a Java `CLASS` can have
the `SERVICE` role, while an HTTP endpoint is a distinct resource anchored to
its controller method.

## Nodes

Persisted symbol kinds include classes, interfaces, enums, records, functions,
methods, constructors, fields, and tests. Roles include controller, service, repository,
entity, component, configuration, and test. HTTP endpoints carry the combined
method/path plus their controller-method source location.

Stable symbol keys hash:

```text
relative path | symbol kind | qualified name | normalized signature
```

A signature change is deletion plus creation in v0.1; symbol lineage is not
inferred.

## Relationships

The stored model includes `CALLS`, `INJECTS`, `IMPLEMENTS`, `EXTENDS`,
`MANAGES_ENTITY`, `READS_ENTITY`, `WRITES_ENTITY`, and `TESTS`. Every resolved
relationship records confidence, resolution method, source file, line, column,
and evidence text.

Ambiguous and unresolved expressions are persisted separately with failure
reason and candidate count. External expressions are terminal references, not
fabricated project symbols.

## Projections

Endpoint and callable graphs use bounded forward breadth-first traversal. Blast
radius graphs walk callers, affected endpoints, and related tests. File graphs connect an indexed
source file to the methods and constructors declared in it; these `DECLARES`
edges are a projection and are not stored as execution relationships.

The frontend starts at depth 3. The API default is depth 4, with limits of 100
nodes and 250 edges. API depth is capped at 8. Truncation is part of the response
contract.

Search queries endpoints, named callables, and files independently. Callable
results include functions, methods, and constructors. Selecting one opens its
blast radius first; the same header switches to its forward dependency graph.
