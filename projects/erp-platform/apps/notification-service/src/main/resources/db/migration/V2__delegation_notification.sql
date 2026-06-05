-- ---------------------------------------------------------------------------
-- V2 — TASK-ERP-BE-014: delegation-granted notification.
--
-- The `type` / `source_type` columns are already VARCHAR(32) STRING enums, so
-- adding NotificationType.DELEGATION_GRANTED + SourceRef.SourceType.DELEGATION
-- needs no column change. BUT the V1 CHECK constraints pin the allowed value
-- set, so they must be extended (the Docker-free `:check` slice does not exercise
-- the DB, so this was caught only by the Testcontainers IT — ERP-BE-014).
-- MySQL 8.0.16+ supports named CHECK constraints + DROP CHECK.
-- ---------------------------------------------------------------------------

ALTER TABLE notification DROP CHECK ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type CHECK (type IN (
    'APPROVAL_SUBMITTED','APPROVAL_APPROVED','APPROVAL_REJECTED','APPROVAL_WITHDRAWN',
    'DELEGATION_GRANTED'));

ALTER TABLE notification DROP CHECK ck_notification_source_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_source_type CHECK (source_type IN (
    'APPROVAL','DELEGATION'));
