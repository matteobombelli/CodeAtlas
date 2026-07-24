# Milestone 2 prompt — Java symbols

Add JavaParser-based source extraction.

Persist source-located classes, interfaces, enums, records, methods,
constructors, fields, roles, stable symbol keys, and parse warnings. Use Java
language kinds separately from Spring semantic roles. Persist all symbols only
as one atomic active index.

Acceptance: focused fixture tests deterministically match expected symbol names,
signatures, roles, source ranges, and warnings.
