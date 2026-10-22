-- TASK-SCM-BE-062 — a reorder suggestion records the scm reorder point that triggered it.
--
-- reorder_point_at_trigger is a SNAPSHOT of reorder_policy.reorder_point taken by the raise
-- factory (ALERT and BATCH paths) in the same transaction that reads the policy. It is
-- written once and never updated: a later policy PUT, approve, dismiss or materialize does
-- not change it.
--
-- Additive + nullable, and deliberately NOT back-filled:
--   * a suggestion raised without a policy row (degraded fallback — wms alert threshold on
--     ALERT, the candidate's own availableQty on BATCH) has no scm reorder point, so it
--     stays NULL;
--   * rows raised before this migration stay NULL. Filling them from the CURRENT policy
--     would record a value that did not trigger them.
-- The REST response omits the key when the value is NULL (@JsonInclude(NON_NULL)).

ALTER TABLE reorder_suggestion
    ADD COLUMN reorder_point_at_trigger INT;
