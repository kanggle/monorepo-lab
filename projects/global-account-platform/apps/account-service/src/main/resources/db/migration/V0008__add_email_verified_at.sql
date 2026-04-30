-- TASK-BE-114: email verification flow (non-blocking)
-- Adds nullable email_verified_at to accounts. Existing rows remain NULL,
-- which represents "email not yet verified" — accounts stay ACTIVE regardless
-- (specs/features/signup.md: non-blocking design).
-- No index added: this column is read on a single-row update path keyed by id,
-- not in any WHERE filter that would benefit from an index.
ALTER TABLE accounts ADD COLUMN email_verified_at DATETIME(6) NULL;
