-- ADR-MONO-037 (TASK-BE-388): a UserProfile born from an IAM account.created
-- lifecycle event carries no raw email/name (the event is emailHash-only), and the
-- account.deleted(anonymized=true) reaction clears profile PII. Both require email and
-- name to be nullable. Additive, net-zero for existing rows (no value change).
ALTER TABLE user_profiles ALTER COLUMN email DROP NOT NULL;
ALTER TABLE user_profiles ALTER COLUMN name  DROP NOT NULL;
