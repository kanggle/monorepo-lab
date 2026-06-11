-- TASK-BE-353 (ADR-MONO-029 § D3) — RESOURCE_TAG access-condition pilot.
--
-- Add a minimal comma-separated `tags` column to admin_operators. The
-- RESOURCE_TAG condition's pilot (ADR-029 D3 = deny-if-present on the `protected`
-- tag) reads this column via a native projection query (no entity field added —
-- the aspect's ResourceTagResolver splits the string into a tag set).
--
-- NULL / empty (the default) = the operator carries no tags = un-gated (the
-- deny-if-present condition is satisfied for an untagged operator). Net-zero:
-- existing operators are all untagged until explicitly tagged, and the gate is
-- itself opt-in (no forbidden tag configured ⇒ no gate).
--
-- Tags are matched case-insensitively by the shared evaluator; storage keeps the
-- raw text. A short VARCHAR is sufficient for the pilot's single-label use.

ALTER TABLE admin_operators
    ADD COLUMN tags VARCHAR(512) NULL COMMENT 'ADR-029 RESOURCE_TAG: comma-separated resource tags (e.g. "protected"); NULL/empty = untagged';
