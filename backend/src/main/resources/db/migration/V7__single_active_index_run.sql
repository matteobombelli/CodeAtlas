-- At most one in-flight index run per repository.
--
-- IndexStore.create already counted active runs before inserting, but under READ
-- COMMITTED two concurrent transactions both see zero and both insert. Enforcing
-- it here makes the second insert fail regardless of interleaving; the store
-- translates that failure back into the same 409 the count check produced.
CREATE UNIQUE INDEX index_runs_single_active_idx
    ON index_runs (repository_id)
    WHERE status IN ('QUEUED', 'RUNNING');
