-- ---------------------------------------------------------------------------
-- V3 — TASK-ERP-BE-016: delegation-revoked notification.
--
-- Adds NotificationType.DELEGATION_REVOKED. The `type` column is VARCHAR(32)
-- STRING enum (no column change), but the ck_notification_type CHECK constraint
-- pins the allowed value set, so it must be extended (the Docker-free `:check`
-- slice does not exercise the DB CHECK — caught only by the Testcontainers IT;
-- the ERP-BE-014 V2 lesson, applied up-front here). source_type DELEGATION is
-- already allowed by V2, so ck_notification_source_type is unchanged.
-- MySQL 8.0.16+ supports named CHECK constraints + DROP CHECK.
-- ---------------------------------------------------------------------------

ALTER TABLE notification DROP CHECK ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type CHECK (type IN (
    'APPROVAL_SUBMITTED','APPROVAL_APPROVED','APPROVAL_REJECTED','APPROVAL_WITHDRAWN',
    'DELEGATION_GRANTED','DELEGATION_REVOKED'));
