# Resolution confidence

Confidence describes the evidence for one relationship. It is not a probability
that the whole execution path runs in production.

| Evidence | Confidence | Presentation |
| --- | ---: | --- |
| Exact method on a known project type | 1.00 | Exact |
| Constructor-injected project type | 0.98 | Exact |
| Declared extends/implements relationship | 0.95 | Exact |
| Spring Data repository method | 0.90 | Inferred |
| Spring Data entity read/write prefix | 0.80 | Inferred |
| Unique project method by name and arity | 0.70 | Inferred |
| Test class naming convention | 0.60 | Inferred |

When multiple declarations remain possible, Code Atlas stores the expression,
candidate count, and `MULTIPLE_CANDIDATES` reason. It does not choose the first
candidate. Calls whose implementation lies outside indexed project source are
stored as external terminal references. Unsupported or unscoped expressions
remain unresolved.

The frontend expresses evidence both visually and as text. Solid, dashed, and
dotted edges are never the only indication of confidence.
