-- TASK-BE-050: align admin_operator_refresh_tokens.jti with JPA entity.
--
-- V0015 originally declared jti as CHAR(36) to reflect the fixed-width UUID
-- string semantics. The corresponding JPA entity, however, declares the
-- column with `@Column(length = 36)` which Hibernate maps to VARCHAR(36).
-- Under `spring.jpa.hibernate.ddl-auto=validate` this mismatch causes
-- admin-service to fail startup with:
--
--   Schema-validation: wrong column type encountered in column [jti] in
--   table [admin_operator_refresh_tokens]; found [char (Types#CHAR)], but
--   expecting [varchar(36) (Types#VARCHAR)]
--
-- Source of truth: the JPA entity (VARCHAR is the idiomatic Hibernate
-- default). Rather than editing V0015 (which would break Flyway checksums
-- on environments that have already applied it), we add a forward ALTER
-- migration that converts the column type to VARCHAR(36) while preserving
-- existing rows. This mirrors the approach used in V0017 for
-- admin_bulk_lock_idempotency.request_hash (TASK-BE-049).
--
-- No data loss is possible: MySQL CHAR(36) → VARCHAR(36) is a widening-safe
-- conversion. UUID strings written by the application are exactly 36
-- characters with no trailing whitespace.

ALTER TABLE admin_operator_refresh_tokens
    MODIFY COLUMN jti VARCHAR(36) NOT NULL;
