-- TASK-BE-050-fix-rotated-from-varchar: align admin_operator_refresh_tokens.rotated_from with JPA entity.
--
-- V0015 originally declared rotated_from as CHAR(36) NULL to mirror the
-- fixed-width UUID string semantics of jti. The corresponding JPA entity,
-- `AdminOperatorRefreshTokenJpaEntity`, declares the column with
-- `@Column(name = "rotated_from", length = 36)` which Hibernate maps to
-- VARCHAR(36). Under `spring.jpa.hibernate.ddl-auto=validate` this mismatch
-- causes admin-service to fail startup with:
--
--   Schema-validation: wrong column type encountered in column [rotated_from]
--   in table [admin_operator_refresh_tokens]; found [char (Types#CHAR)], but
--   expecting [varchar(36) (Types#VARCHAR)]
--
-- TASK-BE-050 (V0018) already corrected `jti`, but the review that followed
-- identified `rotated_from` as a second, independent instance of the same
-- CHAR/VARCHAR drift in the same table. We therefore add a second forward
-- ALTER migration rather than editing V0015 or V0018 (both of which would
-- break Flyway checksums on environments that have already applied them).
-- This mirrors the approach used in V0017 (request_hash) and V0018 (jti).
--
-- Nullability is preserved: rotated_from remains NULL-able because the very
-- first refresh token in a rotation chain has no predecessor.
--
-- No data loss is possible: MySQL CHAR(36) → VARCHAR(36) is a widening-safe
-- conversion. UUID strings written by the application are exactly 36
-- characters with no trailing whitespace.

ALTER TABLE admin_operator_refresh_tokens
    MODIFY COLUMN rotated_from VARCHAR(36) NULL;
