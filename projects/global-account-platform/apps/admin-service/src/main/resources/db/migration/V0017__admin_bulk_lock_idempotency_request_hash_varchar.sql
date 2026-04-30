-- TASK-BE-049: align admin_bulk_lock_idempotency.request_hash with JPA entity.
--
-- V0012 originally declared request_hash as CHAR(64) to reflect the fixed-width
-- SHA-256 hex digest semantics. The BulkLockIdempotencyJpaEntity, however,
-- declares the column with `@Column(length = 64)` which Hibernate maps to
-- VARCHAR(64). Under `spring.jpa.hibernate.ddl-auto=validate` this mismatch
-- causes the admin-service to fail startup schema validation.
--
-- Source of truth: the JPA entity (VARCHAR is the idiomatic Hibernate default
-- and matches the sibling `idempotency_key` column which is already VARCHAR(64)
-- in V0012). Rather than editing V0012 (which would break Flyway checksums on
-- environments that have already applied it), we add a forward ALTER migration
-- that converts the column type to VARCHAR(64) while preserving existing rows.
--
-- No data loss is possible: MySQL CHAR(64) → VARCHAR(64) is a widening-safe
-- conversion (trailing spaces are not padded on VARCHAR storage, but the hex
-- digests written by the application are already exactly 64 characters with no
-- trailing whitespace).

ALTER TABLE admin_bulk_lock_idempotency
    MODIFY COLUMN request_hash VARCHAR(64) NOT NULL;
