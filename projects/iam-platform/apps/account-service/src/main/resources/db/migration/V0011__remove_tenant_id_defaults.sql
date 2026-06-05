-- TASK-BE-228: Remove DEFAULT 'fan-platform' from domain table tenant_id columns.
-- All existing rows have been backfilled by V0010's DEFAULT clause.
-- NOT NULL constraint is preserved — new inserts must supply tenant_id explicitly.
--
-- NOTE: outbox.tenant_id DEFAULT is intentionally kept here because the shared
-- OutboxJpaEntity (libs/java-messaging) is used across multiple services and does
-- not yet carry a tenantId field. The DEFAULT acts as a safe placeholder until a
-- dedicated task adds tenant_id to the outbox entity and all dependent services.

ALTER TABLE accounts
    ALTER COLUMN tenant_id DROP DEFAULT;

ALTER TABLE profiles
    ALTER COLUMN tenant_id DROP DEFAULT;

ALTER TABLE account_status_history
    ALTER COLUMN tenant_id DROP DEFAULT;
