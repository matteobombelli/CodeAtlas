# Graph model

Code Atlas stores one normalized symbol table and a typed relationship table.
Language identity and framework meaning remain separate: a Java `CLASS` can have
the `SERVICE` role, while an HTTP endpoint is a distinct resource anchored to
its controller method.

## Nodes

Persisted symbol kinds include classes, interfaces, enums, records, methods,
constructors, fields, and tests. Roles include controller, service, repository,
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

Endpoint graphs use bounded breadth-first traversal. Potential blast radius uses
the reverse direction and stops at depth and size limits. Defaults are depth 4,
100 nodes, and 250 edges; API depth is capped at 8. Truncation is part of the
response contract.
